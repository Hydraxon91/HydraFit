#!/usr/bin/env python3
"""External task bookkeeping, not an executor or an authorization mechanism.

Usage: task-state.py --dir <external-directory> --task <id> <subcommand>
  init --repo <checkout> --contract <json-file> --budget-seconds <seconds>
  show
  resume
  boundary --expect <revision> [--require-evidence <description> ...]
  update --expect <revision> --event <json-file> [--event <json-file> ...]

Events: begin (id, description), resolve (id, outcome, evidence), phase (value),
evidence (description, outcome, reference), checkpoint (reason), amend
(approval_reference, note, add_allowed_paths, add_acceptance), extend
(approval_reference, reason, add_seconds). Contract fields:
objective, allowed_paths (exact repo-relative files), acceptance (string list),
owner, approval_reference. Other JSON fields are retained as contract context.
The contract object is never rewritten; an approved scope expansion is an appended
amend event whose paths join the effective allowed set. Amendments add scope only,
never budget, and they record which already-dirty paths they legitimized.
Budget extensions are appended separately; original budget and elapsed usage stay
intact. Approval references are audit context, never authenticated user consent.

Multiple --event files apply in the given order against one observed snapshot and one
timestamp, then a single atomic replacement writes the combined result. If any event
fails validation or conflicts, nothing is written. --expect checks the initial revision
only.

Atomic JSON replacement plus a nonblocking POSIX advisory lock serialize cooperating
writers. Locks release on process exit; the permanent lock file is never removed.
Pending operations survive crashes as unknown outcomes. Checkpoints acknowledge
observations, not approval or safety. No command stored in JSON is ever executed.
Fingerprints are drift detectors, not backups or proof of unchanged ignored data.
Exit: 0 success/ready report, 2 conflict/reconciliation needed, 125 invalid input/I/O.
"""

from __future__ import annotations

import argparse
import contextlib
import copy
import fcntl
import hashlib
import json
import math
import os
from pathlib import Path, PurePosixPath
import re
import stat
import subprocess
import sys
import tempfile
import time

VERSION = 1
PHASES = ("planned", "implementing", "verifying", "reviewing", "correcting", "ready", "complete", "blocked")
ID_RE = re.compile(r"[A-Za-z0-9][A-Za-z0-9._-]{0,99}\Z")


class Invalid(ValueError):
    """Invalid input or unsupported/corrupt stored state."""


class Conflict(Exception):
    """Concurrent change or unresolved task precondition."""


def text(value) -> bool:
    return isinstance(value, str) and bool(value.strip())


def number(value) -> bool:
    return type(value) in (int, float) and math.isfinite(value) and value >= 0


def pairs(items):
    result = {}
    for key, value in items:
        if key in result:
            raise Invalid(f"duplicate JSON key: {key}")
        result[key] = value
    return result


def finite_float(raw: str) -> float:
    value = float(raw)
    if not math.isfinite(value):
        raise Invalid("JSON numbers must be finite")
    return value


def read_json(path: Path) -> dict:
    try:
        result = json.loads(path.read_text(), object_pairs_hook=pairs, parse_float=finite_float,
                            parse_constant=lambda value: (_ for _ in ()).throw(Invalid(f"invalid number: {value}")))
    except (ValueError, UnicodeError) as error:
        raise Invalid(f"invalid JSON in {path}: {error}") from error
    if not isinstance(result, dict):
        raise Invalid("expected a JSON object")
    return result


def validate_exact_path(path) -> None:
    if (not text(path) or PurePosixPath(path).is_absolute() or
            any(part in ("", ".", "..", ".git") for part in path.split("/")) or
            any(char in path for char in "*?[]\\")):
        raise Invalid(f"invalid exact allowed path: {path!r}")


def temporary_roots() -> tuple[Path, ...]:
    """Platform temporary roots whose contents a cleanup may remove.

    A record that must outlive the session does not belong in one; a short-lived record may.
    """
    candidates = (tempfile.gettempdir(), "/tmp", "/var/tmp", "/private/tmp", "/private/var/tmp")
    roots = []
    for candidate in candidates:
        try:
            roots.append(Path(candidate).resolve())
        except OSError:
            continue
    return tuple(dict.fromkeys(roots))


