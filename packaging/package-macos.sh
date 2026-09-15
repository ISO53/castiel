#!/usr/bin/env bash
# Builds the castiel macOS bundle: a self-contained castiel.app (launcher +
# private bundled runtime, unsigned, default icon) zipped for direct use.
# Output lands in packaging/dist/.
#
# Usage:  ./package-macos.sh [--skip-ui-build] [--skip-maven] [--skip-archive]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(dirname "$SCRIPT_DIR")"

SKIP_UI_BUILD=false; SKIP_MAVEN=false; SKIP_ARCHIVE=false
for arg in "$@"; do
	case "$arg" in
		--skip-ui-build) SKIP_UI_BUILD=true ;;
		--skip-maven)    SKIP_MAVEN=true ;;
		--skip-archive)  SKIP_ARCHIVE=true ;;
		*) echo "unknown option: $arg"; exit 2 ;;
	esac
done

JPACKAGE="${JAVA_HOME:+$JAVA_HOME/bin/jpackage}"
if [ -z "$JPACKAGE" ] || [ ! -x "$JPACKAGE" ]; then
	JPACKAGE="$(command -v jpackage || true)"
fi
if [ -z "$JPACKAGE" ]; then
	echo "jpackage not found; install a full JDK 25+ or set JAVA_HOME." >&2
	exit 1
fi
echo "Using $JPACKAGE"

# --- 1. frontend (the built dist is embedded into the jar by Maven) -------------
if [ "$SKIP_UI_BUILD" = false ]; then
	cd "$REPO_ROOT/frontend"
	[ -d node_modules ] || npm install
	npm run build
fi

# --- 2. harness fat jar -----------------------------------------------------------
cd "$REPO_ROOT/harness"
if [ "$SKIP_MAVEN" = false ]; then
	mvn clean package -DskipTests
fi
VERSION="$(mvn -q help:evaluate -Dexpression=project.version -DforceStdout)"
echo "Packaging castiel $VERSION"

# --- 3. stage the fat jar (jpackage copies everything under --input) -------------
STAGE="$SCRIPT_DIR/stage"
rm -rf "$STAGE" "$SCRIPT_DIR/dist"
mkdir -p "$STAGE"
cp "target/castiel-$VERSION.jar" "$STAGE/"

# --- 4. app-image -------------------------------------------------------------------
# No --main-class on purpose: the Spring Boot fat jar must boot through its
# manifest Main-Class (the JarLauncher), which unpacks BOOT-INF; passing the app
# class directly fails with ClassNotFoundException. Unsigned on purpose; no
# .icns yet, so jpackage falls back to the default app icon.
"$JPACKAGE" \
	--type app-image \
	--name castiel \
	--app-version "$VERSION" \
	--input "$STAGE" \
	--main-jar "castiel-$VERSION.jar" \
	--dest "$SCRIPT_DIR/dist"
echo "App image: $SCRIPT_DIR/dist/castiel"

# --- 5. zip (ditto preserves macOS metadata) -----------------------------------------
ARCH="$(uname -m)"
if [ "$SKIP_ARCHIVE" = false ]; then
	ZIP="$SCRIPT_DIR/dist/castiel-macos-$ARCH.zip"
	ditto -c -k --keepParent "$SCRIPT_DIR/dist/castiel/castiel.app" "$ZIP"
	echo "Created $ZIP"
fi

# --- 6. clean up ----------------------------------------------------------------------
rm -rf "$STAGE"
