package io.github.iso53.castiel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.iso53.castiel.update.Updater;
import jakarta.annotation.PostConstruct;
import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/**
 * The update feature's web-facing state machine: one {@code phase} string
 * drives everything the UI shows. Checks are cached for ten minutes; installs
 * download the bundle with live progress and hand the swap to the detached
 * sideloader. Startup consumes the script's marker files exactly once.
 */
@Service
public class UpdateService {

	private static final Logger log = LoggerFactory.getLogger(UpdateService.class);

	/** How long a GitHub check stays valid; the UI button and the CLI always force a fresh one. */
	private static final Duration CHECK_TTL = Duration.ofMinutes(10);

	// Lifecycle phases as the UI reads them.
	private static final String IDLE = "idle";
	private static final String DOWNLOADING = "downloading";
	private static final String STAGING = "staging";
	private static final String STAGED = "staged";
	private static final String ERROR = "error";

	private final UserSettingsService settingsService;

	// WebFlux exposes no shared ObjectMapper bean; this service needs one tiny use.
	private final ObjectMapper json = new ObjectMapper();

	// Fan-out of status events for the UI's SSE feed.
	private final Sinks.Many<String> feed = Sinks.many().replay().latest();

	// Backend-owned state; the UI never keeps a second copy of it.
	private volatile String phase = IDLE;
	private volatile Integer percent;
	private volatile String message;
	private volatile String latestVersion;
	private volatile boolean updateAvailable;
	private volatile boolean installable;
	private volatile String reason;
	private volatile String releaseUrl;
	private volatile String lastResultOutcome;
	private volatile String lastResultMessage;
	private volatile Updater.Release release;
	private volatile Instant checkedAt;

	public UpdateService(UserSettingsService settingsService) {
		this.settingsService = settingsService;
	}

	/**
	 * Full update state for the UI: versions, availability, phase, progress and
	 * the once-per-launch startup result.
	 */
	public record UpdateStatus(
		String installedVersion,
		String latestVersion,
		boolean updateAvailable,
		boolean installable,
		String reason,
		String phase,
		Integer percent,
		String message,
		String releaseUrl,
		boolean checkOnStartup,
		String lastResultOutcome,
		String lastResultMessage
	) {}

	/** Consumes the sideloader's marker files exactly once, before the UI can ask. */
	@PostConstruct
	void consumeStartupMarkers() {
		Updater.logLifecycle("startup: reading update markers");
		try {
			if (Files.isRegularFile(Updater.OK_MARKER)) {
				String version = Files.readString(Updater.OK_MARKER).trim();
				lastResultOutcome = "updated";
				lastResultMessage = "castiel was updated to " + version + ".";
				Files.deleteIfExists(Updater.OK_MARKER);
				Updater.logLifecycle("consumed update-ok marker: " + version);
			}
			if (Files.isRegularFile(Updater.ERROR_MARKER)) {
				String report = Files.isRegularFile(Updater.FAILURE_LOG)
					? Files.readString(Updater.FAILURE_LOG)
					: "no failure report was written";
				log.error("Previous app update failed, here is the stack trace:\n" + report);
				// Rotate, not delete: the report stays inspectable after it was reported once.
				Files.move(
					Updater.FAILURE_LOG,
					Updater.WORK_DIR.resolve("update-failure.prev.log"), //
					StandardCopyOption.REPLACE_EXISTING
				);
				lastResultOutcome = "failed";
				lastResultMessage = "Update failed. See log file";
				Files.deleteIfExists(Updater.ERROR_MARKER);
				Updater.logLifecycle("consumed update-error marker and rotated the failure report");
			}
		} catch (IOException ex) {
			log.warn("Could not read the update markers", ex);
		}
	}

	/**
	 * The current status. A staged sideloader reports a script failure through
	 * the error marker; it is peeked at here without being consumed, so a user
	 * who never closed the app still sees the failure in the settings section.
	 */
	public UpdateStatus status() {
		if (STAGED.equals(phase) && Files.isRegularFile(Updater.ERROR_MARKER)) {
			phase = ERROR;
			message = "The update could not be prepared. See log file";
			Updater.logLifecycle("detected the sideloader error marker while staged");
		}
		return new UpdateStatus(
			Updater.installedVersion(),
			latestVersion,
			updateAvailable,
			installable,
			reason,
			phase,
			percent,
			message,
			releaseUrl,
			settingsService.get().checkForUpdatesOnStartup(),
			lastResultOutcome,
			lastResultMessage
		);
	}

