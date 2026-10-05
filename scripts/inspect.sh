#!/usr/bin/env bash
set -euo pipefail
# usage: scripts/inspect.sh [output.xml]

if [ "$#" -gt 1 ] || { [ "$#" -eq 1 ] && [ -z "$1" ]; }; then
  echo "usage: scripts/inspect.sh [output.xml]" >&2
  exit 1
fi

source "$(dirname "${BASH_SOURCE[0]}")/emulator-common.sh"
require_emulator
OUT="${1:-/tmp/hydrafit-ui.xml}"
REMOTE="/data/local/tmp/hydrafit-ui.xml"
DUMP="$(emulator_adb shell uiautomator dump "$REMOTE")"
if [[ "$DUMP" != *"dumped to: $REMOTE"* ]]; then
  echo "UI hierarchy dump did not complete; refusing to read an older hierarchy: $DUMP" >&2
  exit 1
fi
emulator_adb exec-out cat "$REMOTE" | perl -pe 's/></>\n</g' > "$OUT"
echo "Saved UI hierarchy to $OUT; inspect text, descriptions, states and bounds."
