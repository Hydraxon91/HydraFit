"""Offline tests for scripts/plan-file-closure.py.

Every test builds a throwaway repository under a temporary directory, so the
checkout, Gradle and external tools are untouched.

Run: python3 -B -m unittest discover -s scripts/tests -p 'test_plan_file_closure.py'
"""

import os
import pathlib
import subprocess
import sys
import tempfile
import unittest

sys.dont_write_bytecode = True
SCRIPTS_DIR = pathlib.Path(__file__).resolve().parents[1]
CLI_PATH = SCRIPTS_DIR / "plan-file-closure.py"

AGENTS = """# Agents

- **Living docs (keep current):** `docs/a.md`, `docs/b.md`, `docs/c.md`,
  `docs/d.md`, `docs/e.md`.
"""


def write(root: pathlib.Path, relative: str, text: str) -> pathlib.Path:
    path = root / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text)
    return path


def run_cli(root: pathlib.Path, *arguments: str) -> subprocess.CompletedProcess[str]:
    environment = dict(os.environ)
    environment["PYTHONDONTWRITEBYTECODE"] = "1"
    return subprocess.run(
        [sys.executable, str(CLI_PATH), "--repo", str(root), *arguments],
        capture_output=True,
        text=True,
        check=False,
        env=environment,
    )


def section(output: str, title: str) -> list[str]:
    """Bullet paths inside one Markdown section."""
    paths: list[str] = []
    active = False
    for line in output.splitlines():
        if line.startswith("## "):
            active = line[3:].strip() == title
            continue
        if not active or not line.startswith("- `"):
            continue
        paths.append(line[3:-1])
    return paths


def make_docs(root: pathlib.Path) -> None:
    write(root, "AGENTS.md", AGENTS)
    for name in ("a", "b", "c", "d", "e"):
        write(root, f"docs/{name}.md", f"# {name.upper()}\n")
    write(
        root,
        "docs/doc-impact.json",
        '{"feature/demo/": ["docs/a.md"], "core/": ["docs/b.md"]}\n',
    )


