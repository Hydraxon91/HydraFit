#!/usr/bin/env python3
"""Run a command with a hard wall-clock limit and bounded termination.

Usage:
  python3 scripts/run-bounded.py <seconds> <logfile> -- <command...>
  python3 scripts/run-bounded.py --result-json <fresh-path> <seconds> <logfile> -- <command...>

The command runs in its own process group with stdout and stderr redirected to
`logfile` (created fresh). If it outlives `seconds`, the group is sent SIGTERM,
given `TERM_GRACE_SECONDS` to exit, then sent SIGKILL. Termination is reported as
confirmed only when the group is verifiably empty; otherwise the summary states
that termination is not guaranteed, because a surviving process may still be
writing to the log.

Exit codes:
    0..127, 128+signal   the command's own exit status
    124                  the command exceeded the time limit
    125                  usage/start error or result persistence failure
    130 / 143            wrapper interrupted by SIGINT / SIGTERM (bounded cleanup)
"""

from __future__ import annotations

import math
import json
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
KILL_GRACE_SECONDS = 3.0


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


def terminate_group(proc: subprocess.Popen, log, elapsed: float, reason="TIMEOUT",
                    diagnostic_errors=None) -> tuple[bool, bool]:
    """SIGTERM then SIGKILL the group; return (used_sigkill, group_verified_gone)."""
    errors = diagnostic_errors if diagnostic_errors is not None else []

    def note(message):
        # Cleanup must not depend on a writable filesystem (e.g. a full log volume).
        try:
            log.write(message)
            log.flush()
        except OSError as error:
            errors.append(str(error))

    note(
        f"{SUMMARY_PREFIX} {reason} after {elapsed:.2f}s: "
        f"sending SIGTERM to process group {proc.pid}\n"
    )
    try:
        os.killpg(proc.pid, signal.SIGTERM)
    except OSError:
        pass
    deadline = time.monotonic() + TERM_GRACE_SECONDS
    while time.monotonic() < deadline:
        proc.poll()  # Reap the leader before probing its group.
        if group_is_gone(proc.pid):
            return False, True
        time.sleep(POLL_INTERVAL_SECONDS)
    used_sigkill = False
    if not group_is_gone(proc.pid):
        used_sigkill = True
        note(
            f"{SUMMARY_PREFIX} group still alive after {TERM_GRACE_SECONDS}s: "
            f"sending SIGKILL to process group {proc.pid}\n"
        )
        try:
            os.killpg(proc.pid, signal.SIGKILL)
        except OSError:
            pass
        deadline = time.monotonic() + KILL_GRACE_SECONDS
        while time.monotonic() < deadline:
            proc.poll()
            if group_is_gone(proc.pid):
                return used_sigkill, True
            time.sleep(POLL_INTERVAL_SECONDS)
    proc.poll()
    return used_sigkill, group_is_gone(proc.pid)


def main(argv: list[str] | None = None) -> int:
    arguments = list(sys.argv[1:] if argv is None else argv)
    result_path = None
    if arguments[:1] == ["--result-json"]:
        if len(arguments) < 2:
            return _fail("--result-json requires a fresh output path")
        result_path, arguments = arguments[1], arguments[2:]
    try:
        seconds, logfile, command = parse_args(arguments)
    except ValueError as error:
        return _fail(str(error))
    if result_path and os.path.realpath(result_path) == os.path.realpath(logfile):
        return _fail("result and log paths must differ")
    result_file = None
    try:
        if result_path is not None:
            # Refuse old results before any child starts. Never consume a stale success.
            result_file = open(result_path, "x")
    except OSError as error:
        return _fail(f"cannot create result file: {error}")
    start = time.monotonic()
    result = {"version": 1, "outcome": "startup-failure", "exit": USAGE_EXIT,
              "child_exit": None, "pgid": None, "sigkill": False,
              "termination": "not-started", "signal": None, "diagnostic_errors": []}
    interrupted = None
    handlers = {}

    def on_signal(signum, _frame):
        nonlocal interrupted
        if interrupted is None:
            interrupted = signum

    try:
        for signum in (signal.SIGINT, signal.SIGTERM):
            handlers[signum] = signal.signal(signum, on_signal)
        with open(logfile, "w") as log:
            proc = subprocess.Popen(command, stdout=log, stderr=subprocess.STDOUT,
                                    start_new_session=True)
            result["pgid"] = proc.pid
            deadline = start + seconds
            while proc.poll() is None and interrupted is None and time.monotonic() < deadline:
                time.sleep(min(POLL_INTERVAL_SECONDS, max(0, deadline - time.monotonic())))
            if interrupted is not None or proc.poll() is None:
                result["outcome"] = "interrupted" if interrupted is not None else "timeout"
                result["exit"] = 128 + interrupted if interrupted is not None else TIMEOUT_EXIT
                result["signal"] = interrupted
                result["sigkill"], verified = terminate_group(
                    proc, log, time.monotonic() - start, result["outcome"].upper(), result["diagnostic_errors"])
                # A wrapper signal can arrive while timeout cleanup is in progress.
                if interrupted is not None:
                    result.update(outcome="interrupted", exit=128 + interrupted, signal=interrupted)
            else:
                result["outcome"] = "completed"
                result["exit"] = signal_exit_status(proc.returncode)
                verified = group_is_gone(proc.pid)
            result["child_exit"] = (signal_exit_status(proc.returncode)
                                    if proc.returncode is not None else None)
            result["termination"] = "confirmed" if verified else "not-guaranteed"
    except OSError as error:
        _fail(f"cannot start command/log: {error}")
    finally:
        for signum, handler in handlers.items():
            signal.signal(signum, handler)
        result["elapsed"] = round(time.monotonic() - start, 3)
        if result_file is not None:
            try:
                with result_file:
                    json.dump(result, result_file, allow_nan=False)
                    result_file.write("\n")
                    result_file.flush()
                    os.fsync(result_file.fileno())
            except OSError as error:
                _fail(f"result write failed; inspect command outcome before retry: {error}")
                result["exit"] = USAGE_EXIT
        _summary(" ".join(f"{key}={result[key]}" for key in
                          ("outcome", "elapsed", "exit", "sigkill", "termination")) +
                 f" log={logfile} diagnostic_errors={len(result['diagnostic_errors'])}")
    return result["exit"]


if __name__ == "__main__":
    sys.exit(main())
