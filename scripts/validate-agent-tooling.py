#!/usr/bin/env python3
"""Validate HydraFit agent tooling definitions and their references.

Checks:
  - every checked-in agent, command and skill definition has parseable
    frontmatter with its required top-level keys;
  - every agent permission rule declares action, resource and a valid effect;
  - the file references inside the tooling surface resolve (root-relative
    paths, and skill-relative links under .opencode/skills/);
  - the living docs declared in AGENTS.md keep their mechanical invariants
    (existence, numbered headings, relative links and the doc-impact map).

Read-only and standard library only. Exit codes:
    0  consistent
    1  findings (printed one per line as `path: message`)
"""

from __future__ import annotations

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SCRIPTS_DIR = pathlib.Path(__file__).resolve().parent
if str(SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIR))

import living_docs  # noqa: E402  (sibling module, resolved from SCRIPTS_DIR)

# Directories whose backticked paths are resolved from the repository root.
ROOT_RELATIVE_PREFIXES = (
    "docs/",
    "scripts/",
    ".github/",
    ".opencode/",
    "core/",
    "feature/",
    "shared/",
    "androidApp/",
    "gradle/",
    "iosApp/",
)

# Tooling-surface documents whose references are checked.
SURFACE_DOCS = (
    "docs/agent-ci-release.md",
    "docs/agent-project-reference.md",
    "docs/agent-ui-verification.md",
    "docs/review-discipline.md",
    "docs/post-execution-review.md",
    "docs/review-prompt-template.md",
    "docs/maestro-evaluation.md",
)

REQUIRED_KEYS = {
    "agents": ("description", "mode"),
    "commands": ("description",),
    "skills": ("name", "description"),
}
VALID_EFFECTS = ("allow", "deny", "ask")

# Required structure of the coder workflow section in .opencode/agents/coder.md.
CODER_WORKFLOW_HEADING = "### Coder workflow"
CODER_WORKFLOW_STEPS = ("Style sources", "Lint first", "Consumer map", "Structured report")
CODER_WORKFLOW_TOKENS = (
    "android_studio",
    "defaults the limit to 100",
    "ktlint before",
    "no Gradle tasks",
    "producer",
    "consumers",
    "scheduler/clock",
    "elapsed",
    "remaining",
    "correction round",
)

TOP_LEVEL_KEY_RE = re.compile(r"^([A-Za-z_][A-Za-z0-9_-]*)\s*:\s*(.*)$")
PERMISSION_RULE_RE = re.compile(r"^\s*-\s+([A-Za-z_]+)\s*:\s*(.*)$")
PERMISSION_FIELD_RE = re.compile(r"^\s+([A-Za-z_]+)\s*:\s*(.*)$")
COMMENT_OR_BLANK_RE = re.compile(r"^\s*(?:#.*)?$")
BACKTICK_PATH_RE = re.compile(r"`([A-Za-z0-9_][A-Za-z0-9_./-]*\.(?:md|py|sh|json|yml|yaml))`")
MARKDOWN_LINK_RE = re.compile(r"\]\(([^)]+)\)")
LINK_EXTENSIONS = (".md", ".py", ".sh")
URL_PREFIXES = ("http://", "https://", "mailto:")


def clean_scalar(raw: str) -> str:
    """A YAML scalar without its optional quotes or trailing inline comment."""
    value = raw.strip()
    if value[:1] in ("'", '"'):
        end = value.find(value[0], 1)
        return value[1:end] if end != -1 else value
    # An unquoted YAML scalar ends before a whitespace-prefixed comment.
    return re.split(r"\s+#", value, maxsplit=1)[0].strip()


def markdown_link_target(raw: str) -> str:
    """The file path of a Markdown link target, without fragment or title."""
    target = raw.strip()
    if target.startswith("<") and target.endswith(">"):
        target = target[1:-1].strip()
    target = target.split("#", 1)[0].strip()
    return target.split(" ", 1)[0]