def under_temporary_root(directory: Path) -> bool:
    """Whether the state directory sits somewhere a platform cleanup may remove.

    A warning, never a refusal: a deliberately short-lived record may live in a temporary
    directory, but a record meant to outlive the session must not, and a platform cleanup
    has evicted a live record before.
    """
    try:
        resolved = directory.resolve()
    except OSError:
        resolved = directory
    return any(resolved == root or resolved.is_relative_to(root) for root in temporary_roots())


def validate_contract(contract: dict) -> None:
    for key in ("objective", "owner", "approval_reference"):
        if not text(contract.get(key)):
            raise Invalid(f"contract requires {key}")
    acceptance = contract.get("acceptance")
    if not isinstance(acceptance, list) or not acceptance or not all(text(item) for item in acceptance):
        raise Invalid("contract requires a nonempty acceptance string list")
    paths = contract.get("allowed_paths")
    if not isinstance(paths, list) or not paths:
        raise Invalid("contract requires exact allowed_paths")
    for path in paths:
        validate_exact_path(path)


def effective_allowed_paths(record: dict) -> set:
    """Contract paths plus every approved amendment; the contract itself is never rewritten."""
    allowed = set(record["contract"]["allowed_paths"])
    for amendment in record.get("amendments", []):
        allowed.update(amendment["add_allowed_paths"])
    return allowed


def git(repo: Path, *args: str) -> bytes:
    env = dict(os.environ, GIT_OPTIONAL_LOCKS="0")
    result = subprocess.run(["git", "-C", str(repo), *args], capture_output=True,
                            timeout=15, env=env)
    if result.returncode:
        raise Invalid(f"Git inspection failed: {args[0]}")
    return result.stdout


def snapshot(repo: Path) -> dict:
    """Hash index/worktree changes and untracked bytes; never store diff contents."""
    root = Path(os.fsdecode(git(repo, "rev-parse", "--show-toplevel")).strip()).resolve()
    if repo.resolve() != root:
        raise Invalid("--repo must be the checkout root")
    head = git(repo, "rev-parse", "HEAD").decode().strip()
    common = Path(os.fsdecode(git(repo, "rev-parse", "--git-common-dir")).strip())
    common = (repo / common).resolve()
    status = git(repo, "status", "--porcelain=v1", "-z", "--untracked-files=all")
    digest = hashlib.sha256(status)
    for args in (("diff", "--binary", "--no-ext-diff", "--no-textconv"),
                 ("diff", "--cached", "--binary", "--no-ext-diff", "--no-textconv")):
        digest.update(git(repo, *args))
    paths = set()
    entries = iter(status.split(b"\0"))
    for entry in entries:
        if not entry:
            continue
        paths.add(os.fsdecode(entry[3:]))
        if b"R" in entry[:2] or b"C" in entry[:2]:
            paths.add(os.fsdecode(next(entries)))
    untracked = git(repo, "ls-files", "--others", "--exclude-standard", "-z")
    for raw in sorted(filter(None, untracked.split(b"\0"))):
        path = repo / os.fsdecode(raw)
        digest.update(raw + b"\0")
        info = path.lstat()
        digest.update(str(stat.S_IMODE(info.st_mode)).encode())
        if path.is_symlink():
            digest.update(os.fsencode(os.readlink(path)))
        elif stat.S_ISREG(info.st_mode):
            with path.open("rb") as handle:
                for chunk in iter(lambda: handle.read(65536), b""):
                    digest.update(chunk)
        else:
            raise Invalid(f"cannot fingerprint special untracked file: {os.fsdecode(raw)}")
    # A moving checkout is not a valid checkpoint. This is not a filesystem snapshot.
    if status != git(repo, "status", "--porcelain=v1", "-z", "--untracked-files=all") or head != git(repo, "rev-parse", "HEAD").decode().strip():
        raise Conflict("checkout changed during inspection; stop concurrent writers")
    return {"repo": str(root), "common_dir": str(common), "head": head,
            "dirty_sha256": digest.hexdigest(), "changed_paths": sorted(paths)}


def validate_snapshot(value) -> None:
    if not isinstance(value, dict) or not all(text(value.get(key)) for key in ("repo", "common_dir", "head", "dirty_sha256")):
        raise Invalid("invalid repository snapshot")
    if not isinstance(value.get("changed_paths"), list) or not all(text(path) for path in value["changed_paths"]):
        raise Invalid("invalid changed_paths")


