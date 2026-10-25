"""Mechanical checks for HydraFit's living documentation.

The living-doc set is declared in the `**Living docs (keep current):**`
paragraph of `AGENTS.md`. This module resolves that list (including the
mechanics skill's `references/*.md` topic files), checks the mechanical
invariants those documents are expected to keep, and maps changed paths to the
documents that should be reviewed.

Standard library only. The checks are structural: they cannot judge whether
prose, rationale or a stated verification result is accurate.
"""

from __future__ import annotations

import json
import pathlib
import re
import urllib.parse

LIVING_DOCS_LABEL = "**Living docs (keep current):**"
PARAGRAPH_RE = re.compile(
    re.escape(LIVING_DOCS_LABEL)
    + r"(?P<body>.*?)(?=\n[ \t]*(?:[-*+] |#{1,6} )|\n[ \t]*\n|\Z)",
    re.DOTALL,
)
BACKTICK_RE = re.compile(r"`([^`]+)`")
NUMBERED_HEADING_RE = re.compile(
    r"^(#{1,6})\s+(\d+(?:\.\d+)*)\.(?:\s+(.*\S))?\s*$"
)
MARKDOWN_LINK_RE = re.compile(r"\]\(([^)]+)\)")
URL_SCHEME_RE = re.compile(r"^[A-Za-z][A-Za-z0-9+.-]*:")
URL_PREFIXES = ("http://", "https://", "mailto:")

IMPACT_PATH = "docs/doc-impact.json"


def _display(root: pathlib.Path, path: pathlib.Path) -> str:
    """A stable repository-relative display path."""
    try:
        return path.relative_to(root).as_posix()
    except ValueError:
        return path.as_posix()


def _normalize(path: str) -> str:
    normalized = path.replace("\\", "/")
    while normalized.startswith("./"):
        normalized = normalized[2:]
    return normalized


def _add_doc(docs: list[str], path: str) -> None:
    normalized = _normalize(path)
    if normalized and normalized not in docs:
        docs.append(normalized)


def resolve_living_docs(root: pathlib.Path) -> tuple[list[str], list[str]]:
    """Resolve the AGENTS.md living-doc declaration.

    Returns repository-relative paths in declaration order and findings from
    resolving the declaration itself.
    """
    root = pathlib.Path(root)
    agents = root / "AGENTS.md"
    try:
        text = agents.read_text(encoding="utf-8")
    except OSError:
        return [], ["AGENTS.md: missing 'Living docs (keep current)' paragraph"]

    match = PARAGRAPH_RE.search(text)
    if match is None:
        return [], ["AGENTS.md: missing 'Living docs (keep current)' paragraph"]

    paragraph = match.group("body")
    docs: list[str] = []
    findings: list[str] = []
    last_skill_dir: pathlib.Path | None = None

    for raw in BACKTICK_RE.findall(paragraph):
        token = raw.strip()
        if not token or not token.endswith(".md"):
            continue
        if token.startswith(URL_PREFIXES) or token.startswith("/"):
            continue

        if "*" in token or "?" in token:
            bases: list[pathlib.Path] = []
            if last_skill_dir is not None and not token.startswith(
                (".opencode/", "docs/", "scripts/")
            ):
                bases.append(last_skill_dir)
            bases.append(root)
            matches: list[pathlib.Path] = []
            for base in bases:
                matches = sorted(base.glob(token))
                if matches:
                    break
            if not matches:
                findings.append(
                    f"AGENTS.md: living docs glob {token!r} matched no files"
                )
            for path in matches:
                _add_doc(docs, _display(root, path))
            continue

        _add_doc(docs, token)
        if token.endswith("/SKILL.md"):
            last_skill_dir = root / pathlib.PurePosixPath(token).parent

    if len(docs) < 5:
        findings.append(
            "AGENTS.md: living docs paragraph yields fewer than 5 documents "
            f"(found {len(docs)})"
        )
    return docs, findings


def _blank_fenced_code(text: str) -> str:
    """Blank fenced code lines while preserving line numbers."""
    lines = text.splitlines()
    output: list[str] = []
    fence: str | None = None
    for line in lines:
        stripped = line.lstrip()
        if fence is None:
            if stripped.startswith("```") or stripped.startswith("~~~"):
                fence = stripped[:3]
                output.append("")
            else:
                output.append(line)
        else:
            if stripped.startswith(fence):
                fence = None
            output.append("")
    return "\n".join(output)


def _numbered_heading_findings(rel: str, text: str) -> list[str]:
    findings: list[str] = []
    headings: list[tuple[int, int, tuple[int, ...], str]] = []
    for line_number, line in enumerate(text.splitlines(), start=1):
        match = NUMBERED_HEADING_RE.match(line)
        if match is None:
            continue
        headings.append(
            (
                line_number,
                len(match.group(1)),
                tuple(int(part) for part in match.group(2).split(".")),
                (match.group(3) or "").strip(),
            )
        )

    groups: dict[tuple[int, ...], list[tuple[int, tuple[int, ...]]]] = {}
    for line_number, _level, number, _title in headings:
        groups.setdefault(number[:-1], []).append((line_number, number))

    for items in groups.values():
        previous: int | None = None
        for line_number, number in items:
            last = number[-1]
            label = ".".join(str(part) for part in number)
            if previous is not None:
                if last == previous:
                    findings.append(
                        f"{rel}: duplicate numbered heading {label} "
                        f"at line {line_number}"
                    )
                elif last < previous:
                    findings.append(
                        f"{rel}: out-of-order numbered heading {label} "
                        f"at line {line_number}"
                    )
            previous = last

    seen_titles: dict[str, int] = {}
    for line_number, _level, _number, title in headings:
        if not title:
            continue
        key = re.sub(r"\s+", " ", title).strip().casefold()
        if key in seen_titles:
            findings.append(
                f"{rel}: duplicate numbered heading title {title!r} "
                f"at line {line_number} (first at line {seen_titles[key]})"
            )
        else:
            seen_titles[key] = line_number
    return findings