def read_frontmatter(path: pathlib.Path) -> list[str] | None:
    """Frontmatter lines between the delimiting `---` lines, or None if absent."""
    lines = path.read_text().splitlines()
    if not lines or lines[0].strip() != "---":
        return None
    for index in range(1, len(lines)):
        if lines[index].strip() == "---":
            return lines[1:index]
    return None


def top_level_scalars(frontmatter: list[str]) -> dict[str, str]:
    scalars: dict[str, str] = {}
    for line in frontmatter:
        match = TOP_LEVEL_KEY_RE.match(line)
        if match:
            scalars.setdefault(match.group(1), clean_scalar(match.group(2)))
    return scalars


def permission_rules(frontmatter: list[str]) -> list[dict[str, str]]:
    """Rules of the top-level `permissions:` list, one dict per `- action:` item."""
    rules: list[dict[str, str]] = []
    current: dict[str, str] | None = None
    in_permissions = False
    for line in frontmatter:
        if re.match(r"^permissions\s*:", line):
            in_permissions = True
            current = None
            continue
        if not in_permissions:
            continue
        if TOP_LEVEL_KEY_RE.match(line):
            break
        if COMMENT_OR_BLANK_RE.match(line):
            continue
        rule_match = PERMISSION_RULE_RE.match(line)
        if rule_match:
            current = {rule_match.group(1): clean_scalar(rule_match.group(2))}
            rules.append(current)
            continue
        field_match = PERMISSION_FIELD_RE.match(line)
        if field_match and current is not None:
            current[field_match.group(1)] = clean_scalar(field_match.group(2))
    return rules


def definition_findings(path: pathlib.Path, kind: str) -> list[str]:
    findings: list[str] = []
    frontmatter = read_frontmatter(path)
    if frontmatter is None:
        return [f"{path}: missing frontmatter delimiters"]
    scalars = top_level_scalars(frontmatter)
    for key in REQUIRED_KEYS[kind]:
        if key not in scalars:
            findings.append(f"{path}: missing required key '{key}'")
        elif not scalars[key]:
            findings.append(f"{path}: required key '{key}' is empty")
    if kind == "agents":
        for index, rule in enumerate(permission_rules(frontmatter), start=1):
            missing = [key for key in ("action", "resource", "effect") if key not in rule]
            if missing:
                findings.append(f"{path}: permission rule {index} missing {', '.join(missing)}")
                continue
            if rule["effect"] not in VALID_EFFECTS:
                findings.append(f"{path}: permission rule {index} has effect {rule['effect']!r}")
    return findings


def agent_tooling_definitions(root: pathlib.Path) -> list[tuple[pathlib.Path, str]]:
    definitions: list[tuple[pathlib.Path, str]] = []
    for name, kind in (("agents", "agents"), ("commands", "commands")):
        directory = root / ".opencode" / name
        if directory.is_dir():
            definitions.extend((path, kind) for path in sorted(directory.glob("*.md")))
    skills = root / ".opencode" / "skills"
    if skills.is_dir():
        definitions.extend((path, "skills") for path in sorted(skills.glob("*/SKILL.md")))
    return definitions


def surface_files(root: pathlib.Path) -> list[pathlib.Path]:
    files = [root / "AGENTS.md"]
    opencode = root / ".opencode"
    if opencode.is_dir():
        files.extend(sorted(p for p in opencode.rglob("*.md") if "node_modules" not in p.parts))
    files.extend(root / doc for doc in SURFACE_DOCS)
    return [path for path in files if path.exists()]


def skill_directory(root: pathlib.Path, path: pathlib.Path) -> pathlib.Path | None:
    skills = root / ".opencode" / "skills"
    try:
        relative = path.relative_to(skills)
    except ValueError:
        return None
    return skills / relative.parts[0]