def validate_record(record: dict) -> None:
    if type(record.get("version")) is not int or record["version"] != VERSION:
        raise Invalid("unsupported task-state version")
    if type(record.get("revision")) is not int or record["revision"] < 0:
        raise Invalid("invalid revision")
    if not isinstance(record.get("contract"), dict):
        raise Invalid("missing contract")
    validate_contract(record["contract"])
    for key in ("baseline", "expected"):
        validate_snapshot(record.get(key))
    if record["baseline"]["repo"] != record["expected"]["repo"] or record["baseline"]["common_dir"] != record["expected"]["common_dir"]:
        raise Invalid("repository identity changed in record")
    if record.get("phase") not in PHASES:
        raise Invalid("invalid phase")
    if not all(number(record.get(key)) for key in ("budget_seconds", "used_seconds", "accounted_at")) or not record["budget_seconds"]:
        raise Invalid("invalid budget accounting")
    if not isinstance(record.get("operations"), dict) or not isinstance(record.get("evidence"), list):
        raise Invalid("invalid operations/evidence")
    if not isinstance(record.get("history"), list):
        raise Invalid("invalid history")
    for key, operation in record["operations"].items():
        if not ID_RE.fullmatch(key) or not isinstance(operation, dict) or not text(operation.get("description")):
            raise Invalid("invalid operation")
        if operation.get("outcome") not in ("pending", "completed", "failed", "not-performed"):
            raise Invalid("invalid operation outcome")
        if operation["outcome"] != "pending" and not text(operation.get("evidence")):
            raise Invalid("resolved operation lacks evidence")
    for evidence in record["evidence"]:
        if (not isinstance(evidence, dict) or not text(evidence.get("description")) or
                not text(evidence.get("reference")) or evidence.get("outcome") not in
                ("passed", "failed", "unknown", "unverified")):
            raise Invalid("invalid evidence entry")
        validate_snapshot(evidence.get("snapshot"))
    if len(record["history"]) != record["revision"]:
        raise Invalid("history/revision mismatch")
    for revision, entry in enumerate(record["history"], start=1):
        if (not isinstance(entry, dict) or entry.get("revision") != revision or
                not isinstance(entry.get("event"), dict) or not number(entry.get("at"))):
            raise Invalid("invalid history entry")
    validate_operation_history(record)
    validate_amendments(record)
    validate_extensions(record)


def validate_operation_history(record: dict) -> None:
    """Detect lost/contradictory operation state; history is never a replay queue."""
    operations = {}
    for entry in record["history"]:
        event = entry["event"]
        kind = event.get("type")
        if kind == "begin":
            operation_id = event.get("id")
            if (not isinstance(operation_id, str) or not ID_RE.fullmatch(operation_id) or
                    not text(event.get("description"))):
                raise Invalid("invalid begin event in history")
            if operation_id in operations:
                raise Invalid("reused operation id in history")
            if any(operation["outcome"] == "pending" for operation in operations.values()):
                raise Invalid("begin recorded before previous unknown outcome was resolved")
            operations[operation_id] = {"description": event["description"], "outcome": "pending"}
        elif kind == "resolve":
            operation_id = event.get("id")
            if not isinstance(operation_id, str) or operation_id not in operations:
                raise Invalid("resolution without a begin event in history")
            operation = operations[operation_id]
            if (operation["outcome"] != "pending" or
                    event.get("outcome") not in ("completed", "failed", "not-performed") or
                    not text(event.get("evidence"))):
                raise Invalid("invalid resolution in history")
            operation.update(outcome=event["outcome"], evidence=event["evidence"])
        elif kind not in ("phase", "checkpoint", "evidence", "amend", "extend"):
            raise Invalid("unknown event type in history")
    if operations != record["operations"]:
        raise Invalid("operations/history mismatch; unknown outcomes cannot be discarded")


