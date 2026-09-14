package io.github.iso53.castiel.cli;

import picocli.CommandLine.IVersionProvider;

/**
 * Supplies the application version for {@code --version}, read from the jar
 * manifest's {@code Implementation-Version} (generated from the pom by the
 * spring-boot-maven-plugin on every repackaged jar.
 */
public final class AppVersionProvider implements IVersionProvider {

	@Override
	public String[] getVersion() {
		return new String[] { "castiel " + readAppVersion() };
	}

	/** Reads {@code Implementation-Version} from the manifest of the code source. */
	public static String readAppVersion() {
		Package pkg = AppVersionProvider.class.getPackage();
		String version = pkg != null ? pkg.getImplementationVersion() : null;
		return version != null ? version : "dev";
	}
}
