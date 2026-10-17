#!/usr/bin/env python3
"""JSON-stdin bridge for the opt-in model-tool guard; never executes task commands.

Reuses task-state.py's validation, fingerprinting, locking and atomic writes. The
caller supplies a trusted binding, not an approval claim. Actions: inspect, begin,
complete. Errors return JSON with exit 2 (blocked) or 125 (invalid/I/O). A failed
completion write may have committed: inspect before any retry.
"""

from __future__ import annotations

import importlib.util
import json
from pathlib import Path
import sys
import time

sys.dont_write_bytecode = True
SPEC = importlib.util.spec_from_file_location("guard_task_state", Path(__file__).with_name("task-state.py"))
state = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = state
SPEC.loader.exec_module(state)


def reject_constant(value):
    raise state.Invalid(f"invalid number: {value}")


def inspect_request(request):
    if not isinstance(request, dict) or request.get("action") not in ("inspect", "begin", "complete"):
        raise state.Invalid("unknown guard request")
    for key in ("stateDir", "repo", "task"):
        if not state.text(request.get(key)):
            raise state.Invalid(f"missing {key}")
    if not state.ID_RE.fullmatch(request["task"]):
        raise state.Invalid("invalid task id")
    directory = Path(request["stateDir"])
    repo = Path(request["repo"])
    if not directory.is_absolute() or not repo.is_absolute():
        raise state.Invalid("guard bindings require absolute paths")
    directory, repo = directory.resolve(), repo.resolve()
    path = directory / f"{request['task']}.json"
    if path.is_symlink():
        raise state.Invalid("task record must not be a symlink")
    record = state.read_json(path)
    state.validate_record(record)
    if str(repo) != record["baseline"]["repo"]:
        raise state.Conflict("bound repository differs from task record")
    current = state.snapshot(repo)
    for boundary in (repo, Path(current["common_dir"])):
        if directory == boundary or directory.is_relative_to(boundary):
            raise state.Invalid("state directory must be external")
    report = state.reconcile(record, current, time.time())
    if not report["identity_matches"] or report["drift"] or report["outside_allowed_paths"]:
        raise state.Conflict("repository reconciliation required")
    operation_id = request.get("operation")
    if operation_id is not None and (not isinstance(operation_id, str) or not state.ID_RE.fullmatch(operation_id)):
        raise state.Invalid("invalid operation id")
    pending = report["unknown_operations"]
    # Only the executor's in-memory admission token may name its active attempt.
    own_pending = bool(operation_id) and pending == [operation_id]
    if pending and not own_pending:
        raise state.Conflict("unknown operation blocks new work")
    if request["action"] != "complete":
        if record["phase"] in ("complete", "blocked") or report["remaining_seconds"] <= 0:
            raise state.Conflict("task stopped or budget expired")
    return directory, path, record, current, report, own_pending


def handle(request):
    directory, path, record, current, report, own_pending = inspect_request(request)
    if request["action"] == "inspect":
        return {"ok": True, "revision": record["revision"], "remaining_seconds": report["remaining_seconds"]}
    operation = request.get("operation")
    if not operation:
        raise state.Invalid("operation id required")
    with state.locked(directory, request["task"]):
        # Re-read under lock: a sibling process may have admitted work since inspection.
        directory, path, record, current, report, own_pending = inspect_request(request)
        if request["action"] == "begin":
            if not state.text(request.get("tool")):
                raise state.Invalid("tool name required")
            event = {"type": "begin", "id": operation, "description": f"guarded model tool: {request['tool']}"}
        else:
            if not own_pending:
                raise state.Conflict("completion does not match a pending operation")
            event = {"type": "resolve", "id": operation, "outcome": "completed",
                     "evidence": "registered executor returned successfully; tool completion only, not business acceptance"}
        updated = state.apply_event(record, event, current, time.time())
        state.atomic_write(path, updated)
    return {"ok": True, "revision": updated["revision"]}


def main():
    try:
        request = json.loads(sys.stdin.read(), object_pairs_hook=state.pairs,
                             parse_float=state.finite_float, parse_constant=reject_constant)
        result = handle(request)
        code = 0
    except state.Conflict as error:
        result, code = {"ok": False, "reason": str(error)}, 2
    except (ValueError, OSError, state.subprocess.TimeoutExpired) as error:
        result, code = {"ok": False, "reason": str(error)}, 125
    print(json.dumps(result))
    return code


if __name__ == "__main__":
    sys.exit(main())
