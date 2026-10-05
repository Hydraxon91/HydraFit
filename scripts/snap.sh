#!/usr/bin/env bash
set -euo pipefail

# usage: ANDROID_SERIAL=emulator-5554 bash scripts/snap.sh [output.png]
if [ "$#" -gt 1 ] || { [ "$#" -eq 1 ] && [ -z "$1" ]; }; then
  echo "usage: scripts/snap.sh [output.png]" >&2
  exit 1
fi

source "$(dirname "${BASH_SOURCE[0]}")/emulator-common.sh"
require_emulator
OUT="${1:-/tmp/hydrafit-screen.png}"
emulator_adb exec-out screencap -p > "$OUT"
echo "Saved screenshot to $OUT"