def _link_target(raw: str) -> str:
    """The path portion of a Markdown link target, without title or anchor."""
    target = raw.strip()
    if target.startswith("<") and target.endswith(">"):
        target = target[1:-1].strip()
    else:
        target = target.split(None, 1)[0] if target else target
    target = target.split("#", 1)[0]
    target = target.split("?", 1)[0]
    return target.strip()


def _link_findings(root: pathlib.Path, rel: str, text: str) -> list[str]:
    findings: list[str] = []
    document = root / rel
    for match in MARKDOWN_LINK_RE.finditer(text):
        raw = match.group(1).strip()
        target = _link_target(raw)
        if not target or target.startswith("#") or target.startswith("//"):
            continue
        if target.startswith(URL_PREFIXES) or URL_SCHEME_RE.match(target):
            continue
        target = urllib.parse.unquote(target)
        if target.startswith("/"):
            resolved = root / target.lstrip("/")
        else:
            resolved = document.parent / target
        if not resolved.exists():
            findings.append(f"{rel}: dangling link to {target}")
    return findings


def _load_mapping(root: pathlib.Path) -> tuple[dict[str, object] | None, list[str]]:
    path = root / IMPACT_PATH
    if not path.is_file():
        return None, [f"{IMPACT_PATH}: missing living-doc impact mapping"]
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        return None, [f"{IMPACT_PATH}: invalid JSON ({error})"]
    if not isinstance(data, dict):
        return None, [f"{IMPACT_PATH}: top-level value must be an object"]
    return data, []


def _mapping_findings(
    root: pathlib.Path,
    data: dict[str, object],
    living_set: set[str],
) -> list[str]:
    findings: list[str] = []
    for prefix, mapped in data.items():
        if not isinstance(prefix, str) or not prefix.strip():
            findings.append(
                f"{IMPACT_PATH}: mapping keys must be non-empty "
                "repository-relative prefixes"
            )
            continue
        if not isinstance(mapped, list) or not all(
            isinstance(doc, str) for doc in mapped
        ):
            findings.append(
                f"{IMPACT_PATH}: mapping for {prefix!r} must be a list "
                "of document paths"
            )
            continue
        for doc in mapped:
            normalized = _normalize(doc)
            if not (root / normalized).is_file():
                findings.append(
                    f"{IMPACT_PATH}: mapped doc {doc!r} does not exist"
                )
            elif living_set and normalized not in living_set:
                findings.append(
                    f"{IMPACT_PATH}: mapped doc {doc!r} is not a living doc"
                )
    return findings


def structural_findings(root: pathlib.Path) -> list[str]:
    """Mechanical drift findings as `path: message` strings."""
    root = pathlib.Path(root)
    living, findings = resolve_living_docs(root)
    living_set = set(living)

    for rel in living:
        path = root / rel
        if not path.is_file():
            findings.append(f"{rel}: missing living doc")
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except OSError as error:
            findings.append(f"{rel}: cannot read document ({error})")
            continue
        text = _blank_fenced_code(text)
        findings.extend(_numbered_heading_findings(rel, text))
        findings.extend(_link_findings(root, rel, text))

    data, load_findings = _load_mapping(root)
    findings.extend(load_findings)
    if data is not None:
        findings.extend(_mapping_findings(root, data, living_set))
    return findings


def mapped_docs(root: pathlib.Path, changed_paths: list[str]) -> list[str]:
    """Living docs whose longest matching impact prefix covers a changed path."""
    root = pathlib.Path(root)
    data, _findings = _load_mapping(root)
    if data is None:
        return []
    living, _living_findings = resolve_living_docs(root)
    living_set = set(living)
    selected: set[str] = set()

    changed = [_normalize(path) for path in changed_paths if path]
    for path in changed:
        best: tuple[str, list[object]] | None = None
        for raw_prefix, mapped in data.items():
            if not isinstance(raw_prefix, str) or not isinstance(mapped, list):
                continue
            prefix = _normalize(raw_prefix)
            if not prefix:
                continue
            matches = path == prefix or path.startswith(
                prefix if prefix.endswith("/") else prefix + "/"
            )
            if not matches:
                continue
            if best is None or len(prefix) > len(best[0]):
                best = (prefix, mapped)
        if best is None:
            continue
        for doc in best[1]:
            if not isinstance(doc, str):
                continue
            normalized = _normalize(doc)
            if normalized in living_set:
                selected.add(normalized)

    return sorted(selected)
