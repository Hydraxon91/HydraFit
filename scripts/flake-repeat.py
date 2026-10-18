#!/usr/bin/env python3
"""Repeat a bounded command to surface flaky failures as evidence.

Usage:
  python3 scripts/flake-repeat.py <seconds> <runs> <sweep-directory> -- <command...>

Each run goes through `scripts/run-bounded.py`, so the wall-clock limit,
process-group termination and result JSON are inherited rather than reimplemented.
Repeating a Gradle task is an up-to-date no-op unless the command asks for fresh
work (for example `--rerun-tasks`); when every run reported Gradle tasks but none
executed, the sweep is reported inconclusive instead of passing, so an inherited
result is never read as a flake sweep that actually ran.

A sweep directory is refused when it already holds results, so a previous sweep's
pass can never be consumed as this one's. The per-run result JSON, the per-run log
and a `summary.json` are written there.

Exit codes:
    0    every run passed and the sweep was fresh
    2    at least one run failed; distinct failing tests are listed
    3    inconclusive: no run executed a Gradle task, so nothing was really swept
    125  usage error or an unusable sweep directory
"""

from __future__ import annotations

import json
import math
from pathlib import Path
import re
import subprocess
import sys

USAGE_EXIT = 125
SUMMARY_PREFIX = "[flake-repeat]"
INCONCLUSIVE_EXIT = 3
FAILURE_EXIT = 2
FAILED_RE = re.compile(r"^(.+?) FAILED$", re.MULTILINE)


def _fail(message: str) -> int:
    print(f"{SUMMARY_PREFIX} error: {message}", file=sys.stderr)
    print(f"{SUMMARY_PREFIX} usage: flake-repeat.py <seconds> <runs> <sweep-directory> "
          f"-- <command...>", file=sys.stderr)
    return USAGE_EXIT


def parse_args(arguments: list[str]) -> tuple[float, int, Path, list[str]]:
    if "--" not in arguments:
        raise ValueError("missing -- separator before the command")
    separator = arguments.index("--")
    head, command = arguments[:separator], arguments[separator + 1:]
    if len(head) != 3 or not command:
        raise ValueError("expected <seconds> <runs> <sweep-directory> -- <command...>")
    try:
        seconds = float(head[0])
        runs = int(head[1])
    except ValueError as error:
        raise ValueError("seconds must be a number and runs an integer") from error
    if not math.isfinite(seconds) or seconds <= 0 or runs < 1:
        raise ValueError("seconds must be positive and finite and runs at least 1")
    return seconds, runs, Path(head[2]), command


def prepare_sweep(sweep: Path) -> None:
    if any(part == ".." for part in sweep.parts):
        raise ValueError("sweep path must not contain '..'")
    if sweep.is_symlink() or (sweep.exists() and not sweep.is_dir()):
        raise ValueError("sweep path must be a directory, not a symlink or a file")
    if sweep.exists() and any(sweep.glob("run-*.json")):
        raise ValueError("sweep directory already holds results; choose a fresh one")
    sweep.mkdir(parents=True, exist_ok=True)


def run_once(bounded: Path, seconds: float, sweep: Path, index: int,
             command: list[str]) -> dict:
    result_path = sweep / f"run-{index}.json"
    log_path = sweep / f"run-{index}.log"
    completed = subprocess.run(
        [sys.executable, str(bounded), "--result-json", str(result_path),
         str(seconds), str(log_path), "--", *command],
        capture_output=True, text=True, check=False)
    record = {"run": index, "wrapper_exit": completed.returncode, "exit": None,
              "outcome": None, "executed": None, "up_to_date": None, "failures": []}
    try:
        data = json.loads(result_path.read_text())
    except (OSError, ValueError):
        return record
    record["exit"] = data.get("exit")
    record["outcome"] = data.get("outcome")
    tasks = data.get("gradle_tasks")
    if isinstance(tasks, dict):
        record["executed"] = tasks.get("executed")
        record["up_to_date"] = tasks.get("up_to_date")
    if log_path.is_file():
        record["failures"] = sorted(set(FAILED_RE.findall(log_path.read_text(errors="replace"))))
    return record


def main(argv: list[str] | None = None) -> int:
    arguments = list(sys.argv[1:] if argv is None else argv)
    try:
        seconds, runs, sweep, command = parse_args(arguments)
        prepare_sweep(sweep)
    except (ValueError, OSError) as error:
        return _fail(str(error))
    bounded = Path(__file__).resolve().with_name("run-bounded.py")
    results = [run_once(bounded, seconds, sweep, index, command)
               for index in range(1, runs + 1)]
    failed = [record["run"] for record in results
              if record["wrapper_exit"] != 0 or record["exit"] != 0]
    reported = [record for record in results if record["executed"] is not None]
    # A non-Gradle command always ran; a Gradle sweep that executed nothing did not.
    fresh = not reported or any(record["executed"] for record in reported)
    distinct = sorted({name for record in results for name in record["failures"]})
    summary = {"version": 1, "runs": runs, "command": command, "sweep": str(sweep),
               "failed_runs": failed, "distinct_failures": distinct, "fresh": fresh,
               "results": results}
    summary_path = sweep / "summary.json"
    summary_path.write_text(json.dumps(summary, indent=2) + "\n")
    print(f"{SUMMARY_PREFIX} runs={runs} failed={len(failed)} "
          f"fresh={str(fresh).lower()} summary={summary_path}")
    if distinct:
        print(f"{SUMMARY_PREFIX} distinct_failures={'; '.join(distinct)}")
    if failed:
        return FAILURE_EXIT
    return 0 if fresh else INCONCLUSIVE_EXIT


if __name__ == "__main__":
    sys.exit(main())
