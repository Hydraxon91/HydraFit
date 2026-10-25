#!/usr/bin/env python3
"""Check HydraFit's living documentation for mechanical drift.

Structural mode (default) checks:
  - every document declared in AGENTS.md's living-docs paragraph exists;
  - numbered ATX headings are unique and ascending within their parent group;
  - numbered heading titles are unique per document;
  - relative Markdown links resolve;
  - `docs/doc-impact.json` is present, well formed, and maps only to existing
    living documents.

With `--changed-from REV`, the checker additionally lists the living docs that
should be reviewed for the tracked changes since REV plus untracked files. That
advisory list does not change the exit code.

Read-only and standard library only. Exit codes:
    0    consistent
    1    findings (printed one per line as `path: message`)
    125  invalid input (bad root, revision or git failure)
"""

from __future__ import annotations

import argparse
import pathlib
import subprocess
import sys

import living_docs

DEFAULT_ROOT = pathlib.Path(__file__).resolve().parent.parent
GIT_TIMEOUT_SECONDS = 60


class _ArgumentParser(argparse.ArgumentParser):
    """Use 125 for invalid command-line input instead of argparse's 2."""

    def error(self, message: str) -> None:
        self.print_usage(sys.stderr)
        self.exit(125, f"{self.prog}: error: {message}\n")


def _run_git(root: pathlib.Path, *arguments: str) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["git", *arguments],
        cwd=root,
        capture_output=True,
        text=True,
        check=False,
        timeout=GIT_TIMEOUT_SECONDS,
    )


def _changed_paths(
    root: pathlib.Path, revision: str
) -> tuple[list[str] | None, str | None]:
    try:
        diff = _run_git(root, "diff", "--name-only", revision)
        if diff.returncode != 0:
            detail = diff.stderr.strip() or f"git exited {diff.returncode}"
            return None, f"invalid --changed-from {revision!r}: {detail}"
        untracked = _run_git(root, "ls-files", "--others", "--exclude-standard")
        if untracked.returncode != 0:
            detail = untracked.stderr.strip() or f"git exited {untracked.returncode}"
            return None, f"cannot list untracked files: {detail}"
    except (OSError, subprocess.SubprocessError) as error:
        return None, f"cannot inspect git changes: {error}"

    paths = {
        line.strip()
        for output in (diff.stdout, untracked.stdout)
        for line in output.splitlines()
        if line.strip()
    }
    return sorted(paths), None


def main(argv: list[str] | None = None) -> int:
    parser = _ArgumentParser(description=__doc__)
    parser.add_argument(
        "--root",
        type=pathlib.Path,
        default=DEFAULT_ROOT,
        help="repository root (default: this checkout)",
    )
    parser.add_argument(
        "--changed-from",
        metavar="REV",
        help="also list living docs to review for changes since REV",
    )
    arguments = parser.parse_args(argv)

    root = arguments.root.resolve()
    if not root.is_dir():
        print(f"{root}: root directory does not exist", file=sys.stderr)
        return 125

    findings = living_docs.structural_findings(root)
    for finding in findings:
        print(finding)

    if arguments.changed_from:
        changed, error = _changed_paths(root, arguments.changed_from)
        if error is not None:
            print(error, file=sys.stderr)
            return 125
        print("Living docs to review:")
        for document in living_docs.mapped_docs(root, changed or []):
            print(document)

    return 1 if findings else 0


if __name__ == "__main__":
    sys.exit(main())