class ClosureReportTests(unittest.TestCase):
    def test_interface_seed_finds_implementation_consumer_and_test_double(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            seed = write(
                root,
                "core/domain/src/commonMain/kotlin/demo/Thing.kt",
                "interface Thing {\n    fun act()\n}\n",
            )
            implementation = write(
                root,
                "core/database/src/commonMain/kotlin/demo/ThingImpl.kt",
                "class ThingImpl : Thing {\n    override fun act() {}\n}\n",
            )
            consumer = write(
                root,
                "feature/demo/src/commonMain/kotlin/demo/ThingUser.kt",
                "fun useThing(thing: Thing) = thing.act()\n",
            )
            test_double = write(
                root,
                "core/domain/src/commonTest/kotlin/demo/ThingTest.kt",
                "class FakeThing : Thing {\n    override fun act() {}\n}\n",
            )

            result = run_cli(root, "--seed", seed.relative_to(root).as_posix())

            self.assertEqual(0, result.returncode, result.stderr)
            self.assertEqual(
                [seed.relative_to(root).as_posix()],
                section(result.stdout, "Seeds"),
            )
            consumers = section(result.stdout, "Consumers")
            self.assertIn(implementation.relative_to(root).as_posix(), consumers)
            self.assertIn(consumer.relative_to(root).as_posix(), consumers)
            self.assertNotIn(seed.relative_to(root).as_posix(), consumers)
            self.assertEqual(
                [test_double.relative_to(root).as_posix()],
                section(result.stdout, "Test doubles and regression coverage"),
            )

    def test_explicit_symbol_adds_a_match(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            seed = write(
                root,
                "core/domain/src/commonMain/kotlin/demo/Empty.kt",
                "package demo\n",
            )
            user = write(
                root,
                "feature/demo/src/commonMain/kotlin/demo/Uses.kt",
                "val token = MagicToken\n",
            )

            without = run_cli(root, "--seed", seed.relative_to(root).as_posix())
            with_symbol = run_cli(
                root,
                "--seed",
                seed.relative_to(root).as_posix(),
                "--symbol",
                "MagicToken",
            )

            self.assertEqual(0, without.returncode, without.stderr)
            self.assertNotIn(
                user.relative_to(root).as_posix(),
                section(without.stdout, "Consumers"),
            )
            self.assertEqual(0, with_symbol.returncode, with_symbol.stderr)
            self.assertIn(
                user.relative_to(root).as_posix(),
                section(with_symbol.stdout, "Consumers"),
            )

    def test_sql_seed_lists_migration_test_and_build_file(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            seed = write(
                root,
                "core/database/src/commonMain/sqldelight/demo/Plan.sq",
                "CREATE TABLE plan (id INTEGER);\n",
            )
            migration_test = write(
                root,
                "core/database/src/androidHostTest/kotlin/demo/PlanMigrationTest.kt",
                "class PlanMigrationTest\n",
            )
            build_file = write(
                root,
                "core/database/build.gradle.kts",
                "sqldelight {\n    verifySqlDelightMigration\n}\n",
            )

            result = run_cli(root, "--seed", seed.relative_to(root).as_posix())

            self.assertEqual(0, result.returncode, result.stderr)
            migration = section(result.stdout, "Migration and schema coverage")
            self.assertIn(migration_test.relative_to(root).as_posix(), migration)
            self.assertIn(build_file.relative_to(root).as_posix(), result.stdout)

    def test_di_and_navigation_filenames_are_classified(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            seed = write(
                root,
                "core/domain/src/commonMain/kotlin/demo/Thing.kt",
                "interface Thing\n",
            )
            expected = [
                write(
                    root,
                    "shared/src/commonMain/kotlin/demo/Koin.kt",
                    "val thing: Thing? = null\n",
                ),
                write(
                    root,
                    "shared/src/commonMain/kotlin/demo/App.kt",
                    "val thing: Thing? = null\n",
                ),
                write(
                    root,
                    "feature/demo/src/commonMain/kotlin/demo/DemoModule.kt",
                    "val thing: Thing? = null\n",
                ),
                write(
                    root,
                    "feature/demo/src/commonMain/kotlin/demo/DemoNavigation.kt",
                    "val thing: Thing? = null\n",
                ),
                write(
                    root,
                    "feature/demo/src/commonMain/kotlin/demo/DemoGraph.kt",
                    "val thing: Thing? = null\n",
                ),
            ]

            result = run_cli(root, "--seed", seed.relative_to(root).as_posix())

            self.assertEqual(0, result.returncode, result.stderr)
            di = section(result.stdout, "DI and navigation")
            consumers = section(result.stdout, "Consumers")
            for path in expected:
                relative = path.relative_to(root).as_posix()
                self.assertIn(relative, di)
                self.assertIn(relative, consumers)

    def test_living_docs_are_surfaced_from_impact_mapping(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            make_docs(root)
            seed = write(
                root,
                "feature/demo/src/commonMain/kotlin/demo/Thing.kt",
                "interface Thing\n",
            )

            result = run_cli(root, "--seed", seed.relative_to(root).as_posix())

            self.assertEqual(0, result.returncode, result.stderr)
            self.assertEqual(
                ["docs/a.md"],
                section(result.stdout, "Living docs"),
            )
            self.assertNotIn("Advisory:", result.stdout)

    def test_output_is_deterministic_and_sorted(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            seed = write(
                root,
                "core/domain/src/commonMain/kotlin/demo/Thing.kt",
                "interface Thing\n",
            )
            for name in ("Zeta", "Alpha", "Middle"):
                write(
                    root,
                    f"feature/demo/src/commonMain/kotlin/demo/{name}.kt",
                    "val thing: Thing? = null\n",
                )

            first = run_cli(root, "--seed", seed.relative_to(root).as_posix())
            second = run_cli(root, "--seed", seed.relative_to(root).as_posix())

            self.assertEqual(0, first.returncode, first.stderr)
            self.assertEqual(first.stdout, second.stdout)
            consumers = section(first.stdout, "Consumers")
            self.assertEqual(sorted(consumers), consumers)

    def test_no_matches_still_exits_zero(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            seed = write(
                root,
                "core/domain/src/commonMain/kotlin/demo/Empty.kt",
                "package demo\n",
            )

            result = run_cli(root, "--seed", seed.relative_to(root).as_posix())

            self.assertEqual(0, result.returncode, result.stderr)
            self.assertIn("- (none)", result.stdout)

    def test_default_symbols_are_top_level_types_only(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            seed = write(
                root,
                "core/domain/src/commonMain/kotlin/demo/Mixed.kt",
                "package demo\n"
                "fun helper() = Unit\n"
                "class Outer {\n"
                "    class Nested\n"
                "    fun inner() = Unit\n"
                "}\n",
            )
            outer_user = write(
                root,
                "feature/demo/src/commonMain/kotlin/demo/OuterUser.kt",
                "val outer: Outer? = null\n",
            )
            nested_user = write(
                root,
                "feature/demo/src/commonMain/kotlin/demo/NestedUser.kt",
                "val nested: Nested? = null\n",
            )
            helper_user = write(
                root,
                "feature/demo/src/commonMain/kotlin/demo/HelperUser.kt",
                "fun callHelper() = helper()\n",
            )

            default = run_cli(root, "--seed", seed.relative_to(root).as_posix())
            explicit = run_cli(
                root,
                "--seed",
                seed.relative_to(root).as_posix(),
                "--symbol",
                "Nested",
                "--symbol",
                "helper",
            )

            self.assertEqual(0, default.returncode, default.stderr)
            self.assertEqual(
                [outer_user.relative_to(root).as_posix()],
                section(default.stdout, "Consumers"),
            )
            explicit_consumers = section(explicit.stdout, "Consumers")
            self.assertIn(nested_user.relative_to(root).as_posix(), explicit_consumers)
            self.assertIn(helper_user.relative_to(root).as_posix(), explicit_consumers)

    def test_top_level_modifiers_and_annotations_are_parsed(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            seed = write(
                root,
                "core/domain/src/commonMain/kotlin/demo/Types.kt",
                "package demo\n"
                "data class Row\n"
                "sealed class Shape\n"
                "enum class Kind\n"
                "private class Hidden\n"
                '@Deprecated("x")\n'
                "internal open class Annotated\n",
            )
            expected = [
                write(
                    root,
                    f"feature/demo/src/commonMain/kotlin/demo/{name}User.kt",
                    f"val value: {name}? = null\n",
                )
                for name in ("Row", "Shape", "Kind", "Hidden", "Annotated")
            ]

            result = run_cli(root, "--seed", seed.relative_to(root).as_posix())

            self.assertEqual(0, result.returncode, result.stderr)
            consumers = section(result.stdout, "Consumers")
            for path in expected:
                self.assertIn(path.relative_to(root).as_posix(), consumers)

    def test_comments_and_strings_do_not_contribute_symbols(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            seed = write(
                root,
                "core/domain/src/commonMain/kotlin/demo/Mixed.kt",
                '// interface Ignored\n'
                'val text = "class AlsoIgnored"\n'
                "class Real\n",
            )
            real_user = write(
                root,
                "feature/demo/src/commonMain/kotlin/demo/RealUser.kt",
                "val real: Real? = null\n",
            )
            ignored_user = write(
                root,
                "feature/demo/src/commonMain/kotlin/demo/IgnoredUser.kt",
                "val ignored: Ignored? = null\n",
            )

            result = run_cli(root, "--seed", seed.relative_to(root).as_posix())

            self.assertEqual(0, result.returncode, result.stderr)
            consumers = section(result.stdout, "Consumers")
            self.assertIn(real_user.relative_to(root).as_posix(), consumers)
            self.assertNotIn(ignored_user.relative_to(root).as_posix(), consumers)

    def test_invalid_input_returns_125(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            repo = root / "repo"
            seed = write(repo, "demo/Thing.kt", "interface Thing\n")
            outside = write(root, "outside.kt", "interface Outside\n")

            missing = run_cli(root, "--seed", "missing.kt")
            self.assertEqual(125, missing.returncode)
            self.assertIn("does not exist", missing.stderr)

            duplicate = run_cli(
                root,
                "--seed",
                seed.relative_to(root).as_posix(),
                "--seed",
                seed.relative_to(root).as_posix(),
            )
            self.assertEqual(125, duplicate.returncode)
            self.assertIn("duplicate seed", duplicate.stderr)

            outside_repo = subprocess.run(
                [
                    sys.executable,
                    str(CLI_PATH),
                    "--repo",
                    str(repo),
                    "--seed",
                    "../outside.kt",
                ],
                capture_output=True,
                text=True,
                check=False,
            )
            self.assertEqual(125, outside_repo.returncode)
            self.assertIn("outside the repository", outside_repo.stderr)

            absolute = run_cli(root, "--seed", str(seed))
            self.assertEqual(125, absolute.returncode)
            self.assertIn("repository-relative", absolute.stderr)

            no_seeds = run_cli(root)
            self.assertEqual(125, no_seeds.returncode)

            missing_repo = subprocess.run(
                [
                    sys.executable,
                    str(CLI_PATH),
                    "--repo",
                    str(root / "missing"),
                    "--seed",
                    "demo/Thing.kt",
                ],
                capture_output=True,
                text=True,
                check=False,
            )
            self.assertEqual(125, missing_repo.returncode)
            self.assertIn("repository directory does not exist", missing_repo.stderr)

    def test_missing_living_doc_declarations_warn_but_exit_zero(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            seed = write(
                root,
                "feature/demo/src/commonMain/kotlin/demo/Thing.kt",
                "interface Thing\n",
            )

            result = run_cli(root, "--seed", seed.relative_to(root).as_posix())

            self.assertEqual(0, result.returncode, result.stderr)
            self.assertIn("AGENTS.md is missing", result.stdout)
            self.assertIn("docs/doc-impact.json is missing", result.stdout)

    def test_runs_read_only(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            make_docs(root)
            seed = write(
                root,
                "feature/demo/src/commonMain/kotlin/demo/Thing.kt",
                "interface Thing\n",
            )
            write(
                root,
                "feature/demo/src/commonMain/kotlin/demo/ThingUser.kt",
                "val thing: Thing? = null\n",
            )

            def snapshot() -> dict[str, bytes]:
                return {
                    path.relative_to(root).as_posix(): path.read_bytes()
                    for path in sorted(root.rglob("*"))
                    if path.is_file()
                }

            before = snapshot()
            result = run_cli(root, "--seed", seed.relative_to(root).as_posix())
            after = snapshot()

            self.assertEqual(0, result.returncode, result.stderr)
            self.assertEqual(before, after)


if __name__ == "__main__":
    unittest.main()
