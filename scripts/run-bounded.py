#!/usr/bin/env python3
"""Run a command with a hard wall-clock limit and bounded termination.

Usage:
  python3 scripts/run-bounded.py <seconds> <logfile> -- <command...>

The command runs in its own process group with stdout and stderr redirected to
`logfile` (created fresh). If it outlives `seconds`, the group is sent SIGTERM,
given `TERM_GRACE_SECONDS` to exit, then sent SIGKILL. Termination is reported as
confirmed only when the group is verifiably empty; otherwise the summary states
that termination is not guaranteed, because a surviving process may still be
writing to the log.

Exit codes:
    0..127, 128+signal   the command's own exit status
    124                  the command exceeded the time limit
    125                  usage error or a failure to start the command
"""

from __future__ import annotations

import math
import os
import signal
import subprocess
import sys
import time

TERM_GRACE_SECONDS = 3.0
TIMEOUT_EXIT = 124
USAGE_EXIT = 125
SUMMARY_PREFIX = "[run-bounded]"
POLL_INTERVAL_SECONDS = 0.05


def _fail(message: str) -> int:
    print(f"{SUMMARY_PREFIX} error: {message}", file=sys.stderr)
    print(
        f"{SUMMARY_PREFIX} usage: run-bounded.py <seconds> <logfile> -- <command...>",
        file=sys.stderr,
    )
    return USAGE_EXIT


def _summary(text: str) -> None:
    print(f"{SUMMARY_PREFIX} {text}")


def parse_args(argv: list[str]) -> tuple[float, str, list[str]]:
    """Return (seconds, logfile, command), raising ValueError on a usage error."""
    if "--" not in argv:
        raise ValueError("missing '--' separator before the command")
    separator = argv.index("--")
    before = argv[:separator]
    command = argv[separator + 1:]
    if len(before) != 2:
        raise ValueError("expected exactly <seconds> and <logfile> before '--'")
    seconds_raw, logfile = before
    try:
        seconds = float(seconds_raw)
    except ValueError:
        raise ValueError(f"seconds {seconds_raw!r} is not a number") from None
    if not math.isfinite(seconds):
        raise ValueError(f"seconds must be finite, got {seconds_raw!r}")
    if seconds <= 0:
        raise ValueError(f"seconds must be positive, got {seconds_raw!r}")
    if not command:
        raise ValueError("no command given after '--'")
    return seconds, logfile, command


def signal_exit_status(returncode: int) -> int:
    """A process return code, mapping a negative signal code to 128 + signal."""
    return returncode if returncode >= 0 else 128 - returncode


def group_is_gone(pgid: int) -> bool:
    """True only when the process group is verifiably empty.

    `ProcessLookupError` means no process remains in the group. Any other
    outcome - including a successful probe or an inconclusive permission error -
    leaves termination unproven.
    """
    try:
        os.killpg(pgid, 0)
    except ProcessLookupError:
        return True
    except OSError:
        return False
    return False


def terminate_group(proc: subprocess.Popen, log, elapsed: float) -> tuple[bool, bool]:
    """SIGTERM then SIGKILL the group; return (used_sigkill, group_verified_gone)."""
    log.write(
        f"{SUMMARY_PREFIX} TIMEOUT after {elapsed:.2f}s: "
        f"sending SIGTERM to process group {proc.pid}\n"
    )
    log.flush()
    try:
        os.killpg(proc.pid, signal.SIGTERM)
    except ProcessLookupError:
        pass
    deadline = time.monotonic() + TERM_GRACE_SECONDS
    while time.monotonic() < deadline and proc.poll() is None:
        time.sleep(POLL_INTERVAL_SECONDS)
    used_sigkill = False
    if proc.poll() is None:
        used_sigkill = True
        log.write(
            f"{SUMMARY_PREFIX} group still alive after {TERM_GRACE_SECONDS}s: "
            f"sending SIGKILL to process group {proc.pid}\n"
        )
        log.flush()
        try:
            os.killpg(proc.pid, signal.SIGKILL)
        except ProcessLookupError:
            pass
        proc.wait()
    return used_sigkill, group_is_gone(proc.pid)


def main(argv: list[str] | None = None) -> int:
    arguments = list(sys.argv[1:] if argv is None else argv)
    try:
        seconds, logfile, command = parse_args(arguments)
    except ValueError as error:
        return _fail(str(error))
    try:
        log = open(logfile, "w")  # noqa: SIM115 - held open across the child's lifetime
    except OSError as error:
        return _fail(f"cannot open log file {logfile!r}: {error}")
    start = time.monotonic()
    with log:
        try:
            proc = subprocess.Popen(
                command,
                stdout=log,
                stderr=subprocess.STDOUT,
                start_new_session=True,
            )
        except OSError as error:
            return _fail(f"cannot start command {command[0]!r}: {error}")
        try:
            returncode = proc.wait(timeout=seconds)
        except subprocess.TimeoutExpired:
            used_sigkill, verified = terminate_group(proc, log, time.monotonic() - start)
            elapsed = time.monotonic() - start
            if verified:
                termination = "confirmed"
            else:
                termination = "not-guaranteed; group may still exist or check inconclusive"
            _summary(
                f"outcome=timeout elapsed={elapsed:.2f}s exit={TIMEOUT_EXIT} "
                f"log={logfile} sigkill={used_sigkill} termination={termination}"
            )
            return TIMEOUT_EXIT
    elapsed = time.monotonic() - start
    status = signal_exit_status(returncode)
    _summary(f"outcome=completed elapsed={elapsed:.2f}s exit={status} log={logfile}")
    return status


if __name__ == "__main__":
    sys.exit(main())
