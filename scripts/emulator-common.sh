#!/usr/bin/env bash
# Shared targeting for emulator-only UI tooling; source from the command scripts.

HYDRAFIT_SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
HYDRAFIT_ROOT="$(cd "$HYDRAFIT_SCRIPT_DIR/.." && pwd)"
HYDRAFIT_PACKAGE="com.hydrafit.app"

emulator_adb() {
  perl -e 'alarm 120; exec @ARGV' adb -s "$ANDROID_SERIAL" "$@"
}

require_emulator() {
  if [[ ! "${ANDROID_SERIAL:-}" =~ ^emulator-[0-9]+$ ]]; then
    echo "Set ANDROID_SERIAL to an explicit running emulator serial from adb devices -l." >&2
    return 1
  fi
  if ! command -v adb >/dev/null || ! command -v perl >/dev/null; then
    echo "adb and perl must be available on PATH." >&2
    return 1
  fi
  if [[ "$(emulator_adb get-state)" != "device" ]]; then
    echo "The selected emulator is not connected and ready; start it manually." >&2
    return 1
  fi
  if ! emulator_adb emu avd name >/dev/null; then
    echo "The selected target did not respond as an Android emulator." >&2
    return 1
  fi
}
