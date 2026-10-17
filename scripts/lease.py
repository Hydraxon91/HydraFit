#!/usr/bin/env python3
"""Cooperative resource lease built on an atomic mkdir lock.

Usage:
  python3 scripts/lease.py acquire --dir <path> --name <resource> --owner <id> [--wait <seconds>]
  python3 scripts/lease.py status  --dir <path> --name <resource>
  python3 scripts/lease.py release --dir <path> --name <resource> --owner <id>

A lease is the directory `<dir>/<name>.lock.d`. Acquire creates it atomically
with `mkdir` and records `owner.json` (`owner`, `acquired_at`, `pid`). The lease
is never stolen, force-broken or auto-reclaimed: an unreadable holder record is
reported as uncertain and left untouched for a human to resolve. Liveness is not
checked by design: status reports a readable holder record, not a running process.

Exit codes:
    0    acquired, free, or released
    2    contention, refused release, missing lease, or uncertain holder metadata
    3    status: held, with a readable holder record
    125  usage error, or an unexpected OSError
"""

from __future__ import annotations

import argparse
import json
import math
import os
import re
import sys
import time
from datetime import datetime, timezone
from pathlib import Path

OK = 0
REFUSED = 2
HELD = 3
USAGE = 125
NAME_RE = re.compile(r"^[A-Za-z0-9._-]+$")
RETRY_INTERVAL_SECONDS = 0.5
OWNER_FILE = "owner.json"
UNREADABLE_MESSAGE = "holder metadata unreadable; do not auto-reclaim"


class UsageError(Exception):
    """A command-line usage problem, reported with exit code 125."""


class _Parser(argparse.ArgumentParser):
    def error(self, message: str) -> None:  # pragma: no cover - trivial override
        raise UsageError(message)


def lease_path(directory: str, name: str) -> Path:
    if not NAME_RE.match(name) or name in (".", ".."):
        raise UsageError(f"invalid resource name {name!r}")
    return Path(directory) / f"{name}.lock.d"


def read_holder(path: Path) -> dict | None:
    """The holder record, or None when it is missing, corrupt or not an object."""
    try:
        data = json.loads((path / OWNER_FILE).read_text())
    except (OSError, ValueError):
        return None
    return data if isinstance(data, dict) else None


def describe_holder(holder: dict) -> str:
    return f"held by owner={holder.get('owner')!r} acquired_at={holder.get('acquired_at')!r}"


def write_owner(path: Path, owner: str) -> None:
    """Create owner.json and fsync it; remove the fresh lease if the write fails."""
    destination = path / OWNER_FILE
    try:
        with open(destination, "w") as handle:
            json.dump({"owner": owner, "acquired_at": datetime.now(timezone.utc).isoformat(), "pid": os.getpid()}, handle)
            handle.flush()
            os.fsync(handle.fileno())
    except OSError:
        try:
            destination.unlink()
        except OSError:
            pass
        try:
            os.rmdir(path)
        except OSError:
            pass
        raise


def do_acquire(args: argparse.Namespace) -> int:
    path = lease_path(args.directory, args.name)
    if not math.isfinite(args.wait):
        raise UsageError("--wait must be finite")
    if args.wait < 0:
        raise UsageError("--wait must not be negative")
    try:
        os.makedirs(args.directory, exist_ok=True)
    except OSError as error:
        print(f"lease: cannot create lease directory {args.directory!r}: {error}", file=sys.stderr)
        return USAGE
    deadline = time.monotonic() + args.wait
    while True:
        try:
            os.mkdir(path)
        except FileExistsError:
            if time.monotonic() >= deadline:
                holder = read_holder(path)
                if holder is None:
                    print(f"{path}: {UNREADABLE_MESSAGE}")
                    return REFUSED
                print(f"{path}: busy, {describe_holder(holder)}")
                return REFUSED
            time.sleep(min(RETRY_INTERVAL_SECONDS, max(0.0, deadline - time.monotonic())))
            continue
        try:
            write_owner(path, args.owner)
        except OSError as error:
            print(f"lease: cannot record holder in {path}: {error}", file=sys.stderr)
            return USAGE
        print(f"{path}: acquired by {args.owner}")
        return OK


def do_status(args: argparse.Namespace) -> int:
    path = lease_path(args.directory, args.name)
    if not path.is_dir():
        print(f"{path}: free")
        return OK
    holder = read_holder(path)
    if holder is None:
        print(f"{path}: {UNREADABLE_MESSAGE}; status uncertain")
        return REFUSED
    print(f"{path}: {describe_holder(holder)}")
    return HELD


def do_release(args: argparse.Namespace) -> int:
    path = lease_path(args.directory, args.name)
    if not path.is_dir():
        print(f"{path}: no such lease")
        return REFUSED
    holder = read_holder(path)
    if holder is None:
        print(f"{path}: {UNREADABLE_MESSAGE}; refusing to release")
        return REFUSED
    if holder.get("owner") != args.owner:
        print(f"{path}: {describe_holder(holder)}; refusing release by {args.owner!r}")
        return REFUSED
    try:
        (path / OWNER_FILE).unlink()
    except FileNotFoundError:
        pass
    os.rmdir(path)
    print(f"{path}: released by {args.owner}")
    return OK


def build_parser() -> _Parser:
    parser = _Parser(prog="lease.py", description="Cooperative mkdir-based resource lease.")
    subparsers = parser.add_subparsers(dest="command", required=True)

    acquire = subparsers.add_parser("acquire", help="create the lease if it is free")
    acquire.add_argument("--dir", dest="directory", required=True)
    acquire.add_argument("--name", required=True)
    acquire.add_argument("--owner", required=True)
    acquire.add_argument("--wait", type=float, default=0.0)
    acquire.set_defaults(handler=do_acquire)

    status = subparsers.add_parser("status", help="report whether the lease is free or held")
    status.add_argument("--dir", dest="directory", required=True)
    status.add_argument("--name", required=True)
    status.set_defaults(handler=do_status)

    release = subparsers.add_parser("release", help="remove a lease held by --owner")
    release.add_argument("--dir", dest="directory", required=True)
    release.add_argument("--name", required=True)
    release.add_argument("--owner", required=True)
    release.set_defaults(handler=do_release)
    return parser


def main(argv: list[str] | None = None) -> int:
    parser = build_parser()
    try:
        args = parser.parse_args(sys.argv[1:] if argv is None else argv)
    except UsageError as error:
        print(f"lease: {error}", file=sys.stderr)
        return USAGE
    try:
        return args.handler(args)
    except UsageError as error:
        print(f"lease: {error}", file=sys.stderr)
        return USAGE
    except OSError as error:
        print(f"lease: {error}", file=sys.stderr)
        return USAGE


if __name__ == "__main__":
    sys.exit(main())
