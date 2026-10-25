#!/usr/bin/env python3
"""Read-only prepared-journey declaration checks, not authorization or YAML analysis.

Usage: ui-journey-preflight.py CONTRACT --max-age-seconds SECONDS [--brief]
Paths in artifacts/flows are absolute. Exit 0 clear, 2 findings, 125 invalid input.
--brief prints a stable Markdown declaration brief instead of JSON: fixed field
format and order for fixed inputs and time, with baseline age varying with the clock.
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


SCREENSHOT_COMMAND = re.compile(
    r"^\s*(?:-\s*)?(?:takeScreenshot|assertScreenshot)\s*:\s*(.*?)\s*$")
INLINE_SCREENSHOT_PATH = re.compile(
    r"^\{\s*path\s*:\s*(?:\"([^\"]*)\"|'([^']*)'|([^}\s,]+))\s*\}(?:\s+#.*)?\s*$")


def screenshot_name(value):
    """Parse the declaration value, not full YAML: scalar or inline { path: name }."""
    if value.startswith("{"):
        match = INLINE_SCREENSHOT_PATH.match(value)
        if not match:
            return None
        return next(item for item in match.groups() if item is not None).strip()
    if value[:1] in ("'", '"'):
        quote = value[0]
        end = value.find(quote, 1)
        return value[1:end].strip() if end > 0 else None
    return value.split()[0] if value else None


def screenshot_names(text):
    """Collect screenshot names from takeScreenshot/assertScreenshot command lines."""
    names = set()
    for line in text.splitlines():
        match = SCREENSHOT_COMMAND.match(line)
        if not match:
            continue
        name = screenshot_name(match.group(1))
        if name:
            names.add(name)
    return names


def check_screenshot_evidence(contract, flow_artifacts, errors):
    evidence = contract.get("screenshot_evidence")
    if evidence == "none":
        checkpoints = contract.get("screenshot_checkpoints")
        if not nonempty(checkpoints) or checkpoints.strip().lower() != "none":
            errors.append("screenshot_evidence: 'none' requires screenshot_checkpoints 'none'")
        return
    if not isinstance(evidence, list) or not evidence:
        errors.append("screenshot_evidence: 'none' or a nonempty list of unique names required")
        return
    names = []
    for index, name in enumerate(evidence):
        if not nonempty(name):
            errors.append(f"screenshot_evidence[{index}]: nonempty screenshot name required")
        elif name in names:
            errors.append(f"screenshot_evidence[{index}]: duplicate screenshot name")
        else:
            names.append(name)
    if flow_artifacts is None:
        return
    available = set()
    for item in flow_artifacts:
        try:
            available |= screenshot_names(Path(item["path"]).read_text())
        except (OSError, UnicodeError) as error:
            errors.append(f"flows: cannot read {item['path']}: {error}")
    for name in names:
        if name not in available:
            errors.append(f"screenshot_evidence: {name} is not taken or asserted by a referenced flow")


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
    version = contract.get("version")
    if type(version) is not int or version not in (1, 2):
        errors.append("version must be 1 or 2")
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
    flows_valid = isinstance(flows, list) and bool(flows) and all(artifact(item) for item in flows)
    if not flows_valid:
        errors.append("flows: nonempty list of matching absolute-path SHA256 artifacts required")
    if version == 2:
        check_screenshot_evidence(contract, flows if flows_valid else None, errors)
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


def display(value):
    if value is None:
        return "none"
    if isinstance(value, str):
        return value
    return json.dumps(value, sort_keys=True, ensure_ascii=False)


def brief(contract, now, errors):
    """Stable declarations-only Markdown: fixed format/order for fixed inputs and time.

    No writes; the reported baseline age is the only time-dependent field.
    """
    contract = contract if isinstance(contract, dict) else {}
    lines = [
        "# Prepared-journey preflight brief",
        "",
        f"- Status: {'clear' if not errors else f'findings ({len(errors)})'}",
        f"- Contract version: {display(contract.get('version'))}",
        f"- Device: {display(contract.get('device'))}",
        f"- Build: {display(contract.get('build'))}",
        f"- Starting state: {display(contract.get('starting_state'))}",
        f"- Approval reference: {display(contract.get('approval_reference'))}",
        f"- Allowed interactions: {display(contract.get('allowed_interactions'))}",
        f"- Prior writes: {display(contract.get('prior_writes'))}",
        f"- Planned writes: {display(contract.get('planned_writes'))}",
        f"- Unknown writes: {display(contract.get('unknown_writes'))}",
        f"- Safeguard: {display(contract.get('safeguard'))}",
        f"- Cleanup: {display(contract.get('cleanup'))}",
        f"- Restoration: {display(contract.get('restoration'))}",
        f"- Budgets: {display(contract.get('budgets'))}",
    ]
    baseline = contract.get("baseline")
    observed = baseline.get("observed_at") if isinstance(baseline, dict) else None
    age = f"{now - observed:g}s" if positive(observed) else "unknown"
    lines.append(f"- Baseline observed at: {display(observed)} (age {age})")
    lines.append("- Flows:")
    flows = contract.get("flows")
    if isinstance(flows, list) and flows:
        for item in flows:
            if isinstance(item, dict) and nonempty(item.get("path")):
                sha = str(item.get("sha256", ""))[:12] or "unknown"
                lines.append(f"  - {item['path']} (sha256 {sha})")
            else:
                lines.append(f"  - {display(item)}")
    else:
        lines.append("  - none")
    lines.append(f"- Fixture expectations: {display(contract.get('fixture_expectations'))}")
    lines.append("- Durable assertions:")
    assertions = contract.get("assertions")
    if isinstance(assertions, list) and assertions:
        for item in assertions:
            if isinstance(item, dict):
                expected = display(item.get("expected"))
                downstream = display(item.get("downstream_check"))
                lines.append(f"  - {expected} -> {downstream}")
            else:
                lines.append(f"  - {display(item)}")
    else:
        lines.append("  - none")
    lines.append(f"- Screenshot checkpoints: {display(contract.get('screenshot_checkpoints'))}")
    lines.append(f"- Screenshot evidence: {display(contract.get('screenshot_evidence'))}")
    lines.extend([
        "",
        "This brief lists declarations only; it is not approval and does not verify device "
        "state, YAML semantics or authorization.",
    ])
    if errors:
        lines.extend(["", "## Findings"])
        lines.extend(f"- {error}" for error in errors)
    return "\n".join(lines)


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
    parser.add_argument("--brief", action="store_true",
                        help="print a Markdown declaration brief instead of JSON")
    args = parser.parse_args(argv)
    try:
        if not positive(args.max_age_seconds):
            raise ValueError("max age must be positive and finite")
        contract = json.loads(args.contract.read_text(), object_pairs_hook=unique_pairs,
                              parse_constant=lambda value: (_ for _ in ()).throw(ValueError(value)))
        now = time.time()
        errors = findings(contract, args.max_age_seconds, now)
        if args.brief:
            print(brief(contract, now, errors))
        else:
            version = contract.get("version") if isinstance(contract, dict) else None
            print(json.dumps({"version": version if type(version) is int else None,
                              "preflight_clear": not errors, "findings": errors,
                              "coverage": "declarations only; no device state, YAML or approval verified"}))
        return 2 if errors else 0
    except (OSError, ValueError, UnicodeError) as error:
        print(f"invalid preflight input: {error}", file=sys.stderr)
        return 125


if __name__ == "__main__":
    sys.exit(main())
