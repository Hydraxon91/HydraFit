#!/usr/bin/env bash
set -euo pipefail

# usage: ANDROID_SERIAL=emulator-5554 bash scripts/maestro-scroll.sh \
#          (--target-id <resource-id> | --target-text <regex>) [--container-id logger_list] [...]
#
# Container-aware, bounded scrolling for a Maestro hierarchy: it brings a target into view by
# swiping the tagged outer container while avoiding nested scroll containers. Inspects and swipes
# only — never taps, logs a set, restarts the app or clears state. The wrapper owns the verified
# target; remaining args pass through to scripts/maestro-scroll.py (see `--help`).

source "$(dirname "${BASH_SOURCE[0]}")/emulator-common.sh"
require_emulator

# The wrapper owns the verified target: refuse a caller-supplied device so the verified serial
# cannot be silently overridden by a later argument.
for arg in "$@"; do
  case "$arg" in
    --device | --device=* | --expected-device | --expected-device=*)
      echo "scripts/maestro-scroll.sh owns --device; remove $arg." >&2
      exit 2
      ;;
  esac
done

exec python3 "$HYDRAFIT_SCRIPT_DIR/maestro-scroll.py" \
  --device "$ANDROID_SERIAL" \
  --expected-device "$ANDROID_SERIAL" \
  --app-id "$HYDRAFIT_PACKAGE" \
  "$@"
