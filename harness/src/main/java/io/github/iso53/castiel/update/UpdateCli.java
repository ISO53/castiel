package io.github.iso53.castiel.update;

import java.io.Console;
import java.nio.file.Path;

/**
 * The {@code --check-update} command line flow. Runs before Spring starts, so
 * it depends on nothing but {@link Updater}. On agreement it downloads the new
 * bundle and spawns the sideloader in session mode: the script stays attached
 * to the user's terminal, swaps the bundle once this process exits, and starts
 * the new build in the same shell.
 */
public final class UpdateCli {

	private UpdateCli() {}

	/** Runs the flow and returns the process exit code. */
	public static int run() {
		Updater.logLifecycle("cli: update check requested");
		Updater.Release release;
		try {
			release = Updater.fetchLatestRelease();
		} catch (Exception ex) {
			System.out.println("Update check failed: " + ex.getMessage());
			Updater.writeFailure("checking for updates", ex);
			return 1;
		}
		String installed = Updater.installedVersion();
		boolean newer = Updater.isNewer(installed, release.version());
		Updater.logLifecycle(
			"cli checked: installed " + installed + ", latest " + release.version() + ", update available " + newer
		);
		if (!newer) {
			System.out.println("castiel is up to date.");
			return 0;
		}
		String blocked = installBlockReason(release);
		if (blocked != null) {
			System.out.println("castiel " + release.version() + " is available, but " + blocked);
			return 1;
		}
		Console console = System.console();
		boolean assumeYes = Boolean.getBoolean("castiel.update.assume-yes");
		if (console == null && !assumeYes) {
			// Piped or CI runs get a report only; installing needs a person to agree.
			System.out.println("Installed " + installed + ", latest " + release.version() + ".");
			return 0;
		}
		String answer = assumeYes ? "y" : console.readLine("there is a new version, would you like to install? (y/n) ");
		if (answer == null || !"y".equalsIgnoreCase(answer.trim())) {
			System.out.println("Update skipped.");
			return 0;
		}
		try {
			Path archive = Updater.download(release, percent ->
				System.out.print("\rDownloading castiel " + release.version() + "... " + percent + "%")
			);
			System.out.println();
			Updater.spawnSideloader(archive, true, release.version());
			System.out.println(
				"castiel " +
					release.version() +
					" installs when this process exits; the new build starts automatically."
			);
			return 0;
		} catch (Exception ex) {
			System.out.println("Update failed: " + ex.getMessage());
			Updater.writeFailure("installing the update", ex);
			return 1;
		}
	}

	/** Why the running build cannot install this release, or null when it can. */
	private static String installBlockReason(Updater.Release release) {
		if (Updater.installDir() == null) {
			return "updates are only available in packaged installs.";
		}
		if (release.assetName() == null) {
			return "no castiel build is published for this platform.";
		}
		if (release.downloadUrl() == null) {
			return "this release has no bundle for this platform.";
		}
		return null;
	}
}
