"""Offline tests for scripts/living_docs.py and scripts/check-living-docs.py.

Standard library only; every test builds a throwaway tree under a temporary
directory, so repository state, the emulator and external tools are untouched.

Run: python3 -B -m unittest discover -s scripts/tests -p 'test_*.py'
"""

import importlib.util
import json
import os
import pathlib
import subprocess
import sys
import tempfile
import unittest

sys.dont_write_bytecode = True
SCRIPTS_DIR = pathlib.Path(__file__).resolve().parents[1]
MODULE_PATH = SCRIPTS_DIR / "living_docs.py"
CLI_PATH = SCRIPTS_DIR / "check-living-docs.py"

_spec = importlib.util.spec_from_file_location("living_docs", MODULE_PATH)
ld = importlib.util.module_from_spec(_spec)
assert _spec.loader is not None
sys.modules["living_docs"] = ld
_spec.loader.exec_module(ld)

CLEAN_AGENTS = """# Agents

- **Living docs (keep current):** `docs/a.md`, `docs/b.md`, `docs/c.md`,
  `docs/d.md`, `docs/e.md`, `.opencode/skills/demo/SKILL.md`, and its
  `references/*.md` topic files.
"""

CLEAN_IMPACT = {
    "core/": ["docs/a.md"],
    "core/domain/": ["docs/b.md"],
}


def write(root: pathlib.Path, relative: str, text: str) -> pathlib.Path:
    path = root / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text)
    return path


def make_root(base: pathlib.Path, agents: str = CLEAN_AGENTS) -> pathlib.Path:
    write(base, "AGENTS.md", agents)
    write(base, "docs/a.md", "# A\n\nOverview.\n")
    write(base, "docs/b.md", "# B\n\nOverview.\n")
    write(
        base,
        "docs/c.md",
        "# C\n\n[web](https://example.com) and [anchor](#c).\n",
    )
    write(
        base,
        "docs/d.md",
        "# D\n\n```text\n## 1. Example\n[x](missing.md)\n```\n",
    )
    write(base, "docs/e.md", "# E\n")
    write(base, ".opencode/skills/demo/SKILL.md", "# Demo\n")
    write(base, ".opencode/skills/demo/references/topic.md", "# Topic\n")
    write(base, "docs/doc-impact.json", json.dumps(CLEAN_IMPACT, indent=2) + "\n")
    return base


def run_cli(root: pathlib.Path, *arguments: str) -> subprocess.CompletedProcess[str]:
    environment = dict(os.environ)
    environment["PYTHONDONTWRITEBYTECODE"] = "1"
    return subprocess.run(
        [sys.executable, str(CLI_PATH), "--root", str(root), *arguments],
        capture_output=True,
        text=True,
        check=False,
        env=environment,
    )


