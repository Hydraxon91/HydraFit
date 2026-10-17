#!/usr/bin/env python3
"""Read-only prepared-journey declaration checks, not authorization or YAML analysis.

Usage: ui-journey-preflight.py CONTRACT --max-age-seconds SECONDS
Paths in artifacts/flows are absolute. Exit 0 clear, 2 findings, 125 invalid input.
"""
import argparse
import hashlib
import json
import math
from pathlib import Path
import re
import sys
import time


def nonempty(value):
    return isinstance(value, str) and bool(value.strip())


def positive(value):
    return type(value) in (int, float) and math.isfinite(value) and value > 0


def artifact(value):
    if not isinstance(value, dict) or not nonempty(value.get("path")):
        return False
    path = Path(value["path"])
    return (path.is_absolute() and path.is_file() and
            value.get("sha256") == hashlib.sha256(path.read_bytes()).hexdigest())


def same_fact(left, right):
    """JSON booleans are not numbers; numeric int/float representations may agree."""
    if type(left) in (int, float) and type(right) in (int, float):
        return left == right
    if type(left) is not type(right):
        return False
    if isinstance(left, dict):
        return left.keys() == right.keys() and all(same_fact(left[k], right[k]) for k in left)
    if isinstance(left, list):
        return len(left) == len(right) and all(same_fact(a, b) for a, b in zip(left, right))
    return left == right


def findings(contract, max_age, now):
    errors = []
    if not isinstance(contract, dict):
        return ["contract must be an object"]
    if type(contract.get("version")) is not int or contract["version"] != 1:
        errors.append("version must be 1")
    if not re.fullmatch(r"emulator-[0-9]+", str(contract.get("device", ""))):
        errors.append("device must be an explicit emulator serial")
    for field in ("build", "starting_state", "approval_reference", "allowed_interactions",
                  "prior_writes", "safeguard", "cleanup", "restoration", "screenshot_checkpoints"):
        if not nonempty(contract.get(field)):
            errors.append(f"{field}: nonempty declaration required (use explicit 'none' when applicable)")
    baseline = contract.get("baseline")
    if not isinstance(baseline, dict):
        errors.append("baseline: object required")
    else:
        observed = baseline.get("observed_at")
        if not positive(observed) or not 0 <= now - observed <= max_age:
            errors.append("baseline: stale or invalid/future observation time")
        if not artifact(baseline.get("artifact")):
            errors.append("baseline: missing or changed evidence artifact")
        if not isinstance(baseline.get("facts"), dict) or not baseline["facts"]:
            errors.append("baseline: nonempty fixture facts required")
        expected = contract.get("fixture_expectations")
        if not isinstance(expected, dict) or not expected:
            errors.append("fixture_expectations: nonempty expected facts required")
        elif not isinstance(baseline.get("facts"), dict) or any(
                key not in baseline["facts"] or not same_fact(baseline["facts"][key], value)
                for key, value in expected.items()):
            errors.append("fixture_expectations: assertions contradict observed baseline facts")
    budgets = contract.get("budgets")
    if not isinstance(budgets, dict) or not all(positive(budgets.get(k)) for k in ("exploration", "execution")):
        errors.append("budgets: positive exploration and execution seconds required")
    flows = contract.get("flows")
    if not isinstance(flows, list) or not flows or not all(artifact(item) for item in flows):
        errors.append("flows: nonempty list of matching absolute-path SHA256 artifacts required")
    writes = contract.get("planned_writes")
    if not isinstance(writes, list) or not all(nonempty(item) for item in writes):
        errors.append("planned_writes: string list required (empty means no writes)")
    unknown = contract.get("unknown_writes")
    if not isinstance(unknown, list) or not all(nonempty(item) for item in unknown):
        errors.append("unknown_writes: string list required")
    elif unknown and writes:
        errors.append("unknown_writes: resolve by inspection before any planned write")
    assertions = contract.get("assertions")
    if not isinstance(assertions, list) or not assertions:
        errors.append("assertions: nonempty durable assertions required")
    else:
        for index, item in enumerate(assertions):
            if (not isinstance(item, dict) or item.get("kind") != "durable" or
                    not all(nonempty(item.get(k)) for k in ("expected", "downstream_check"))):
                errors.append(f"assertions[{index}]: durable expected state and downstream_check required")
    categories = contract.get("destructive_categories")
    if not isinstance(categories, list):
        errors.append("destructive_categories: explicit list required (empty for unrelated journeys)")
    else:
        names = set()
        for index, item in enumerate(categories):
            prefix = f"destructive_categories[{index}]"
            if not isinstance(item, dict) or not nonempty(item.get("name")):
                errors.append(f"{prefix}: name required")
                continue
            if item["name"] in names:
                errors.append(f"{prefix}: duplicate category")
            names.add(item["name"])
            mode = item.get("coverage")
            if mode not in ("execution-planned", "gating-only", "unverified"):
                errors.append(f"{prefix}: invalid coverage (planned is not executed)")
            if not nonempty(item.get("reason")):
                errors.append(f"{prefix}: reason required")
            if mode == "execution-planned":
                if not isinstance(writes, list) or item.get("write") not in writes:
                    errors.append(f"{prefix}: execution must name an allowed planned write")
                for field in ("authorization_reference", "safeguard", "recovery", "postcondition"):
                    if not nonempty(item.get(field)) or item[field].strip().lower() == "none":
                        errors.append(f"{prefix}: {field} required for destructive execution")
    return errors


def unique_pairs(items):
    result = {}
    for key, value in items:
        if key in result:
            raise ValueError(f"duplicate key: {key}")
        result[key] = value
    return result


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("contract", type=Path)
    parser.add_argument("--max-age-seconds", type=float, required=True)
    args = parser.parse_args(argv)
    try:
        if not positive(args.max_age_seconds):
            raise ValueError("max age must be positive and finite")
        contract = json.loads(args.contract.read_text(), object_pairs_hook=unique_pairs,
                              parse_constant=lambda value: (_ for _ in ()).throw(ValueError(value)))
        errors = findings(contract, args.max_age_seconds, time.time())
        print(json.dumps({"version": 1, "preflight_clear": not errors, "findings": errors,
                          "coverage": "declarations only; no device state, YAML or approval verified"}))
        return 2 if errors else 0
    except (OSError, ValueError, UnicodeError) as error:
        print(f"invalid preflight input: {error}", file=sys.stderr)
        return 125


if __name__ == "__main__":
    sys.exit(main())
