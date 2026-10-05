#!/usr/bin/env bash
set -euo pipefail
# usage: scripts/deploy.sh [build-log]

if [ "$#" -gt 1 ] || { [ "$#" -eq 1 ] && [ -z "$1" ]; }; then
  echo "usage: scripts/deploy.sh [build-log]" >&2
  exit 1
fi

source "$(dirname "${BASH_SOURCE[0]}")/emulator-common.sh"
require_emulator
LOG="${1:-/tmp/hydrafit-deploy.log}"
if ! perl -e 'alarm 600; exec @ARGV' "$HYDRAFIT_ROOT/gradlew" -p "$HYDRAFIT_ROOT" :androidApp:assembleDebug > "$LOG" 2>&1; then
  echo "Build failed or timed out; inspect $LOG before retrying." >&2
  exit 1
fi
APK="$HYDRAFIT_ROOT/androidApp/build/outputs/apk/debug/androidApp-debug.apk"
if [ ! -f "$APK" ]; then
  echo "Expected debug APK not found at $APK; inspect $LOG." >&2
  exit 1
fi
emulator_adb install -r "$APK"
echo "Installed debug APK on $ANDROID_SERIAL; build log: $LOG"
