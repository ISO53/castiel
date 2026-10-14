package io.github.iso53.castiel.update;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.iso53.castiel.util.AppPaths;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Plain update core shared by the CLI flow (which runs before Spring starts)
 * and the web service. Owns everything that must not depend on Spring beans:
 * version and install detection, the GitHub release check, the asset download,
 * and the native sideloader process that swaps the bundle after this app exits.
 *
 * <p>The sideloader is a PowerShell or POSIX sh script shipped as a classpath
 * resource. It cannot be a Java process: it would run on the very runtime the
 * swap replaces. All feedback between the script and the app goes through tiny
 * marker files in {@link #WORK_DIR}.</p>
 */
public final class Updater {

	private static final Logger log = LoggerFactory.getLogger(Updater.class);

	/** GitHub endpoint that answers with the latest published release. */
	private static final String LATEST_RELEASE_URL = "https://api.github.com/repos/ISO53/castiel/releases/latest";

	/** Directory under the OS config dir that holds the markers, logs and downloads. */
	public static final Path WORK_DIR = AppPaths.configDir().resolve("update");

	/** Appended lifecycle log: one line per check, download, spawn and marker consumption. */
	public static final Path UPDATE_LOG = WORK_DIR.resolve("update.log");

	/** The last failure only, overwritten per failure; rotated after the next startup reports it. */
	public static final Path FAILURE_LOG = WORK_DIR.resolve("update-failure.log");

	/** Marker written by the script after a successful swap; consumed once on next launch. */
	public static final Path OK_MARKER = WORK_DIR.resolve("update-ok");

	/** Marker written by the script when the swap failed; consumed once on next launch. */
	public static final Path ERROR_MARKER = WORK_DIR.resolve("update-error");

	/** Sideloader pid of the last spawn, so overlapping scripts can be killed. */
	private static final Path PID_FILE = WORK_DIR.resolve("sideload.pid");

	/** The sideloader script, copied here next to the markers before every spawn. */
	private static final Path SCRIPT = WORK_DIR.resolve(
		System.getProperty("os.name", "").toLowerCase().contains("win") ? "sideload.ps1" : "sideload.sh"
	);

	private static final ObjectMapper JSON = new ObjectMapper();

	private static final HttpClient HTTP = HttpClient.newBuilder()
		.followRedirects(HttpClient.Redirect.NORMAL)
		.connectTimeout(Duration.ofSeconds(20))
		.build();

	// Detected once: null means this is not a packaged build.
	private static volatile Path installDir;

	private Updater() {}

	/** One published release as the updater needs it. */
	public record Release(String tag, String version, String assetName, String downloadUrl, String releaseUrl) {}

	/** Download progress callback; receives whole percent values only. */
	@FunctionalInterface
	public interface Progress {
		void accept(int percent);
	}

	/** Installed version from the jar manifest, or {@code dev} when run from source. */
	public static String installedVersion() {
		Package pkg = Updater.class.getPackage();
		String version = pkg != null ? pkg.getImplementationVersion() : null;
		return version != null ? version : "dev";
	}

	/** Root of the jpackage app image this build runs from, or null when not packaged. */
	public static Path installDir() {
		Path cached = installDir;
		if (cached == null) {
			cached = detectInstallDir();
			installDir = cached;
		}
		return cached;
	}

	/**
	 * Walks up from the running jar to the app image root. The root holds the
	 * launcher and the {@code runtime} dir (Windows, Linux) or the nested
	 * {@code castiel.app} bundle (macOS). Anything else counts as a dev run.
	 */
	private static Path detectInstallDir() {
		Path jar = codeSourceJar();
		if (log.isDebugEnabled()) {
			log.debug(
				"Install detection: jar={}, classPath={}, commandLine={}",
				jar,
				System.getProperty("java.class.path"),
				ProcessHandle.current().info().commandLine().orElse("(none)")
			);
		}
		if (jar == null || !Files.isRegularFile(jar) || !jar.getFileName().toString().endsWith(".jar")) {
			return null;
		}
		Path dir = jar.getParent();
		for (int level = 0; level < 5 && dir != null; level++, dir = dir.getParent()) {
			if (Files.isDirectory(dir.resolve("castiel.app"))) {
				return dir;
			}
			// Contents/app is inside the macOS bundle; never swap that deep.
			if (
				!"Contents".equals(dir.getFileName().toString()) &&
				Files.isDirectory(dir.resolve("app")) &&
				Files.isDirectory(dir.resolve("runtime"))
			) {
				return dir;
			}
		}
		return null;
	}

	/**
	 * The running jar, or null when none can be found. The nested fat jar's
	 * code-source URL is not a plain file path, so the class path and the
	 * process command line are tried too.
	 */
	private static Path codeSourceJar() {
		// `java -jar app.jar` puts the jar on the class path.
		for (String entry : System.getProperty("java.class.path", "").split(File.pathSeparator)) {
			if (entry.endsWith(".jar") && Files.isRegularFile(Path.of(entry))) {
				return Path.of(entry);
			}
		}
		// The launcher's command line names the jar, e.g. `java -jar path/to/castiel.jar`.
		Optional<String> command = ProcessHandle.current().info().commandLine();
		if (command.isPresent()) {
			for (String part : command.get().split(" ")) {
				if (part.endsWith(".jar") && Files.isRegularFile(Path.of(part))) {
					return Path.of(part);
				}
			}
		}
		// Plain (exploded) class path or a non-nested jar URL.
		try {
			Path codeSource = Path.of(Updater.class.getProtectionDomain().getCodeSource().getLocation().toURI());
			if (Files.isRegularFile(codeSource) && codeSource.getFileName().toString().endsWith(".jar")) {
				return codeSource;
			}
		} catch (Exception ex) {
			log.debug("Code-source jar detection failed", ex);
		}
		return null;
	}

	/** Asset name published for the running platform, or null when none is published. */
	public static String platformAssetName() {
		String os = System.getProperty("os.name", "").toLowerCase();
		String arch = System.getProperty("os.arch", "").toLowerCase();
		boolean arm64 = arch.contains("aarch64") || arch.contains("arm64");
		boolean x64 = arch.contains("64") && !arm64;
		if (os.contains("win") && x64) {
			return "castiel-windows-x64.zip";
		}
		if (os.contains("mac") && arm64) {
			return "castiel-macos-arm64.zip";
		}
		if (os.contains("linux") && x64) {
			return "castiel-linux-x64.tar.gz";
		}
		return null;
	}

	/** Fetches the latest GitHub release, or the mocked one set by the test property. */
	public static Release fetchLatestRelease() throws IOException, InterruptedException {
		String mock = System.getProperty("castiel.update.mock-latest");
		if (mock != null && !mock.isBlank()) {
			String version = mock.strip();
			logLifecycle("using mocked latest release " + version);
			return new Release("v" + version, version, platformAssetName(), null, LATEST_RELEASE_URL);
		}
		HttpRequest request = HttpRequest.newBuilder(URI.create(LATEST_RELEASE_URL))
			.header("Accept", "application/vnd.github+json")
			.header("User-Agent", "castiel/" + installedVersion())
			.timeout(Duration.ofSeconds(20))
			.GET()
			.build();
		HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
		if (response.statusCode() != 200) {
			throw new IOException("GitHub answered " + response.statusCode() + " for the latest release");
		}
		JsonNode root = JSON.readTree(response.body());
		String tag = root.path("tag_name").asText("");
		String assetName = platformAssetName();
		String downloadUrl = null;
		for (JsonNode asset : root.path("assets")) {
			if (assetName != null && assetName.equals(asset.path("name").asText())) {
				downloadUrl = asset.path("browser_download_url").asText(null);
				break;
			}
		}
		return new Release(tag, versionOf(tag), assetName, downloadUrl, root.path("html_url").asText(null));
	}

	// Strips the leading v from a release tag.
	private static String versionOf(String tag) {
		return tag != null && tag.toLowerCase().startsWith("v") ? tag.substring(1) : tag;
	}

	/**
	 * Whether {@code latest} is newer than {@code installed}. Numeric segment
	 * comparison; a non-numeric version (dev) is never newer than anything.
	 */
	public static boolean isNewer(String installed, String latest) {
		int[] current = segments(installed);
		int[] next = segments(latest);
		if (current == null || next == null) {
			return false;
		}
		for (int i = 0; i < Math.max(current.length, next.length); i++) {
			int a = i < current.length ? current[i] : 0;
			int b = i < next.length ? next[i] : 0;
			if (a != b) {
				return b > a;
			}
		}
		return false;
	}

	// Version split into integer segments; null when any segment is not numeric.
	private static int[] segments(String version) {
		if (version == null || version.isBlank() || version.equals("dev")) {
			return null;
		}
		try {
			String[] parts = version.strip().toLowerCase().split("[.\\-+_]");
			int[] out = new int[parts.length];
			for (int i = 0; i < parts.length; i++) {
				out[i] = Integer.parseInt(parts[i]);
			}
			return out;
		} catch (NumberFormatException ex) {
			return null;
		}
	}

	/**
	 * Downloads the release asset into the work dir and reports whole percents.
	 * The archive stays there until the script deletes it after a successful swap.
	 */
	public static Path download(Release release, Progress progress) throws IOException, InterruptedException {
		if (release.downloadUrl() == null) {
			throw new IOException("This release has no bundle for this platform.");
		}
		Files.createDirectories(WORK_DIR);
		Path target = WORK_DIR.resolve(release.assetName());
		HttpRequest request = HttpRequest.newBuilder(URI.create(release.downloadUrl()))
			.header("User-Agent", "castiel/" + installedVersion())
			.GET()
			.build();
		HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
		if (response.statusCode() != 200) {
			response.body().close();
			throw new IOException("The download answered " + response.statusCode());
		}
		long total = response.headers().firstValueAsLong("Content-Length").orElse(0);
		logLifecycle("downloading " + release.assetName() + " (" + total + " bytes)");
		try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(target)) {
			byte[] buffer = new byte[64 * 1024];
			long done = 0;
			int lastPercent = -1;
			for (int read = in.read(buffer); read != -1; read = in.read(buffer)) {
				out.write(buffer, 0, read);
				done += read;
				if (total > 0) {
					int percent = (int) ((done * 100) / total);
					if (percent != lastPercent) {
						lastPercent = percent;
						progress.accept(percent);
					}
				}
			}
		}
		logLifecycle("downloaded to " + target);
		return target;
	}

	/**
	 * Copies the sideloader script into the work dir and spawns it. Session mode
	 * stays attached to the caller's terminal (CLI); detached mode survives the
	 * app window closing. The script extracts the archive, waits for this
	 * process to exit, then swaps the bundle.
	 */
	public static void spawnSideloader(Path archive, boolean session, String newVersion) throws IOException {
		Files.createDirectories(WORK_DIR);
		killPreviousSideloaders();
		// A stale failure report from an earlier attempt must not lie on the next boot.
		Files.deleteIfExists(FAILURE_LOG);
		copyScript();
		Path dir = installDir();
		String mode = session ? "session" : "detached";
		List<String> command = new ArrayList<>();
		if (SCRIPT.getFileName().toString().endsWith(".ps1")) {
			command.addAll(
				List.of(
					"powershell.exe",
					"-NoProfile",
					"-ExecutionPolicy",
					"Bypass",
					"-File",
					SCRIPT.toString(), //
					"-AppPid",
					Long.toString(ProcessHandle.current().pid()), //
					"-InstallDir",
					dir.toString(), //
					"-Archive",
					archive.toString(), //
					"-WorkDir",
					WORK_DIR.toString(), //
					"-Mode",
					mode, //
					"-Version",
					newVersion
				)
			);
		} else {
			command.addAll(
				List.of(
					"sh",
					SCRIPT.toString(), //
					Long.toString(ProcessHandle.current().pid()), //
					dir.toString(), //
					archive.toString(), //
					WORK_DIR.toString(), //
					mode, //
					newVersion
				)
			);
		}
		ProcessBuilder builder = new ProcessBuilder(command);
		// The script must never run with the install dir as its working directory;
		// Windows refuses to rename a directory that is any process's CWD.
		builder.directory(WORK_DIR.toFile());
		if (session) {
			builder.inheritIO();
		} else {
			Path spawnLog = WORK_DIR.resolve("sideload-spawn.log");
			builder.redirectOutput(spawnLog.toFile());
			builder.redirectError(spawnLog.toFile());
		}
		Process process = builder.start();
		Files.writeString(PID_FILE, Long.toString(process.pid()));
		logLifecycle("spawned sideloader pid " + process.pid() + ", mode " + mode + ", install dir " + dir);
	}

	// Unpacks the classpath script next to the markers so the running jar is never a dependency.
	private static void copyScript() throws IOException {
		boolean windows = SCRIPT.getFileName().toString().endsWith(".ps1");
		String resource = "/update/sideload-" + (windows ? "windows.ps1" : "posix.sh");
		try (InputStream in = Updater.class.getResourceAsStream(resource)) {
			if (in == null) {
				throw new IOException("The sideloader script is missing from the jar: " + resource);
			}
			Files.copy(in, SCRIPT, StandardCopyOption.REPLACE_EXISTING);
		}
		if (!windows) {
			// The copy drops the mode bits; the script must run without a shell preflight.
			SCRIPT.toFile().setExecutable(true, false);
		}
	}

	/**
	 * Kills sideloaders left over by earlier sessions. Matching by command line
	 * does not work (Windows often reports none), so the last spawn's pid is
	 * tracked in a file; the start-time guard stops a recycled, innocent pid
	 * from being killed.
	 */
	private static void killPreviousSideloaders() {
		if (!Files.isRegularFile(PID_FILE)) {
			return;
		}
		try {
			Instant recorded = Files.getLastModifiedTime(PID_FILE).toInstant();
			for (String line : Files.readAllLines(PID_FILE)) {
				long pid = Long.parseLong(line.trim());
				Optional<ProcessHandle> handle = ProcessHandle.of(pid);
				if (handle.isEmpty()) {
					continue;
				}
				Optional<Instant> started = handle.get().info().startInstant();
				if (started.isPresent() && started.get().isBefore(recorded) && handle.get().destroy()) {
					logLifecycle("killed leftover sideloader pid " + pid);
				}
			}
		} catch (Exception ex) {
			logLifecycle("could not kill leftover sideloaders: " + ex);
		}
	}

	/** Appends one lifecycle line to update.log. */
	public static void logLifecycle(String line) {
		try {
			Files.createDirectories(WORK_DIR);
			Files.writeString(
				UPDATE_LOG,
				Instant.now() + " " + line + System.lineSeparator(), //
				StandardCharsets.UTF_8,
				StandardOpenOption.CREATE,
				StandardOpenOption.APPEND
			);
		} catch (IOException ex) {
			log.debug("Could not write the update log", ex);
		}
	}

	/** Writes the full failure detail to update-failure.log; reported once on next launch. */
	public static void writeFailure(String step, Throwable failure) {
		StringWriter trace = new StringWriter();
		failure.printStackTrace(new PrintWriter(trace));
		try {
			Files.createDirectories(WORK_DIR);
			Files.writeString(FAILURE_LOG, "step: " + step + System.lineSeparator() + trace, StandardCharsets.UTF_8);
		} catch (IOException ex) {
			log.error("Could not write the update failure log", ex);
		}
		log.error("App update failed at {}: {}", step, failure.toString(), failure);
	}
}
