#!/usr/bin/env python3
"""Produce an advisory change-closure report for planned work.

Given seed files that are about to change, this helper lists predictable
consumers, test doubles and regression coverage, DI/navigation registrations,
migration/schema coverage and living docs. It exists so a batch pre-flight can
name those files before implementation instead of discovering them mid-run.

The symbol discovery is deliberately narrow and heuristic:

  - seed symbols are top-level `interface`, `class` and `object` declarations,
    allowing simple modifiers and annotations; indented/nested declarations are
    ignored;
  - function and method names are not inferred from seeds — pass `--symbol` for
    a method such as `substitute` or any extra name you want to search for;
  - repository `.kt`, `.kts`, `.sq` and `.sqm` files are scanned for whole-word
    symbol matches, ignoring `.git`, `build`, `.gradle` and `node_modules`;
  - a seed file is not reported against its own symbols;
  - test classification uses source-set directory names and `*Test.kt`;
  - DI/navigation classification uses file names (`Koin.kt`, `App.kt`,
    `*Module.kt`, `*Navigation.kt`, `*Graph.kt`);
  - schema seeds list `*MigrationTest.kt` files under
    `core/database/src/*Test/` and build files mentioning
    `verifySqlDelightMigration`;
  - living docs reuse `scripts/living_docs.py`'s impact mapping.

Textual matching cannot resolve types, aliases, reflection or generated code,
so the report is a checklist, never proof that a change is closed.

Read-only and standard library only. Exit codes:
    0    report produced (including empty groups)
    125  invalid input (bad repository, seed, symbol or unreadable seed)
"""

from __future__ import annotations

import argparse
import os
import pathlib
import re
import sys

sys.dont_write_bytecode = True

SCRIPTS_DIR = pathlib.Path(__file__).resolve().parent
DEFAULT_REPO = SCRIPTS_DIR.parent
if str(SCRIPTS_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPTS_DIR))

try:
    import living_docs
except ImportError:  # pragma: no cover - defensive, the sibling always exists
    living_docs = None

SOURCE_SUFFIXES = (".kt", ".kts", ".sq", ".sqm")
IGNORED_DIRECTORIES = {".git", "build", ".gradle", "node_modules"}
TEST_SOURCE_DIRECTORIES = {
    "androidHostTest",
    "androidInstrumentedTest",
    "androidTest",
    "androidUnitTest",
    "commonTest",
    "iosTest",
    "jvmTest",
    "test",
    "testFixtures",
}
DI_FILE_NAMES = ("App.kt", "Koin.kt")
DI_FILE_SUFFIXES = ("Graph.kt", "Module.kt", "Navigation.kt")
SCHEMA_SUFFIXES = (".sq", ".sqm")
BUILD_FILE_NAMES = ("build.gradle", "build.gradle.kts")

DECLARATION_RE = re.compile(
    r"^(?:(?:public|private|protected|internal|expect|actual|abstract|open|"
    r"final|sealed|data|enum|annotation|value|inline|fun)\s+)*"
    r"(?:class|interface|object)\s+([A-Za-z_][A-Za-z0-9_]*)",
    re.MULTILINE,
)
ANNOTATION_LINE_RE = re.compile(
    r"^[ \t]*@[A-Za-z_][A-Za-z0-9_.]*(?:\([^()\n]*\))?[ \t]*$",
    re.MULTILINE,
)
INLINE_ANNOTATION_RE = re.compile(
    r"^(?:@[A-Za-z_][A-Za-z0-9_.]*(?:\([^()\n]*\))?[ \t]+)+",
    re.MULTILINE,
)
TRIPLE_STRING_RE = re.compile(r'""".*?"""', re.DOTALL)
BLOCK_COMMENT_RE = re.compile(r"/\*.*?\*/", re.DOTALL)
LINE_COMMENT_RE = re.compile(r"//[^\n]*")
STRING_RE = re.compile(r'"(?:\\.|[^"\\])*"')
CHAR_RE = re.compile(r"'(?:\\.|[^'\\])*'")
SQL_MIGRATION_MARKER = "verifySqlDelightMigration"


class _ArgumentParser(argparse.ArgumentParser):
    """Use 125 for invalid command-line input instead of argparse's 2."""

    def error(self, message: str) -> None:
        self.print_usage(sys.stderr)
        self.exit(125, f"{self.prog}: error: {message}\n")


def _invalid(message: str) -> int:
    print(message, file=sys.stderr)
    return 125


def _strip_comments_and_strings(text: str) -> str:
    """Blank comments and string literals so declaration regexes see code only.

    This is a heuristic: nested block comments and raw strings are not parsed
    fully, and only the common Kotlin forms are removed.
    """
    text = TRIPLE_STRING_RE.sub('""', text)
    text = BLOCK_COMMENT_RE.sub(" ", text)
    text = LINE_COMMENT_RE.sub("", text)
    text = STRING_RE.sub('""', text)
    return CHAR_RE.sub("''", text)