class ResolutionTests(unittest.TestCase):
    def test_clean_fixture_has_no_findings(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = make_root(pathlib.Path(tmp))
            self.assertEqual([], ld.structural_findings(root))

    def test_missing_living_docs_paragraph_is_a_finding(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            write(root, "AGENTS.md", "# Agents\n")
            write(root, "docs/doc-impact.json", "{}\n")
            findings = ld.structural_findings(root)
            self.assertEqual(1, len(findings))
            self.assertIn("missing 'Living docs (keep current)' paragraph", findings[0])

    def test_fewer_than_five_documents_is_a_finding(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            write(
                root,
                "AGENTS.md",
                "- **Living docs (keep current):** `docs/a.md`, `docs/b.md`.\n",
            )
            write(root, "docs/a.md", "# A\n")
            write(root, "docs/b.md", "# B\n")
            write(root, "docs/doc-impact.json", "{}\n")
            findings = ld.structural_findings(root)
            self.assertTrue(any("fewer than 5 documents" in item for item in findings))

    def test_skill_topic_files_are_expanded(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = make_root(pathlib.Path(tmp))
            documents, findings = ld.resolve_living_docs(root)
            self.assertEqual([], findings)
            self.assertIn(
                ".opencode/skills/demo/references/topic.md", documents
            )

    def test_dangling_link_in_skill_topic_is_reported(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = make_root(pathlib.Path(tmp))
            write(
                root,
                ".opencode/skills/demo/references/topic.md",
                "# Topic\n\n[gone](missing.md)\n",
            )
            findings = ld.structural_findings(root)
            self.assertTrue(
                any("references/topic.md: dangling link to missing.md" in item
                    for item in findings)
            )


class HeadingTests(unittest.TestCase):
    def test_duplicate_numbered_heading_is_reported(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = make_root(pathlib.Path(tmp))
            write(root, "docs/a.md", "# A\n\n## 1. One\n\n## 1. Two\n")
            findings = ld.structural_findings(root)
            self.assertTrue(
                any("docs/a.md: duplicate numbered heading 1" in item
                    for item in findings)
            )

    def test_out_of_order_numbered_heading_is_reported(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = make_root(pathlib.Path(tmp))
            write(root, "docs/a.md", "# A\n\n## 2. Two\n\n## 1. One\n")
            findings = ld.structural_findings(root)
            self.assertTrue(
                any("docs/a.md: out-of-order numbered heading 1" in item
                    for item in findings)
            )

    def test_duplicate_numbered_heading_title_is_reported(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = make_root(pathlib.Path(tmp))
            write(root, "docs/a.md", "# A\n\n## 1. Same\n\n## 2. Same\n")
            findings = ld.structural_findings(root)
            self.assertTrue(
                any("duplicate numbered heading title 'Same'" in item
                    for item in findings)
            )

    def test_numbered_headings_in_fenced_code_are_ignored(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = make_root(pathlib.Path(tmp))
            write(
                root,
                "docs/a.md",
                "# A\n\n```text\n## 1. One\n## 1. Two\n```\n",
            )
            self.assertEqual([], ld.structural_findings(root))


class LinkTests(unittest.TestCase):
    def test_dangling_relative_link_is_reported(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = make_root(pathlib.Path(tmp))
            write(root, "docs/a.md", "# A\n\n[missing](missing.md)\n")
            findings = ld.structural_findings(root)
            self.assertTrue(
                any("docs/a.md: dangling link to missing.md" in item
                    for item in findings)
            )

    def test_existing_directory_link_is_not_reported(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = make_root(pathlib.Path(tmp))
            (root / "docs" / "nested").mkdir()
            write(root, "docs/a.md", "# A\n\n[dir](nested/)\n")
            self.assertEqual([], ld.structural_findings(root))

    def test_missing_living_doc_is_reported(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = make_root(pathlib.Path(tmp))
            (root / "docs/a.md").unlink()
            findings = ld.structural_findings(root)
            self.assertTrue(
                any("docs/a.md: missing living doc" in item for item in findings)
            )


class MappingTests(unittest.TestCase):
    def _root_with(self, base: pathlib.Path, mapping: dict) -> pathlib.Path:
        root = make_root(base)
        write(root, "docs/doc-impact.json", json.dumps(mapping, indent=2) + "\n")
        return root

    def test_longest_prefix_wins(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self._root_with(
                pathlib.Path(tmp),
                {"core/": ["docs/a.md"], "core/domain/": ["docs/b.md"]},
            )
            self.assertEqual(
                ["docs/b.md"], ld.mapped_docs(root, ["core/domain/Thing.kt"])
            )
            self.assertEqual(
                ["docs/a.md"], ld.mapped_docs(root, ["core/Other.kt"])
            )

    def test_multiple_changes_are_deduplicated(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self._root_with(
                pathlib.Path(tmp),
                {"core/domain/": ["docs/b.md"]},
            )
            self.assertEqual(
                ["docs/b.md"],
                ld.mapped_docs(
                    root,
                    ["core/domain/One.kt", "core/domain/Two.kt"],
                ),
            )

    def test_missing_mapped_doc_is_a_finding(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self._root_with(
                pathlib.Path(tmp),
                {"core/": ["docs/missing.md"]},
            )
            findings = ld.structural_findings(root)
            self.assertTrue(
                any(
                    "mapped doc 'docs/missing.md' does not exist" in item
                    for item in findings
                )
            )
            self.assertEqual([], ld.mapped_docs(root, ["core/Thing.kt"]))

    def test_mapped_doc_outside_living_set_is_a_finding(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = self._root_with(
                pathlib.Path(tmp),
                {"core/": ["README.md"]},
            )
            write(root, "README.md", "# Readme\n")
            findings = ld.structural_findings(root)
            self.assertTrue(
                any(
                    "mapped doc 'README.md' is not a living doc" in item
                    for item in findings
                )
            )


class CliTests(unittest.TestCase):
    def test_structural_mode_exits_zero_when_clean(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = make_root(pathlib.Path(tmp))
            result = run_cli(root)
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertEqual("", result.stdout)

    def test_structural_mode_exits_one_with_findings(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = make_root(pathlib.Path(tmp))
            write(root, "docs/a.md", "# A\n\n## 1. One\n\n## 1. Two\n")
            result = run_cli(root)
            self.assertEqual(1, result.returncode)
            self.assertIn("duplicate numbered heading 1", result.stdout)

    def test_changed_from_lists_mapped_docs_and_exits_zero(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = make_root(pathlib.Path(tmp))
            subprocess.run(
                ["git", "init", "-q"],
                cwd=root,
                check=True,
                capture_output=True,
                text=True,
            )
            subprocess.run(
                ["git", "add", "-A"],
                cwd=root,
                check=True,
                capture_output=True,
                text=True,
            )
            subprocess.run(
                [
                    "git",
                    "-c",
                    "user.email=test@example.com",
                    "-c",
                    "user.name=Test",
                    "commit",
                    "-qm",
                    "baseline",
                ],
                cwd=root,
                check=True,
                capture_output=True,
                text=True,
            )
            write(root, "core/domain/Thing.kt", "class Thing\n")

            result = run_cli(root, "--changed-from", "HEAD")
            self.assertEqual(0, result.returncode, result.stderr)
            self.assertIn("Living docs to review:", result.stdout)
            self.assertIn("docs/b.md", result.stdout)

    def test_invalid_changed_from_exits_125(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = make_root(pathlib.Path(tmp))
            result = run_cli(root, "--changed-from", "definitely-not-a-revision")
            self.assertEqual(125, result.returncode)
            self.assertIn("invalid --changed-from", result.stderr)


class ReadOnlyTests(unittest.TestCase):
    def test_structural_checks_do_not_change_file_bytes(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = make_root(pathlib.Path(tmp))
            before = {
                path: (path.read_bytes(), path.stat().st_mtime_ns)
                for path in root.rglob("*")
                if path.is_file()
            }
            ld.structural_findings(root)
            after = {
                path: (path.read_bytes(), path.stat().st_mtime_ns)
                for path in root.rglob("*")
                if path.is_file()
            }
            self.assertEqual(before, after)


if __name__ == "__main__":
    unittest.main()
