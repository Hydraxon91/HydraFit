#!/usr/bin/env bash
set -euo pipefail
# usage: scripts/tap.sh <x> <y> [output.png]

if [ "$#" -lt 2 ]; then
  echo "usage: $0 <x> <y> [output.png]" >&2
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

if ! adb get-state >/dev/null 2>&1; then
  echo "No adb device/emulator connected. Start one and retry." >&2
  exit 1
fi

adb shell input tap "$1" "$2"
sleep 1
adb exec-out screencap -p > "${3:-/tmp/hydrafit-screen.png}"
echo "Tapped ($1,$2); screenshot saved."