def validate_amendments(record: dict) -> None:
    """Amendments are append-only and must restate their recorded history event."""
    amendments = record.get("amendments", [])
    if not isinstance(amendments, list):
        raise Invalid("invalid amendments")
    entries = [entry for entry in record["history"] if entry["event"].get("type") == "amend"]
    if len(amendments) != len(entries):
        raise Invalid("amendments/history mismatch")
    previous = 0
    for amendment, entry in zip(amendments, entries):
        event = entry["event"]
        if not isinstance(amendment, dict):
            raise Invalid("invalid amendment")
        if amendment.get("revision") != entry["revision"] or entry["revision"] <= previous:
            raise Invalid("amendment revision mismatch")
        previous = entry["revision"]
        if not text(amendment.get("approval_reference")) or not text(amendment.get("note")):
            raise Invalid("amendment lacks an approval reference or note")
        if not number(amendment.get("at")) or amendment["at"] != entry["at"]:
            raise Invalid("amendment timestamp does not match the recorded event")
        for key in ("add_allowed_paths", "already_allowed_paths", "add_acceptance"):
            value = amendment.get(key)
            if not isinstance(value, list) or not all(text(item) for item in value):
                raise Invalid(f"invalid amendment {key}")
        covered = amendment.get("covered_dirty_paths")
        if not isinstance(covered, list) or not all(text(path) for path in covered):
            raise Invalid("invalid amendment covered_dirty_paths")
        for path in amendment["add_allowed_paths"] + amendment["already_allowed_paths"]:
            validate_exact_path(path)
        if not amendment["add_allowed_paths"] and not amendment["add_acceptance"]:
            raise Invalid("amendment adds neither a path nor an acceptance item")
        # The stored split must restate exactly what the recorded event requested,
        # so a hand-edited amendment cannot widen the effective set on its own.
        requested = event.get("add_allowed_paths", [])
        if not isinstance(requested, list) or not all(text(path) for path in requested):
            raise Invalid("invalid amend event in history")
        added = set(amendment["add_allowed_paths"])
        redundant = set(amendment["already_allowed_paths"])
        if added | redundant != set(requested) or added & redundant:
            raise Invalid("amendment paths do not match the recorded event")
        acceptance = event.get("add_acceptance", [])
        if not isinstance(acceptance, list) or not all(text(item) for item in acceptance):
            raise Invalid("invalid amend event in history")
        if amendment["add_acceptance"] != list(acceptance):
            raise Invalid("amendment acceptance does not match the recorded event")
        if (amendment["approval_reference"] != event.get("approval_reference")
                or amendment["note"] != event.get("note")):
            raise Invalid("amendment text does not match the recorded event")
        if not set(covered) <= added:
            raise Invalid("amendment covered paths must be among the added paths")


def extension_fields(event: dict) -> dict:
    seconds = event.get("add_seconds")
    if not number(seconds) or seconds <= 0:
        raise Invalid("extend requires positive finite add_seconds")
    if not text(event.get("approval_reference")) or not text(event.get("reason")):
        raise Invalid("extend requires an approval reference and reason")
    return {key: event[key] for key in ("add_seconds", "approval_reference", "reason")}


def effective_budget_seconds(record: dict) -> float:
    total = record["budget_seconds"]
    for extension in record.get("extensions", []):
        total += extension["add_seconds"]
        if not number(total):
            raise Invalid("effective budget must be finite")
    return total


def validate_extensions(record: dict) -> None:
    """Validate both representations; legacy v1 records without extensions stay readable."""
    extensions = record.get("extensions", [])
    if not isinstance(extensions, list):
        raise Invalid("invalid extensions")
    expected = []
    phase = "planned"
    for entry in record["history"]:
        event = entry["event"]
        if event.get("type") == "phase":
            phase = event.get("value")
        elif event.get("type") == "extend":
            if phase in ("complete", "blocked"):
                raise Invalid("extension recorded for a terminal task")
            expected.append({"revision": entry["revision"], "at": entry["at"],
                             **extension_fields(event)})
    if extensions != expected:
        raise Invalid("extensions/history mismatch")
    effective_budget_seconds(record)


def used_seconds(record: dict, now: float) -> float:
    return record["used_seconds"] + max(0, now - record["accounted_at"])


def reconcile_blockers(report: dict, *, budget: bool = True, drift: bool = True,
                       scope: bool = True) -> list[str]:
    """Derive human-readable blockers from a reconcile report.

    Budget, drift and scope are selectable because they are not blockers for every
    action: a checkpoint acknowledges drift, an amendment may legitimize an
    out-of-scope path, and completion may be bookkeeping after expiry.
    """
    blockers = []
    if not report["identity_matches"]:
        blockers.append("repository identity mismatch")
    if drift and report["drift"]:
        blockers.append("repository snapshot drift")
    if report["unknown_operations"]:
        blockers.append("pending operation(s): " + ", ".join(report["unknown_operations"]))
    if scope and report["outside_allowed_paths"]:
        blockers.append("out-of-scope path(s): " + ", ".join(report["outside_allowed_paths"]))
    if budget and report["remaining_seconds"] <= 0:
        blockers.append("budget exhausted")
    return blockers