def resolves_backticked(root: pathlib.Path, path: pathlib.Path, target: str) -> bool:
    if target.startswith(URL_PREFIXES):
        return True
    if target.startswith(ROOT_RELATIVE_PREFIXES):
        return (root / target).exists()
    # Skill-relative forms such as `references/fatigue.md` or `../SKILL.md`.
    skills = skill_directory(root, path)
    if skills is not None and (
        target.startswith("references/") or target.startswith("./") or target.startswith("../")
    ):
        return (path.parent / target).exists()
    return True


def resolves_link(root: pathlib.Path, path: pathlib.Path, target: str) -> bool:
    if target.startswith(URL_PREFIXES):
        return True
    return (path.parent / target).exists() or (root / target).exists()


def reference_findings(root: pathlib.Path, path: pathlib.Path) -> list[str]:
    findings: list[str] = []
    text = path.read_text()
    for match in BACKTICK_PATH_RE.finditer(text):
        target = match.group(1)
        if not resolves_backticked(root, path, target):
            findings.append(f"{path}: dangling reference to {target}")
    for match in MARKDOWN_LINK_RE.finditer(text):
        target = markdown_link_target(match.group(1))
        if not target.endswith(LINK_EXTENSIONS):
            continue
        if not resolves_link(root, path, target):
            findings.append(f"{path}: dangling link to {target}")
    return findings


def batch_state_findings(root: pathlib.Path) -> list[str]:
    """Check the declared protocol's structure, not runtime compliance or prose semantics."""
    path = root / ".opencode/commands/batch.md"
    if not path.exists():
        return []  # Small fixture/tooling trees need not provide the batch command.
    body = path.read_text()
    heading = "### Durable batch state"
    if heading not in body:
        return [f"{path}: missing durable batch state section"]
    section = re.split(r"\n#{1,3} ", body.split(heading, 1)[1], maxsplit=1)[0]
    labels = ("Initialize", "Attempt", "Resume", "Boundary", "Review", "Budget stop", "Publication")
    findings = [f"{path}: durable state missing lifecycle step {label}"
                for label in labels if f"- **{label}:**" not in section]
    for token in ("scripts/task-state.py", "`begin`", "`resolve`", "`resume`", "`checkpoint`",
                  "`evidence`", "`amend`", "`extend`", "boundary --expect", "--require-evidence"):
        if token not in section:
            findings.append(f"{path}: durable state missing protocol reference {token}")
    return findings


def coder_workflow_findings(root: pathlib.Path) -> list[str]:
    """Check the coder workflow section's structure, not runtime brief compliance."""
    path = root / ".opencode/agents/coder.md"
    if not path.exists():
        return []  # Small fixture/tooling trees need not provide the coder agent.
    body = path.read_text()
    if CODER_WORKFLOW_HEADING not in body:
        return [f"{path}: missing coder workflow section"]
    section = re.split(r"\n#{1,3} ", body.split(CODER_WORKFLOW_HEADING, 1)[1], maxsplit=1)[0]
    findings = [f"{path}: coder workflow missing step {step}"
                for step in CODER_WORKFLOW_STEPS if f"- **{step}:**" not in section]
    for token in CODER_WORKFLOW_TOKENS:
        if token not in section:
            findings.append(f"{path}: coder workflow missing protocol reference {token}")
    return findings


TRUST_BOUNDARY_TOKENS = {
    "docs/agent-project-reference.md": ("not under a temporary directory", "not a safety boundary"),
    "AGENTS.md": ("not under a temporary directory",),
    ".opencode/commands/task.md": ("not under a temporary directory", "not a safety boundary"),
    ".opencode/commands/batch.md": ("not under a temporary directory",),
}


def trust_boundary_findings(root: pathlib.Path) -> list[str]:
    """Declaration checks only: cannot observe a directory's lifetime or contain a shell call."""
    reference = root / "docs/agent-project-reference.md"
    if not reference.exists():
        return []  # Small fixture/tooling trees need not provide the reference.
    findings = []
    for relative, tokens in TRUST_BOUNDARY_TOKENS.items():
        path = root / relative
        if not path.exists():
            continue
        body = " ".join(path.read_text().split())
        for token in tokens:
            if token not in body:
                findings.append(f"{path}: missing trust-boundary statement {token!r}")
    return findings


