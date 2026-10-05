#!/usr/bin/env bash
set -euo pipefail
# usage: scripts/tap.sh <x> <y> [--screenshot [output.png]]

if [ "$#" -lt 2 ]; then
  echo "usage: scripts/tap.sh <x> <y> [--screenshot [output.png]]" >&2
  exit 1
fi

for arg in "$1" "$2"; do
  case "$arg" in
    '' | *[!0-9]*)
      echo "x and y must be non-negative integers" >&2
      exit 1
      ;;
  esac
done

X="$1"
Y="$2"
shift 2
CAPTURE=false
OUT="/tmp/hydrafit-screen.png"
if [ "$#" -gt 0 ]; then
  if [ "$1" != "--screenshot" ] || [ "$#" -gt 2 ] ||
    { [ "$#" -eq 2 ] && [ -z "$2" ]; }; then
    echo "usage: scripts/tap.sh <x> <y> [--screenshot [output.png]]" >&2
    exit 1
  fi
  CAPTURE=true
  OUT="${2:-$OUT}"
fi

source "$(dirname "${BASH_SOURCE[0]}")/emulator-common.sh"
require_emulator
emulator_adb shell input tap "$X" "$Y"
if [ "$CAPTURE" = true ]; then
  sleep 1
  emulator_adb exec-out screencap -p > "$OUT"
  echo "Tapped ($X,$Y); screenshot saved to $OUT."
else
  echo "Tapped ($X,$Y)."
fi
