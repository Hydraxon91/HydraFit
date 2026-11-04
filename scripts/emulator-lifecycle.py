#!/usr/bin/env python3
"""Start or gracefully stop an approved test AVD; never wipe or force recovery.

The caller owns approval, journey completion and unknown-write reconciliation.
This helper checks device identity, not whether an app write is safe to interrupt.
"""

from __future__ import annotations

import argparse
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time

ALLOWED_AVDS = (
    "Medium_Phone", "Pixel_Tablet", "Pixel_Fold", "6.7_Horizontal_Fold-in",
)


class LifecycleError(Exception):
    """A failed or uncertain lifecycle operation; no automatic recovery is safe."""


class Lifecycle:
    def __init__(self, adb: str, emulator: str, timeout: float):
        self.adb = adb
        self.emulator = emulator
        self.deadline = time.monotonic() + timeout

    def remaining(self) -> float:
        remaining = self.deadline - time.monotonic()
        if remaining <= 0:
            raise LifecycleError("Deadline exceeded; inspect the outcome before another operation.")
        return remaining

    def run(self, command: list[str]) -> str:
        result = subprocess.run(command, capture_output=True, text=True,
                                timeout=min(10, self.remaining()), check=False)
        if result.returncode:
            raise LifecycleError(f"Command failed ({result.returncode}): {command!r}; "
                                 f"{result.stderr.strip()}")
        return result.stdout.strip()

    def devices(self) -> list[tuple[str, str]]:
        output = self.run([self.adb, "devices"])
        lines = output.splitlines()
        if not lines or lines[0].strip() != "List of devices attached":
            raise LifecycleError("Unexpected adb device listing; refusing lifecycle operations.")
        devices = []
        for line in lines[1:]:
            if not line.strip():
                continue
            fields = line.split()
            if len(fields) != 2:
                raise LifecycleError("Unrecognized device entry; inspect adb state manually.")
            devices.append((fields[0], fields[1]))
        return devices

    def avd_name(self, serial: str) -> str:
        output = self.run([self.adb, "-s", serial, "emu", "avd", "name"])
        lines = output.splitlines()
        if len(lines) != 2 or lines[-1] != "OK":
            raise LifecycleError("Cannot establish exact AVD identity.")
        return lines[0]

    def emulator_processes(self) -> list[str]:
        output = self.run(["/bin/ps", "-axo", "pid=,comm="])
        processes = []
        for line in output.splitlines():
            fields = line.strip().split(None, 1)
            if len(fields) != 2 or not fields[0].isdigit():
                raise LifecycleError("Cannot establish emulator process state.")
            executable = Path(fields[1]).name
            if executable in ("emulator", "emulator-headless") or executable.startswith("qemu-system-"):
                processes.append(fields[0])
        return processes

    def current(self) -> tuple[str, str] | None:
        devices = self.devices()
        if any(not serial.startswith("emulator-") or not serial[9:].isdigit()
               for serial, _ in devices):
            raise LifecycleError("A non-emulator target is connected; disconnect it before switching.")
        if len(devices) > 1:
            raise LifecycleError("More than one emulator is connected; resolve ownership manually.")
        if not devices:
            return None
        serial, status = devices[0]
        if status != "device":
            raise LifecycleError(f"{serial} is {status}; inspect it without retrying lifecycle operations.")
        name = self.avd_name(serial)
        if name not in ALLOWED_AVDS:
            raise LifecycleError(f"Connected AVD {name!r} is not allowlisted.")
        return serial, name

    def start(self, name: str, log: Path) -> str:
        if self.current() is not None:
            raise LifecycleError("An emulator is already connected; start does not stop or restart it.")
        if self.emulator_processes():
            raise LifecycleError("An emulator process exists without a ready target; inspect it manually.")
        available = self.run([self.emulator, "-list-avds"]).splitlines()
        if name not in available:
            raise LifecycleError(f"AVD {name!r} is not installed.")
        # Exclusive creation avoids replacing another run's startup evidence.
        with log.open("x") as output:
            process = subprocess.Popen(
                [self.emulator, "-avd", name], stdout=output, stderr=subprocess.STDOUT,
                start_new_session=True,
            )
        print(f"Started emulator process {process.pid}; log: {log}", flush=True)
        while True:
            self.remaining()
            if process.poll() is not None:
                raise LifecycleError("Emulator launcher exited before readiness; inspect the log and device state.")
            devices = self.devices()
            if any(not serial.startswith("emulator-") or not serial[9:].isdigit()
                   for serial, _ in devices) or len(devices) > 1:
                raise LifecycleError("Unexpected connected targets during startup; no automatic cleanup performed.")
            if devices and devices[0][1] == "device":
                serial = devices[0][0]
                if self.avd_name(serial) != name:
                    raise LifecycleError("Connected AVD differs from the requested AVD.")
                booted = self.run([self.adb, "-s", serial, "shell", "getprop", "sys.boot_completed"])
                if booted == "1":
                    return serial
            time.sleep(min(1, self.remaining()))

    def stop(self, name: str) -> str:
        current = self.current()
        if current is None or current[1] != name:
            raise LifecycleError("The requested AVD is not the sole connected, ready emulator.")
        serial = current[0]
        self.run([self.adb, "-s", serial, "emu", "kill"])
        while True:
            self.remaining()
            devices = self.devices()
            if not devices:
                if not self.emulator_processes():
                    return serial
            if any(found != serial for found, _ in devices):
                raise LifecycleError("Unexpected target during shutdown; inspect manually.")
            time.sleep(min(1, self.remaining()))


def sdk_binary(name: str) -> str:
    found = shutil.which(name)
    if found:
        return found
    sdk = Path(os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT")
               or Path.home() / "Library/Android/sdk")
    candidate = sdk / ("platform-tools" if name == "adb" else "emulator") / name
    if not candidate.is_file():
        raise LifecycleError(f"Cannot locate {name}; configure the existing Android SDK.")
    return str(candidate)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=("start", "stop"))
    parser.add_argument("avd", choices=ALLOWED_AVDS)
    parser.add_argument("--timeout", type=int, default=180, choices=range(1, 301),
                        metavar="SECONDS")
    parser.add_argument("--log", type=Path, help="Fresh external startup log (required for start)")
    parser.add_argument("--journey-ended", action="store_true",
                        help="Caller confirms no controller or unresolved write remains (required for stop)")
    args = parser.parse_args()
    if args.action == "start" and args.log is None:
        parser.error("start requires --log pointing to a fresh external file")
    if args.log is not None and args.log.resolve().is_relative_to(Path(__file__).resolve().parent.parent):
        parser.error("startup logs must be outside the checkout")
    if args.action == "stop" and not args.journey_ended:
        parser.error("stop requires --journey-ended; this declaration is not a safety verification")
    try:
        lifecycle = Lifecycle(sdk_binary("adb"), sdk_binary("emulator"), args.timeout)
        if args.action == "start":
            serial = lifecycle.start(args.avd, args.log)
            print(f"Ready: {args.avd}; ANDROID_SERIAL={serial}")
        else:
            serial = lifecycle.stop(args.avd)
            print(f"Stopped: {args.avd}; {serial}; emulator processes exited.")
        return 0
    except (LifecycleError, OSError, subprocess.TimeoutExpired) as error:
        print(f"Lifecycle stopped: {error}. No retry, wipe or forced termination performed.", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