def parse_type_symbols(text: str) -> set[str]:
    """Top-level type names from one seed file.

    Only declarations starting at column 0 after simple modifiers and
    annotations are included; nested/indented types and function names are not.
    """
    cleaned = _strip_comments_and_strings(text)
    cleaned = ANNOTATION_LINE_RE.sub("", cleaned)
    cleaned = INLINE_ANNOTATION_RE.sub("", cleaned)
    return {match.group(1) for match in DECLARATION_RE.finditer(cleaned)}


def _read_text(path: pathlib.Path) -> str:
    return path.read_text(encoding="utf-8")


def _resolve_repo(raw: pathlib.Path) -> pathlib.Path | None:
    repo = raw.expanduser().resolve()
    if not repo.is_dir():
        return None
    return repo


def _resolve_seeds(
    repo: pathlib.Path, raw_seeds: list[str]
) -> tuple[list[str], str | None]:
    seeds: list[str] = []
    seen: set[str] = set()
    for raw in raw_seeds:
        if not raw:
            return [], "seed paths must not be empty"
        candidate = pathlib.Path(raw)
        if candidate.is_absolute():
            return [], f"seed must be repository-relative: {raw}"
        resolved = (repo / candidate).resolve()
        try:
            relative = resolved.relative_to(repo)
        except ValueError:
            return [], f"seed is outside the repository: {raw}"
        if not resolved.is_file():
            return [], f"seed does not exist: {raw}"
        normalized = relative.as_posix()
        if normalized in seen:
            return [], f"duplicate seed: {normalized}"
        seen.add(normalized)
        seeds.append(normalized)
    return seeds, None


def _symbols_for_seeds(
    repo: pathlib.Path, seeds: list[str], explicit: list[str]
) -> tuple[set[str], str | None]:
    symbols: set[str] = set()
    for raw in explicit:
        symbol = raw.strip()
        if not symbol:
            return set(), "explicit --symbol values must not be empty"
        symbols.add(symbol)
    for seed in seeds:
        try:
            symbols.update(parse_type_symbols(_read_text(repo / seed)))
        except (OSError, UnicodeDecodeError) as error:
            return set(), f"cannot read seed {seed}: {error}"
    return symbols, None


def _iter_source_files(repo: pathlib.Path) -> list[pathlib.Path]:
    """Source files in deterministic order, pruning ignored directories."""
    paths: list[pathlib.Path] = []
    for current, directories, files in os.walk(repo):
        directories[:] = sorted(
            name for name in directories if name not in IGNORED_DIRECTORIES
        )
        base = pathlib.Path(current)
        for name in sorted(files):
            if pathlib.Path(name).suffix in SOURCE_SUFFIXES:
                paths.append(base / name)
    return paths


def _symbol_matches(
    repo: pathlib.Path, seeds: list[str], symbols: set[str]
) -> dict[str, set[str]]:
    """Whole-word symbol matches, excluding the seed files themselves."""
    seed_set = set(seeds)
    patterns = {
        symbol: re.compile(rf"\b{re.escape(symbol)}\b") for symbol in symbols
    }
    matches: dict[str, set[str]] = {}
    for path in _iter_source_files(repo):
        relative = path.relative_to(repo).as_posix()
        if relative in seed_set:
            continue
        try:
            text = _read_text(path)
        except (OSError, UnicodeDecodeError):
            continue
        found = {
            symbol
            for symbol, pattern in patterns.items()
            if pattern.search(text)
        }
        if found:
            matches[relative] = found
    return matches


def _is_test_path(relative: str) -> bool:
    parts = pathlib.PurePosixPath(relative).parts
    if any(part in TEST_SOURCE_DIRECTORIES for part in parts):
        return True
    return relative.endswith("Test.kt")


def _is_di_path(relative: str) -> bool:
    name = pathlib.PurePosixPath(relative).name
    return name in DI_FILE_NAMES or name.endswith(DI_FILE_SUFFIXES)


def _is_schema_seed(seed: str) -> bool:
    return seed.endswith(SCHEMA_SUFFIXES) or "/sqldelight/" in seed


def _migration_coverage(repo: pathlib.Path) -> tuple[list[str], list[str]]:
    tests: set[str] = set()
    source_root = repo / "core" / "database" / "src"
    if source_root.is_dir():
        for source_set in source_root.glob("*Test"):
            if not source_set.is_dir():
                continue
            for path in source_set.rglob("*MigrationTest.kt"):
                tests.add(path.relative_to(repo).as_posix())

    builds: set[str] = set()
    for path in _iter_files_named(repo, BUILD_FILE_NAMES):
        try:
            text = _read_text(path)
        except (OSError, UnicodeDecodeError):
            continue
        if SQL_MIGRATION_MARKER in text:
            builds.add(path.relative_to(repo).as_posix())
    return sorted(tests), sorted(builds)