ACCEPTANCE_HEADING = "### Acceptance and authorization evidence"
ACCEPTANCE_STEPS = ("Consumer evidence", "Regression evidence", "Missing verification",
                    "Design decisions", "Record consistency", "Publication")


def acceptance_workflow_findings(root: pathlib.Path) -> list[str]:
    """Declaration checks only: cannot evaluate tests, prose semantics or actual consent."""
    path = root / "docs/agent-project-reference.md"
    if not path.exists():
        return []
    body = path.read_text()
    if ACCEPTANCE_HEADING not in body:
        return [f"{path}: missing acceptance and authorization evidence section"]
    section = re.split(r"\n#{1,3} ", body.split(ACCEPTANCE_HEADING, 1)[1], maxsplit=1)[0]
    findings = [f"{path}: acceptance evidence missing step {step}"
                for step in ACCEPTANCE_STEPS if f"- **{step}:**" not in section]
    for relative in ("AGENTS.md", ".opencode/commands/task.md", ".opencode/commands/batch.md"):
        consumer = root / relative
        if consumer.exists() and "Acceptance and authorization evidence" not in " ".join(consumer.read_text().split()):
            findings.append(f"{consumer}: missing acceptance evidence procedure reference")
    return findings


FORMAT_PASS_HEADING = "### Scoped Kotlin format pass"
FORMAT_PASS_STEPS = ("Module-scoped invocation", "Ownership delta", "Incomplete pass")
FORMAT_PASS_TOKENS = ("separate invocation",)


def format_pass_findings(root: pathlib.Path) -> list[str]:
    """Declaration checks only: cannot tell which paths a format pass actually rewrote."""
    path = root / "docs/agent-project-reference.md"
    if not path.exists():
        return []
    body = path.read_text()
    if FORMAT_PASS_HEADING not in body:
        return [f"{path}: missing scoped Kotlin format pass section"]
    section = re.split(r"\n#{1,3} ", body.split(FORMAT_PASS_HEADING, 1)[1], maxsplit=1)[0]
    findings = [f"{path}: scoped format pass missing step {step}"
                for step in FORMAT_PASS_STEPS if f"- **{step}:**" not in section]
    normalized = " ".join(section.split())
    findings.extend(f"{path}: scoped format pass missing statement {token!r}"
                    for token in FORMAT_PASS_TOKENS if token not in normalized)
    consumer = root / "AGENTS.md"
    if consumer.exists() and "Scoped Kotlin format pass" not in " ".join(consumer.read_text().split()):
        findings.append(f"{consumer}: missing scoped format pass procedure reference")
    return findings


def main(argv: list[str] | None = None) -> int:
    arguments = sys.argv[1:] if argv is None else argv
    root = pathlib.Path(arguments[0]).resolve() if arguments else ROOT
    findings: list[str] = []
    definitions = agent_tooling_definitions(root)
    for path, kind in definitions:
        findings.extend(definition_findings(path, kind))
    surface = surface_files(root)
    for path in surface:
        findings.extend(reference_findings(root, path))
    findings.extend(batch_state_findings(root))
    findings.extend(coder_workflow_findings(root))
    findings.extend(acceptance_workflow_findings(root))
    findings.extend(trust_boundary_findings(root))
    findings.extend(format_pass_findings(root))
    # Evaluate the supplied root: an alternate tree must satisfy the same
    # living-doc declaration, numbering, link and mapping checks as the checkout.
    findings.extend(living_docs.structural_findings(root))
    for finding in findings:
        print(finding)
    print(
        f"checked {len(definitions)} definitions and {len(surface)} surface files; "
        f"findings: {len(findings)}"
    )
    return 0 if findings == [] else 1


if __name__ == "__main__":
    sys.exit(main())