def reconcile(record: dict, current: dict, now: float) -> dict:
    pending = [key for key, value in record["operations"].items() if value["outcome"] == "pending"]
    identity = all(current[key] == record["expected"][key] for key in ("repo", "common_dir"))
    drift = current != record["expected"]
    budget = effective_budget_seconds(record)
    remaining = max(0, budget - used_seconds(record, now))
    allowed = effective_allowed_paths(record)
    # Baseline dirt is preserved context, not authority to modify it.
    outside = sorted(set(current["changed_paths"]) - set(record["baseline"]["changed_paths"]) - allowed)
    report = {"revision": record["revision"], "phase": record["phase"],
            "identity_matches": identity, "drift": drift,
            "unknown_operations": pending, "outside_allowed_paths": outside,
            "amended_paths": sorted(allowed - set(record["contract"]["allowed_paths"])),
             "original_budget_seconds": record["budget_seconds"],
             "effective_budget_seconds": budget, "remaining_seconds": remaining,
            "reconciliation_needed": drift or bool(pending) or bool(outside) or remaining <= 0,
             "authorization": "recorded approval is audit context only; verify actual user authorization"}
    # Additive detail: blockers includes the budget stop; completion_blockers omits it
    # because marking an otherwise-clean record complete is bookkeeping, not new work.
    report["blockers"] = reconcile_blockers(report)
    report["completion_blockers"] = reconcile_blockers(report, budget=False)
    return report


def boundary_report(record: dict, current: dict, now: float, expected: int, required: list[str]) -> dict:
    """Read-only cooperative gate. Evidence labels are not a review or an approval."""
    report = reconcile(record, current, now)
    missing = []
    for description in required:
        matches = [item for item in record["evidence"] if item["description"] == description]
        # A later failure/unknown supersedes an earlier pass. Pin evidence to this tree.
        if not matches or matches[-1]["outcome"] != "passed" or matches[-1]["snapshot"] != current:
            missing.append(description)
    report.update(stale_revision=expected != record["revision"], missing_evidence=missing,
                  terminal_phase=record["phase"] in ("blocked", "complete"))
    report["boundary_clear"] = not (report["reconciliation_needed"] or report["stale_revision"] or
                                     missing or report["terminal_phase"])
    return report


@contextlib.contextmanager
def locked(directory: Path, task: str):
    """A cooperative writer lock; never unlink it (doing so splits lock identity)."""
    fd = os.open(directory / f"{task}.lock", os.O_CREAT | os.O_RDWR | os.O_NOFOLLOW, 0o600)
    try:
        try:
            fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError as error:
            raise Conflict("task record has an active writer") from error
        yield
    finally:
        os.close(fd)


def atomic_write(path: Path, record: dict) -> None:
    """An interrupted replacement leaves either the old or new complete record."""
    validate_record(record)
    fd, name = tempfile.mkstemp(prefix=f".{path.name}.", dir=path.parent)
    temporary = Path(name)
    try:
        with os.fdopen(fd, "w") as handle:
            json.dump(record, handle, indent=2, allow_nan=False)
            handle.write("\n")
            handle.flush()
            os.fsync(handle.fileno())
        os.replace(temporary, path)
        directory_fd = os.open(path.parent, os.O_RDONLY)
        try:
            os.fsync(directory_fd)
        finally:
            os.close(directory_fd)
    finally:
        temporary.unlink(missing_ok=True)