	/**
	 * Compares this build with the latest GitHub release, unless a fresh check
	 * is cached. A cached result is re-evaluated, never re-fetched.
	 */
	public synchronized UpdateStatus check(boolean force) throws IOException, InterruptedException {
		if (!force && release != null && checkedAt != null && checkedAt.plus(CHECK_TTL).isAfter(Instant.now())) {
			return status();
		}
		Updater.Release fetched = Updater.fetchLatestRelease();
		release = fetched;
		checkedAt = Instant.now();
		refreshAvailability();
		Updater.logLifecycle(
			"checked: installed " +
				Updater.installedVersion() +
				", latest " +
				fetched.version() +
				", update available " +
				updateAvailable
		);
		emit();
		return status();
	}

	// Recomputes availability and the not-installable reason from the cached release.
	private void refreshAvailability() {
		latestVersion = release.version();
		releaseUrl = release.releaseUrl();
		updateAvailable = false;
		installable = false;
		reason = null;
		if (Updater.installDir() == null) {
			reason = "Updates are only available in packaged installs.";
		} else if (release.assetName() == null) {
			reason = "No castiel build is published for this platform.";
		} else if (release.downloadUrl() == null) {
			reason = "This release has no bundle for your platform.";
		} else {
			installable = true;
			updateAvailable = Updater.isNewer(Updater.installedVersion(), release.version());
		}
	}

	/** Starts the install: download with progress, then hand the swap to the detached sideloader. */
	public synchronized void install() {
		if (DOWNLOADING.equals(phase) || STAGING.equals(phase)) {
			throw new IllegalStateException("An update is already in progress.");
		}
		if (STAGED.equals(phase)) {
			throw new IllegalStateException("The update is already prepared; it applies on next launch.");
		}
		if (release == null || !updateAvailable || !installable) {
			throw new IllegalStateException("No update to install. Check for updates first.");
		}
		Updater.Release target = release;
		Thread.ofPlatform()
			.name("castiel-update")
			.start(() -> downloadAndStage(target));
	}

	// Download → stage → hand over; every phase change is pushed to the SSE feed.
	private void downloadAndStage(Updater.Release target) {
		phase = DOWNLOADING;
		percent = 0;
		message = "Downloading castiel " + target.version() + "...";
		emit();
		try {
			Path archive = Updater.download(target, this::onDownloadProgress);
			phase = STAGING;
			percent = null;
			message = "Preparing the update...";
			emit();
			Updater.spawnSideloader(archive, false, target.version());
			phase = STAGED;
			message = null;
			emit();
			Updater.logLifecycle("update staged; it applies on next launch");
		} catch (Exception ex) {
			Updater.writeFailure("installing the update", ex);
			phase = ERROR;
			message = ex.getMessage() != null ? ex.getMessage() : ex.toString();
			emit();
		}
	}

	// Whole-percent updates during the download; the feed pushes them to the UI.
	private void onDownloadProgress(int value) {
		percent = value;
		emit();
	}

	/** Opens the log directory in the system file manager; headless builds get the path back. */
	public String openLogs() throws IOException {
		Files.createDirectories(Updater.WORK_DIR);
		if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
			Desktop.getDesktop().open(Updater.WORK_DIR.toFile());
			Updater.logLifecycle("opened the update log directory in the file manager");
			return null;
		}
		Updater.logLifecycle("no file manager available; reported the log directory path");
		return Updater.WORK_DIR.toString();
	}

	/** Live status feed; one event per phase or progress change. */
	public Flux<ServerSentEvent<String>> events() {
		return feed.asFlux().map(tick -> ServerSentEvent.builder(tick).build());
	}

	// Serializes the status and pings every connected UI.
	private void emit() {
		try {
			feed.tryEmitNext(json.writeValueAsString(status()));
		} catch (Exception ex) {
			log.debug("Could not emit the update status", ex);
		}
	}
}
