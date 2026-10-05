#!/usr/bin/env bash
set -euo pipefail
# usage: scripts/launch.sh [--restart]

if [ "$#" -gt 1 ] || { [ "$#" -eq 1 ] && [ "$1" != "--restart" ]; }; then
  echo "usage: scripts/launch.sh [--restart]" >&2
  exit 1
fi

source "$(dirname "${BASH_SOURCE[0]}")/emulator-common.sh"
require_emulator
if [ "${1:-}" = "--restart" ]; then
  emulator_adb shell am force-stop "$HYDRAFIT_PACKAGE"
fi
emulator_adb shell am start -W -n "$HYDRAFIT_PACKAGE/.MainActivity"