def apply_event(record: dict, event: dict, current: dict, now: float) -> dict:
    result = copy.deepcopy(record)
    report = reconcile(record, current, now)
    kind = event.get("type")
    if kind == "begin":
        if record["phase"] in ("complete", "blocked"):
            raise Conflict("cannot begin: task is complete or blocked")
        if report["reconciliation_needed"]:
            raise Conflict("cannot begin: " + "; ".join(reconcile_blockers(report)))
        operation_id = event.get("id")
        if not isinstance(operation_id, str) or not ID_RE.fullmatch(operation_id) or not text(event.get("description")):
            raise Invalid("begin requires a unique id and description")
        if operation_id in result["operations"]:
            raise Conflict("operation id already recorded; do not replay")
        result["operations"][operation_id] = {"description": event["description"], "outcome": "pending"}
    elif kind == "resolve":
        if not text(event.get("id")):
            raise Invalid("resolve requires an operation id")
        operation = result["operations"].get(event["id"])
        if operation is None or operation["outcome"] != "pending":
            raise Conflict("only a pending operation can be resolved")
        if event.get("outcome") not in ("completed", "failed", "not-performed") or not text(event.get("evidence")):
            raise Invalid("resolve requires observed outcome and evidence, never a guessed failure")
        operation.update(outcome=event["outcome"], evidence=event["evidence"])
    elif kind == "checkpoint":
        if not text(event.get("reason")):
            raise Invalid("checkpoint requires a reconciliation reason")
        blockers = reconcile_blockers(report, budget=False, drift=False)
        if blockers:
            raise Conflict("cannot checkpoint: " + "; ".join(blockers))
        result["expected"] = current
    elif kind == "amend":
        if not text(event.get("approval_reference")) or not text(event.get("note")):
            raise Invalid("amend requires an approval reference and a note")
        blockers = reconcile_blockers(report, budget=False, drift=False, scope=False)
        if blockers:
            raise Conflict("cannot amend: " + "; ".join(blockers))
        if record["phase"] in ("complete", "blocked"):
            raise Conflict("a terminal record cannot admit more work")
        requested = event.get("add_allowed_paths", [])
        if not isinstance(requested, list) or not all(text(path) for path in requested):
            raise Invalid("amend requires a list of exact allowed paths")
        for path in requested:
            validate_exact_path(path)
        acceptance = event.get("add_acceptance", [])
        if not isinstance(acceptance, list) or not all(text(item) for item in acceptance):
            raise Invalid("amend acceptance additions must be nonempty strings")
        allowed = effective_allowed_paths(record)
        added = sorted(set(requested) - allowed)
        if not added and not acceptance:
            raise Invalid("amend adds no new allowed path or acceptance item")
        # Record what this amendment legitimizes that was already dirty, so an
        # amendment written after the fact stays visible in the record.
        covered = sorted(set(added) & set(current["changed_paths"]))
        result.setdefault("amendments", []).append(
            {"revision": record["revision"] + 1, "at": now,
             "approval_reference": event["approval_reference"], "note": event["note"],
             "add_allowed_paths": added, "add_acceptance": list(acceptance),
             "already_allowed_paths": sorted(set(requested) & allowed),
             "covered_dirty_paths": covered})
    elif kind == "extend":
        fields = extension_fields(event)
        if not report["identity_matches"]:
            raise Conflict("cannot extend against a different repository")
        if record["phase"] in ("complete", "blocked"):
            raise Conflict("a terminal record cannot be extended")
        # Recording more approved time is not reconciliation: pending operations,
        # source drift, scope and expected snapshots all remain untouched.
        result.setdefault("extensions", []).append(
            {"revision": record["revision"] + 1, "at": now, **fields})
    elif kind == "phase":
        value = event.get("value")
        if value not in PHASES:
            raise Invalid("invalid phase")
        if value == "blocked":
            result["phase"] = value
        elif value == "complete":
            # Completion is bookkeeping; an expired budget alone does not block it.
            # Identity, drift, pending outcomes and out-of-scope paths still do.
            blockers = reconcile_blockers(report, budget=False)
            if blockers:
                raise Conflict("cannot complete: " + "; ".join(blockers))
            result["phase"] = value
        else:
            blockers = reconcile_blockers(report)
            if blockers:
                raise Conflict(f"cannot advance to {value}: " + "; ".join(blockers))
            result["phase"] = value
    elif kind == "evidence":
        if not text(event.get("description")) or not text(event.get("reference")) or event.get("outcome") not in ("passed", "failed", "unknown", "unverified"):
            raise Invalid("evidence requires description, reference and explicit outcome")
        result["evidence"].append({**event, "snapshot": current})
    else:
        raise Invalid("unknown event type")
    result["used_seconds"] = used_seconds(record, now)
    # A backward wall clock can never refill the budget on the next update.
    result["accounted_at"] = max(record["accounted_at"], now)
    result["revision"] += 1
    result["history"].append({"revision": result["revision"], "event": event, "at": now})
    validate_record(result)
    return result


