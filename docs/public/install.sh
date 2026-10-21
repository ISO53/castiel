#!/usr/bin/env bash
# castiel installer (Linux / macOS).
#
#   curl -fsSL https://iso53.github.io/castiel/install.sh | bash
#
# Downloads the latest release bundle for the detected platform, installs the
# self-contained app image into the home directory and puts the `castiel`
# launcher on the PATH (Linux) or into the Applications folder (macOS).
set -euo pipefail

REPO="ISO53/castiel"
BASE="https://github.com/${REPO}/releases/latest/download"
PREFIX="$HOME/.local/share/castiel"
BIN_DIR="$HOME/.local/bin"

bold=$'\033[1m'
red=$'\033[0;31m'
green=$'\033[0;32m'
dim=$'\033[0m'

say() { printf '%s\n' "${bold}$*${dim}"; }
ok() { printf '%s\n' "${green}✓${dim} $*"; }
die() {
	printf '%s\n' "${red}error:${dim} $*" >&2
	exit 1
}

# --- platform ----------------------------------------------------------------
OS="$(uname -s)"
ARCH="$(uname -m)"

case "$OS" in
Linux) OS_ID="linux" ;;
Darwin) OS_ID="macos" ;;
*)
	die "unsupported OS '$OS' — grab a bundle from https://github.com/${REPO}/releases/latest"
	;;
esac

if [ "$OS_ID" = "macos" ]; then
	# Multiplexers / CI shells sometimes report x86_64 on Apple Silicon.
	if [ "$ARCH" != "arm64" ] && [ "$(sysctl -n hw.optional.arm64 2>/dev/null || echo 0)" = "1" ]; then
		ARCH="arm64"
	fi
	if [ "$ARCH" != "arm64" ]; then
		die "macOS bundles are arm64-only — grab one from https://github.com/${REPO}/releases/latest"
	fi
	ASSET="castiel-macos-arm64.zip"
else
	case "$ARCH" in
	x86_64 | amd64) ASSET="castiel-linux-x64.tar.gz" ;;
	arm64 | aarch64)
		die "no arm64 Linux bundle yet — grab one from https://github.com/${REPO}/releases/latest"
		;;
	*)
		die "unsupported architecture '$ARCH'"
		;;
	esac
fi

# --- download ----------------------------------------------------------------
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

say "Downloading $ASSET ..."
if command -v curl >/dev/null 2>&1; then
	curl -fsSL --retry 3 -o "$TMP/$ASSET" "$BASE/$ASSET" ||
		die "download failed — check https://github.com/${REPO}/releases"
else
	wget -q -O "$TMP/$ASSET" "$BASE/$ASSET" ||
		die "download failed — check https://github.com/${REPO}/releases"
fi

# --- extract & install ---------------------------------------------------------
say "Extracting ..."
if [ "$OS_ID" = "macos" ]; then
	unzip -q "$TMP/$ASSET" -d "$TMP" || die "extraction failed (unzip required)"

	APP_DIR="/Applications"
	if [ ! -w "$APP_DIR" ]; then
		APP_DIR="$HOME/Applications"
	fi
	mkdir -p "$APP_DIR"
	rm -rf "$APP_DIR/castiel.app"
	cp -R "$TMP/castiel/castiel.app" "$APP_DIR/castiel.app"

	ok "Installed castiel.app to $APP_DIR"
	printf '%s\n' "  Open ${APP_DIR}/castiel.app — right-click → Open on first launch (unsigned build)."
else
	tar -xzf "$TMP/$ASSET" -C "$TMP" || die "extraction failed (tar required)"

	rm -rf "$PREFIX"
	mkdir -p "$(dirname "$PREFIX")"
	mv "$TMP/castiel" "$PREFIX"

	LAUNCHER="$PREFIX/bin/castiel"
	if [ ! -x "$LAUNCHER" ]; then
		LAUNCHER="$(find "$PREFIX" -maxdepth 2 -type f -name castiel -print -quit 2>/dev/null || true)"
	fi
	if [ -z "$LAUNCHER" ] || [ ! -x "$LAUNCHER" ]; then
		die "could not find the castiel launcher in the bundle"
	fi

	mkdir -p "$BIN_DIR"
	ln -sf "$LAUNCHER" "$BIN_DIR/castiel"

	ok "Installed castiel to $PREFIX"
	case ":$PATH:" in
	*":$BIN_DIR:"*) ;;
	*)
		printf '%s\n' "  Add to your PATH:  export PATH=\"$BIN_DIR:\$PATH\"  (then add it to ~/.bashrc or ~/.zshrc)"
		;;
	esac
	printf '%s\n' "  Run:  castiel"
fi
