#!/bin/sh
# Sideloads a downloaded castiel bundle: extracts it, waits for the running app
# to exit, then swaps the whole install directory. Writes update-ok / update-error
# markers plus update-failure.log into the work dir so the next launch reports it.
#
# Args come from the Java spawner: app_pid install_dir archive work_dir mode version.
set -u
APP_PID="$1"
INSTALL_DIR="$2"
ARCHIVE="$3"
WORK_DIR="$4"
MODE="$5"
VERSION="$6"

# Never hold the install dir as the working directory; it blocks renames.
cd "$(mktemp -d)" || exit 1

# Stay alive when the terminal that started the app closes.
trap '' HUP

log() {
	printf '%s %s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)" "$*" >> "$WORK_DIR/sideload.log"
}

fail() {
	STEP="$1"
	DETAIL="$2"
	# Snapshot every process whose path is under the install dir; it names the blocker.
	LOCKERS=""
	for proc in /proc/[0-9]*; do
		[ -r "$proc/cmdline" ] || continue
		CMD="$(tr '\0' ' ' < "$proc/cmdline" 2>/dev/null)"
		case " $CMD " in
			*" $INSTALL_DIR"*) LOCKERS="$LOCKERS
${proc#/proc/} $CMD" ;;
		esac
	done
	[ -n "$LOCKERS" ] || LOCKERS="
none detected"
	printf 'step: %s\nerror: %s\nprocesses under the install dir:%s\n' "$STEP" "$DETAIL" "$LOCKERS" \
		> "$WORK_DIR/update-failure.log"
	printf 'The update could not be applied.' > "$WORK_DIR/update-error"
	log "failed at step $STEP: $DETAIL"
	if [ "$MODE" = "session" ]; then
		echo "Update failed. See $WORK_DIR/update-failure.log"
	fi
}

log "started: app pid $APP_PID, mode $MODE, target version $VERSION"
NEW_DIR="$INSTALL_DIR.new"
OLD_DIR="$INSTALL_DIR.old"
rm -rf "$NEW_DIR" "$OLD_DIR"

log "extracting $ARCHIVE"
case "$ARCHIVE" in
	*.zip)
		unzip -q "$ARCHIVE" -d "$NEW_DIR" || { fail extracting "unzip failed"; exit 1; } ;;
	*.tar.gz)
		tar -xzf "$ARCHIVE" -C "$NEW_DIR" || { fail extracting "tar failed"; exit 1; } ;;
	*)
		fail extracting "unknown archive type"; exit 1 ;;
esac
[ -d "$NEW_DIR/castiel" ] || { fail extracting "the inner castiel folder is missing from the archive"; exit 1; }

log "waiting for app pid $APP_PID to exit"
while kill -0 "$APP_PID" 2>/dev/null; do
	sleep 1
done

# Swap with retries: a straggler process inside the install dir can block the rename.
SWAPPED=0
ATTEMPT=1
while [ "$ATTEMPT" -le 60 ]; do
	log "swap attempt $ATTEMPT"
	if [ -d "$INSTALL_DIR" ] && [ ! -d "$OLD_DIR" ]; then
		mv "$INSTALL_DIR" "$OLD_DIR" || { ATTEMPT=$((ATTEMPT + 1)); sleep 1; continue; }
	fi
	if mv "$NEW_DIR/castiel" "$INSTALL_DIR"; then
		SWAPPED=1
		break
	fi
	ATTEMPT=$((ATTEMPT + 1))
	sleep 1
done
if [ "$SWAPPED" -ne 1 ]; then
	# Put the old build back so the app does not vanish from disk.
	[ -d "$INSTALL_DIR" ] || mv "$OLD_DIR" "$INSTALL_DIR" 2>/dev/null
	fail swapping "the install directory rename stayed blocked after 60 attempts"
	exit 1
fi

printf '%s' "$VERSION" > "$WORK_DIR/update-ok"
log "updated to $VERSION"
rm -rf "$OLD_DIR" "$NEW_DIR"
# The ~95 MB archive has no use after the swap.
rm -f "$ARCHIVE" "$WORK_DIR/sideload.pid"
if [ "$MODE" = "session" ]; then
	echo "castiel was updated to v$VERSION. Starting it again..."
	case "$(uname)" in
		Darwin) open "$INSTALL_DIR/castiel.app" ;;
		*) "$INSTALL_DIR/bin/castiel" >/dev/null 2>&1 & ;;
	esac
fi