class Parser(argparse.ArgumentParser):
    def error(self, message):
        raise Invalid(message)


def main(argv: list[str] | None = None) -> int:
    parser = Parser(description=__doc__)
    parser.add_argument("--dir", type=Path, required=True)
    parser.add_argument("--task", required=True)
    commands = parser.add_subparsers(dest="command", required=True)
    init = commands.add_parser("init")
    init.add_argument("--repo", type=Path, required=True)
    init.add_argument("--contract", type=Path, required=True)
    init.add_argument("--budget-seconds", type=float, required=True)
    commands.add_parser("show")
    commands.add_parser("resume")
    boundary = commands.add_parser("boundary")
    boundary.add_argument("--expect", type=int, required=True)
    boundary.add_argument("--require-evidence", action="append", default=[])
    update = commands.add_parser("update")
    update.add_argument("--expect", type=int, required=True)
    update.add_argument("--event", type=Path, action="append", required=True)
    try:
        args = parser.parse_args(argv)
        if not ID_RE.fullmatch(args.task):
            raise Invalid("invalid task id")
        directory = args.dir.resolve()
        if args.command == "init":
            current = snapshot(args.repo.resolve())
        else:
            path = directory / f"{args.task}.json"
            if path.is_symlink():
                raise Invalid("task record must not be a symlink")
            record = read_json(path)
            validate_record(record)
            current = snapshot(Path(record["baseline"]["repo"]))
        for boundary in (Path(current["repo"]), Path(current["common_dir"])):
            if directory == boundary or directory.is_relative_to(boundary):
                raise Invalid("state directory must be outside the checkout and Git directory")
        path = directory / f"{args.task}.json"
        if args.command == "show":
            print(json.dumps(record, indent=2))
            return 0
        if args.command == "resume":
            report = reconcile(record, current, time.time())
            # Informational only: a temporary location is a risk to report, not a reason to block.
            report["state_directory_temporary"] = under_temporary_root(directory)
            print(json.dumps(report, indent=2))
            return 2 if report["reconciliation_needed"] else 0
        if args.command == "boundary":
            if any(not text(item) for item in args.require_evidence):
                raise Invalid("required evidence descriptions must be nonempty")
            report = boundary_report(record, current, time.time(), args.expect, args.require_evidence)
            report["state_directory_temporary"] = under_temporary_root(directory)
            print(json.dumps(report, indent=2))
            return 0 if report["boundary_clear"] else 2
        if args.command == "init" and under_temporary_root(directory):
            print(f"task-state: warning: the state directory {directory} is under a temporary root; "
                  f"a platform cleanup can evict this record before the task finishes",
                  file=sys.stderr)
        directory.mkdir(parents=True, exist_ok=True, mode=0o700)
        with locked(directory, args.task):
            if path.is_symlink():
                raise Invalid("task record must not be a symlink")
            now = time.time()
            if args.command == "init":
                if path.exists():
                    raise Conflict("task already exists")
                if not number(args.budget_seconds) or args.budget_seconds <= 0:
                    raise Invalid("budget must be positive and finite")
                contract = read_json(args.contract)
                validate_contract(contract)
                record = {"version": VERSION, "revision": 0, "contract": contract,
                          "baseline": current, "expected": current, "phase": "planned",
                          "budget_seconds": args.budget_seconds, "used_seconds": 0,
                          "accounted_at": now, "operations": {}, "evidence": [],
                          "amendments": [], "history": []}
            else:
                record = read_json(path)
                validate_record(record)
                if record["revision"] != args.expect:
                    raise Conflict("stale revision; reload instead of overwriting")
                if current["repo"] != record["baseline"]["repo"]:
                    raise Conflict("repository identity changed")
                # Ordered events share one observed snapshot and one timestamp; a
                # failure aborts before the single atomic write below.
                for event_path in args.event:
                    record = apply_event(record, read_json(event_path), current, now)
            atomic_write(path, record)
        print(json.dumps({"task": args.task, "revision": record["revision"], "phase": record["phase"]}))
        return 0
    except Conflict as error:
        print(f"task-state: {error}", file=sys.stderr)
        return 2
    except (Invalid, OSError, subprocess.TimeoutExpired) as error:
        print(f"task-state: {error}", file=sys.stderr)
        return 125


if __name__ == "__main__":
    sys.exit(main())
