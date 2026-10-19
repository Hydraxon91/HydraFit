#!/usr/bin/env bash
set -euo pipefail

# Resolve the Maestro CLI once, disable analytics, and pass every argument
# through unchanged. This wrapper never infers a device or changes app/device
# state; callers still supply --device and their own hard timeout.

if [[ -n "${MAESTRO_BIN:-}" ]]; then
  binary=$MAESTRO_BIN
elif binary=$(command -v maestro); then
  :
elif [[ -x "${HOME:-/nonexistent}/.maestro/bin/maestro" ]]; then
  binary="${HOME}/.maestro/bin/maestro"
else
  echo "maestro-cli.sh: Maestro binary not found; set MAESTRO_BIN or install Maestro." >&2
  exit 127
fi

export MAESTRO_CLI_NO_ANALYTICS=1
export MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED=true
exec "$binary" "$@"
