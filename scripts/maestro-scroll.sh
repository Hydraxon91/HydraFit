#!/usr/bin/env bash
set -euo pipefail

# usage: ANDROID_SERIAL=emulator-5554 bash scripts/maestro-scroll.sh \
#          (--target-id <resource-id> | --target-text <regex>) [--container-id logger_list] [...]
#
# Container-aware, bounded scrolling for a Maestro hierarchy: it brings a target into view by
# swiping the tagged outer container while avoiding nested scroll containers. Inspects and swipes
# only — never taps, logs a set, restarts the app or clears state. Args after this point are passed
# straight to scripts/maestro-scroll.py (see `--help`).

source "$(dirname "${BASH_SOURCE[0]}")/emulator-common.sh"
require_emulator

exec python3 "$HYDRAFIT_SCRIPT_DIR/maestro-scroll.py" \
  --device "$ANDROID_SERIAL" \
  --app-id "$HYDRAFIT_PACKAGE" \
  "$@"