def _iter_files_named(
    repo: pathlib.Path, names: tuple[str, ...]
) -> list[pathlib.Path]:
    paths: list[pathlib.Path] = []
    for current, directories, files in os.walk(repo):
        directories[:] = sorted(
            name for name in directories if name not in IGNORED_DIRECTORIES
        )
        base = pathlib.Path(current)
        for name in sorted(files):
            if name in names:
                paths.append(base / name)
    return paths


def _living_docs(
    repo: pathlib.Path, paths: list[str]
) -> tuple[list[str], list[str]]:
    """Mapped living docs plus advisory warnings for missing declarations."""
    warnings: list[str] = []
    if living_docs is None:
        return [], [
            "scripts/living_docs.py is unavailable; living-doc mapping was skipped."
        ]
    if not (repo / "AGENTS.md").is_file():
        warnings.append(
            "AGENTS.md is missing; living-doc mapping may be incomplete."
        )
    if not (repo / "docs" / "doc-impact.json").is_file():
        warnings.append(
            "docs/doc-impact.json is missing; living-doc mapping was skipped."
        )
    resolved, findings = living_docs.resolve_living_docs(repo)
    if not resolved and not warnings:
        warnings.append(
            "AGENTS.md has no resolvable living-doc declaration; "
            "mapping may be incomplete."
        )
    elif findings and not warnings:
        warnings.append(
            "AGENTS.md living-doc declaration has findings; "
            "mapping may be incomplete."
        )
    return living_docs.mapped_docs(repo, paths), warnings


def _bullets(paths: list[str], empty: str = "(none)") -> list[str]:
    if not paths:
        return [f"- {empty}"]
    return [f"- `{path}`" for path in paths]


def build_report(
    repo: pathlib.Path, seeds: list[str], symbols: set[str]
) -> str:
    matches = _symbol_matches(repo, seeds, symbols)
    consumers = sorted(
        path for path in matches if not _is_test_path(path)
    )
    tests = sorted(path for path in matches if _is_test_path(path))
    di = sorted(path for path in matches if _is_di_path(path))

    if any(_is_schema_seed(seed) for seed in seeds):
        migration_tests, migration_builds = _migration_coverage(repo)
    else:
        migration_tests, migration_builds = [], []

    docs, warnings = _living_docs(repo, seeds + consumers)

    lines = ["# Change-closure advisory report", "", "## Seeds", ""]
    lines.extend(_bullets(seeds))
    lines.extend(["", "## Consumers", ""])
    lines.extend(_bullets(consumers))
    lines.extend(["", "## Test doubles and regression coverage", ""])
    lines.extend(_bullets(tests))
    lines.extend(["", "## DI and navigation", ""])
    lines.extend(_bullets(di))
    lines.extend(["", "## Migration and schema coverage", ""])
    lines.extend(_bullets(migration_tests))
    lines.extend(["", "Build files verifying migrations:", ""])
    lines.extend(_bullets(migration_builds))
    lines.extend(["", "## Living docs", ""])
    lines.extend(_bullets(docs))
    if warnings:
        lines.extend([""] + [f"_Advisory: {warning}_" for warning in warnings])
    lines.extend(
        [
            "",
            "Advisory only: symbol matching cannot prove closure or replace "
            "consumer inspection.",
            "",
        ]
    )
    return "\n".join(lines)


def main(argv: list[str] | None = None) -> int:
    parser = _ArgumentParser(description=__doc__)
    parser.add_argument(
        "--repo",
        type=pathlib.Path,
        default=DEFAULT_REPO,
        help="repository root (default: this checkout)",
    )
    parser.add_argument(
        "--seed",
        action="append",
        required=True,
        metavar="PATH",
        help="repository-relative file about to change (repeatable)",
    )
    parser.add_argument(
        "--symbol",
        action="append",
        default=[],
        metavar="NAME",
        help=(
            "additional whole-word name to search for, such as a method or "
            "function (repeatable)"
        ),
    )
    arguments = parser.parse_args(argv)

    repo = _resolve_repo(arguments.repo)
    if repo is None:
        return _invalid(
            f"{arguments.repo}: repository directory does not exist"
        )

    seeds, error = _resolve_seeds(repo, arguments.seed)
    if error is not None:
        return _invalid(error)

    symbols, error = _symbols_for_seeds(repo, seeds, arguments.symbol)
    if error is not None:
        return _invalid(error)

    print(build_report(repo, seeds, symbols), end="")
    return 0


if __name__ == "__main__":
    sys.exit(main())
