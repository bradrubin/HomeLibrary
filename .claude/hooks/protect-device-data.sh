#!/bin/bash
# PreToolUse hook for Bash: protects the Book Catalog library on attached devices.
#  - Blocks commands that uninstall the app or wipe its data (connected tests,
#    uninstall tasks, pm uninstall/clear, rm through run-as).
#  - Before anything that installs over the app or writes into its data folder,
#    copies databases/ and files/ from every attached device to the Mac. If that
#    backup fails, the command is blocked.
# Exit 2 blocks the command and shows stderr to Claude.

set -u
PACKAGE="com.example.bookcatalog"
BACKUP_ROOT="${HOME}/Documents/HomeLibrary-device-backups"

command=$(jq -r '.tool_input.command // empty')
[ -z "$command" ] && exit 0

block() {
  echo "BLOCKED by .claude/hooks/protect-device-data.sh: $1" >&2
  echo "This would delete the user's real library. Do not retry or work around it; ask the user to run it themselves if they really want it." >&2
  exit 2
}

if grep -Eq 'connected[A-Za-z]*(AndroidTest|Check)|deviceCheck' <<<"$command"; then
  block "connected/device tests uninstall the app when they finish."
fi
if grep -Eq '(^|[^A-Za-z])uninstall(All|Debug|Release|[A-Z][A-Za-z]*AndroidTest)' <<<"$command"; then
  block "Gradle uninstall tasks remove the app and its data."
fi
if grep -Eq '(adb|pm|cmd package)[^|;&]*[[:space:]](uninstall|clear)([[:space:]]|$)' <<<"$command"; then
  block "uninstalling or clearing the app deletes its data."
fi
if grep -Eq "run-as[[:space:]]+${PACKAGE//./\\.}[^|;&]*[[:space:]]rm[[:space:]]" <<<"$command" \
  || grep -Eq "adb[^|;&]*shell[^|;&]*[[:space:]]rm[[:space:]][^|;&]*${PACKAGE//./\\.}" <<<"$command"; then
  block "deleting files from the app's data folder."
fi

needs_backup=0
if grep -Eq '(^|[^A-Za-z])install(Debug|Release)' <<<"$command" \
  || grep -Eq 'adb[^|;&]*[[:space:]]install(-multiple)?([[:space:]]|$)' <<<"$command" \
  || grep -Eq 'pm[[:space:]]+install' <<<"$command" \
  || grep -Eq "run-as[[:space:]]+${PACKAGE//./\\.}[^|;&]*[[:space:]](cp|mv|dd|tee|sqlite3)[[:space:]]" <<<"$command"; then
  needs_backup=1
fi
[ "$needs_backup" -eq 0 ] && exit 0

command -v adb >/dev/null 2>&1 || PATH="$PATH:$HOME/Library/Android/sdk/platform-tools"
command -v adb >/dev/null 2>&1 || block "adb not found, so the library could not be backed up first."

stamp=$(date +%Y%m%d-%H%M%S)
serials=$(adb devices | awk 'NR > 1 && $2 == "device" { print $1 }')
for serial in $serials; do
  # Nothing to back up if the app isn't installed on this device.
  adb -s "$serial" shell pm path "$PACKAGE" 2>/dev/null | grep -q '^package:' || continue
  dir="$BACKUP_ROOT/$serial"
  mkdir -p "$dir" || block "could not create $dir for the backup."
  out="$dir/$stamp.tar"
  # Stop the app so the database and its WAL file are copied in a consistent state.
  adb -s "$serial" shell am force-stop "$PACKAGE" >/dev/null 2>&1
  adb -s "$serial" exec-out run-as "$PACKAGE" tar cf - databases files >"$out" 2>/dev/null
  if ! tar tf "$out" 2>/dev/null | grep -q '^databases/'; then
    rm -f "$out"
    block "backing up the library from $serial failed, so the install was stopped."
  fi
  echo "Backed up $PACKAGE data from $serial to $out ($(du -h "$out" | cut -f1))" >&2
done
exit 0
