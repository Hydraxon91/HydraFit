#!/usr/bin/env bash
set -euo pipefail

PKG="com.hydrafit.app"
OUT="${1:-/tmp/hydrafit-screen.png}"

if ! adb get-state >/dev/null 2>&1; then
  echo "No adb device/emulator connected. Start one and retry." >&2
  exit 1
fi

./gradlew :androidApp:installDebug -q
adb shell am force-stop "$PKG"
adb shell am start -n "$PKG/.MainActivity" >/dev/null
sleep 3   # let the first frame render
adb exec-out screencap -p > "$OUT"
echo "Saved screenshot to $OUT"
