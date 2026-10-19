"""Offline tests for scripts/validate-agent-tooling.py.

Standard library only; every test builds a throwaway tree under a temporary
directory, so repository state, the emulator and external tools are untouched.

Run: python3 scripts/tests/test_validate_tooling.py
"""

import contextlib
import importlib.util
import io
import pathlib
import sys
import tempfile
import unittest

MODULE_PATH = pathlib.Path(__file__).resolve().parents[1] / "validate-agent-tooling.py"
# Loading the helper by path would otherwise drop a __pycache__ directory into scripts/.
sys.dont_write_bytecode = True
_spec = importlib.util.spec_from_file_location("validate_agent_tooling", MODULE_PATH)
vt = importlib.util.module_from_spec(_spec)
assert _spec.loader is not None
sys.modules["validate_agent_tooling"] = vt
_spec.loader.exec_module(vt)

VALID_AGENT = """---
description: Does a bounded thing
mode: subagent
permissions:
  - action: read
    resource: "*"
    effect: allow
---

Body text.
"""

VALID_CODER_WORKFLOW = """---
description: Implements a bounded chunk
mode: subagent
permissions:
  - action: read
    resource: "*"
    effect: allow
---

Intro text.

### Coder workflow

- **Style sources:** follow `.editorconfig` with `ktlint_code_style = android_studio`,
  which defaults the limit to 100 columns.
- **Lint first:** run ktlint before the heavier test task; agent tooling adds no Gradle tasks.
- **Consumer map:** trace producer to consumers and their tests; check scheduler/clock.
- **Structured report:** classify each correction round and record elapsed and remaining.
"""


def write(root: pathlib.Path, relative: str, text: str) -> pathlib.Path:
    path = root / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text)
    return path


class DefinitionTests(unittest.TestCase):
    def test_valid_agent_passes(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = write(pathlib.Path(tmp), ".opencode/agents/worker.md", VALID_AGENT)
            self.assertEqual([], vt.definition_findings(path, "agents"))

    def test_missing_delimiters_is_reported(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = write(pathlib.Path(tmp), ".opencode/agents/worker.md", "description: x\n")
            findings = vt.definition_findings(path, "agents")
            self.assertEqual(1, len(findings))
            self.assertIn("missing frontmatter delimiters", findings[0])

    def test_missing_mode_is_reported(self):
        with tempfile.TemporaryDirectory() as tmp:
            text = VALID_AGENT.replace("mode: subagent\n", "")
            path = write(pathlib.Path(tmp), ".opencode/agents/worker.md", text)
            findings = vt.definition_findings(path, "agents")
            self.assertIn("missing required key 'mode'", findings[0])

    def test_empty_description_is_reported(self):
        with tempfile.TemporaryDirectory() as tmp:
            text = VALID_AGENT.replace("description: Does a bounded thing", "description:")
            path = write(pathlib.Path(tmp), ".opencode/agents/worker.md", text)
            findings = vt.definition_findings(path, "agents")
            self.assertIn("required key 'description' is empty", findings[0])

    def test_bad_effect_is_reported(self):
        with tempfile.TemporaryDirectory() as tmp:
            text = VALID_AGENT.replace("effect: allow", "effect: maybe")
            path = write(pathlib.Path(tmp), ".opencode/agents/worker.md", text)
            findings = vt.definition_findings(path, "agents")
            self.assertEqual(1, len(findings))
            self.assertIn("effect 'maybe'", findings[0])

    def test_missing_rule_field_is_reported(self):
        with tempfile.TemporaryDirectory() as tmp:
            text = VALID_AGENT.replace('    resource: "*"\n', "")
            path = write(pathlib.Path(tmp), ".opencode/agents/worker.md", text)
            findings = vt.definition_findings(path, "agents")
            self.assertIn("permission rule 1 missing resource", findings[0])

    def test_comments_inside_the_permissions_list_are_skipped(self):
        with tempfile.TemporaryDirectory() as tmp:
            text = VALID_AGENT.replace(
                "permissions:\n",
                "permissions:\n  # - action: deny inside a comment\n",
            )
            path = write(pathlib.Path(tmp), ".opencode/agents/worker.md", text)
            self.assertEqual([], vt.definition_findings(path, "agents"))
            frontmatter = vt.read_frontmatter(path)
            self.assertEqual(1, len(vt.permission_rules(frontmatter)))

    def test_inline_comments_and_quotes_are_accepted_in_effects(self):
        with tempfile.TemporaryDirectory() as tmp:
            for effect in ("allow # explained", '"deny"', "'ask'"):
                with self.subTest(effect=effect):
                    text = VALID_AGENT.replace("effect: allow", f"effect: {effect}")
                    path = write(pathlib.Path(tmp), ".opencode/agents/worker.md", text)
                    self.assertEqual([], vt.definition_findings(path, "agents"))

    def test_commented_problem_value_is_still_reported(self):
        with tempfile.TemporaryDirectory() as tmp:
            text = VALID_AGENT.replace("effect: allow", "effect: maybe # note")
            path = write(pathlib.Path(tmp), ".opencode/agents/worker.md", text)
            findings = vt.definition_findings(path, "agents")
            self.assertEqual(1, len(findings))
            self.assertIn("effect 'maybe'", findings[0])

    def test_command_and_skill_require_description(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            command = write(root, ".opencode/commands/run.md", "---\nsubagent: false\n---\n")
            skill = write(root, ".opencode/skills/demo/SKILL.md", "---\nname: demo\n---\n")
            self.assertIn("missing required key 'description'", vt.definition_findings(command, "commands")[0])
            self.assertIn("missing required key 'description'", vt.definition_findings(skill, "skills")[0])


class ReferenceTests(unittest.TestCase):
    def _root_with_agents_md(self, body: str) -> tuple[pathlib.Path, pathlib.Path]:
        root = pathlib.Path(tempfile.mkdtemp())
        self.addCleanup(lambda: __import__("shutil").rmtree(root))
        agents = write(root, "AGENTS.md", body)
        return root, agents

    def test_existing_root_relative_reference_passes(self):
        root, agents = self._root_with_agents_md("See `docs/notes.md`.\n")
        write(root, "docs/notes.md", "notes\n")
        self.assertEqual([], vt.reference_findings(root, agents))

    def test_dangling_root_relative_reference_is_reported(self):
        root, agents = self._root_with_agents_md("See `docs/missing.md`.\n")
        findings = vt.reference_findings(root, agents)
        self.assertEqual(1, len(findings))
        self.assertIn("dangling reference to docs/missing.md", findings[0])

    def test_dangling_markdown_link_is_reported(self):
        root, agents = self._root_with_agents_md("See [notes](docs/missing.md).\n")
        findings = vt.reference_findings(root, agents)
        self.assertEqual(1, len(findings))
        self.assertIn("dangling link to docs/missing.md", findings[0])

    def test_markdown_link_anchors_and_titles_are_checked(self):
        root, agents = self._root_with_agents_md(
            "See [a](docs/notes.md#section) and [b](docs/notes.md \"Title\").\n"
        )
        write(root, "docs/notes.md", "notes\n")
        self.assertEqual([], vt.reference_findings(root, agents))
        broken = write(root, "AGENTS.md", "See [a](docs/missing.md#section).\n")
        findings = vt.reference_findings(root, broken)
        self.assertEqual(1, len(findings))
        self.assertIn("dangling link to docs/missing.md", findings[0])

    def test_gradle_and_iosapp_prefixes_are_checked(self):
        root, agents = self._root_with_agents_md("See `gradle/libs.versions.toml`.\n")
        write(root, "gradle/libs.versions.toml", "x\n")
        self.assertEqual([], vt.reference_findings(root, agents))
        broken = write(root, "AGENTS.md", "See `iosApp/missing.md`.\n")
        findings = vt.reference_findings(root, broken)
        self.assertEqual(1, len(findings))
        self.assertIn("dangling reference to iosApp/missing.md", findings[0])

    def test_skill_relative_reference_is_checked(self):
        root = pathlib.Path(tempfile.mkdtemp())
        self.addCleanup(lambda: __import__("shutil").rmtree(root))
        skill = write(root, ".opencode/skills/demo/SKILL.md", "See `references/topic.md`.\n")
        self.assertEqual(1, len(vt.reference_findings(root, skill)))
        write(root, ".opencode/skills/demo/references/topic.md", "topic\n")
        self.assertEqual([], vt.reference_findings(root, skill))

    def test_urls_and_unknown_forms_are_ignored(self):
        root, agents = self._root_with_agents_md(
            "See https://example.com/docs/x.md and `settings.gradle.kts`.\n"
        )
        self.assertEqual([], vt.reference_findings(root, agents))


class CoderWorkflowTests(unittest.TestCase):
    def _root_with_coder(self, body: str) -> pathlib.Path:
        root = pathlib.Path(tempfile.mkdtemp())
        self.addCleanup(lambda: __import__("shutil").rmtree(root))
        write(root, ".opencode/agents/coder.md", body)
        return root

    def test_complete_coder_workflow_passes(self):
        root = self._root_with_coder(VALID_CODER_WORKFLOW)
        self.assertEqual([], vt.coder_workflow_findings(root))

    def test_absent_coder_agent_is_not_a_finding(self):
        with tempfile.TemporaryDirectory() as tmp:
            self.assertEqual([], vt.coder_workflow_findings(pathlib.Path(tmp)))

    def test_missing_section_is_reported(self):
        root = self._root_with_coder(VALID_CODER_WORKFLOW.replace("### Coder workflow", "### Notes"))
        self.assertIn("missing coder workflow section", vt.coder_workflow_findings(root)[0])

    def test_each_required_step_removal_is_reported(self):
        for step in vt.CODER_WORKFLOW_STEPS:
            with self.subTest(step=step):
                body = VALID_CODER_WORKFLOW.replace(f"- **{step}:**", f"- **Omitted {step}:**")
                root = self._root_with_coder(body)
                self.assertTrue(any(f"step {step}" in item for item in vt.coder_workflow_findings(root)))

    def test_each_required_token_removal_is_reported(self):
        for token in vt.CODER_WORKFLOW_TOKENS:
            with self.subTest(token=token):
                root = self._root_with_coder(VALID_CODER_WORKFLOW.replace(token, ""))
                self.assertTrue(
                    any(f"missing protocol reference {token}" in item
                        for item in vt.coder_workflow_findings(root))
                )

    def test_no_consumer_justification_is_declaration_only(self):
        # Structural coverage, not runtime compliance: a brief that justifies an
        # absent consumer and one that simply omits the justification both pass as
        # long as the required labels and protocol tokens are present.
        justified = VALID_CODER_WORKFLOW.replace(
            "trace producer to consumers and their tests; check scheduler/clock.",
            "no consumers or checks exist; producer-side only; scheduler/clock N/A.",
        )
        self.assertEqual([], vt.coder_workflow_findings(self._root_with_coder(justified)))
        self.assertEqual([], vt.coder_workflow_findings(self._root_with_coder(VALID_CODER_WORKFLOW)))


class AcceptanceWorkflowTests(unittest.TestCase):
    """Mutation scenarios check declaration loss, not agent behavior or real approval."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = pathlib.Path(self.tmp.name)
        self.body = (MODULE_PATH.parents[1] / "docs/agent-project-reference.md").read_text()
        self.path = write(self.root, "docs/agent-project-reference.md", self.body)

    def test_complete_declarations_pass(self):
        self.assertEqual([], vt.acceptance_workflow_findings(self.root))

    def test_missing_procedure_is_reported(self):
        self.path.write_text(self.body.replace(vt.ACCEPTANCE_HEADING, "### Removed"))
        self.assertIn("missing acceptance and authorization", vt.acceptance_workflow_findings(self.root)[0])

    def test_failure_scenarios_require_their_declarations(self):
        scenarios = {
            "builder mistaken for connected feature": "Consumer evidence",
            "guard mistaken for reproduced regression": "Regression evidence",
            "reviewer silently waives missing check": "Missing verification",
            "full scope mistaken for behavioral decision": "Design decisions",
            "approval reference laundered as consent": "Record consistency",
            "earlier publish instruction treated as report approval": "Publication",
        }
        for scenario, label in scenarios.items():
            with self.subTest(scenario=scenario):
                self.path.write_text(self.body.replace(f"- **{label}:**", f"- **Omitted {label}:**"))
                self.assertTrue(any(f"step {label}" in finding
                                    for finding in vt.acceptance_workflow_findings(self.root)))

    def test_commands_and_agents_reference_the_canonical_procedure(self):
        for relative in ("AGENTS.md", ".opencode/commands/task.md", ".opencode/commands/batch.md"):
            with self.subTest(path=relative):
                consumer = write(self.root, relative, "No reference here")
                self.assertTrue(any(str(consumer) in finding
                                    for finding in vt.acceptance_workflow_findings(self.root)))
                consumer.write_text("Acceptance\n  and authorization evidence")
                self.assertEqual([], vt.acceptance_workflow_findings(self.root))

    def test_labels_do_not_authenticate_consent_or_validate_coverage(self):
        # Arbitrary content with the required labels still passes: this intentionally
        # demonstrates the boundary instead of claiming semantic/runtime enforcement.
        self.path.write_text(vt.ACCEPTANCE_HEADING + "\n" + "\n".join(
            f"- **{label}:** operator claim only" for label in vt.ACCEPTANCE_STEPS))
        self.assertEqual([], vt.acceptance_workflow_findings(self.root))


class FormatPassTests(unittest.TestCase):
    """Mutation scenarios check declaration loss, not which paths a format pass rewrote."""

    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = pathlib.Path(self.tmp.name)
        source = MODULE_PATH.parents[1]
        self.reference = (source / "docs/agent-project-reference.md").read_text()
        self.agents = (source / "AGENTS.md").read_text()
        self.path = write(self.root, "docs/agent-project-reference.md", self.reference)
        write(self.root, "AGENTS.md", self.agents)

    def test_complete_declarations_pass(self):
        self.assertEqual([], vt.format_pass_findings(self.root))

    def test_missing_procedure_is_reported(self):
        self.path.write_text(self.reference.replace(vt.FORMAT_PASS_HEADING, "### Removed"))
        self.assertIn("missing scoped Kotlin format pass", vt.format_pass_findings(self.root)[0])

    def test_each_required_step_removal_is_reported(self):
        for step in vt.FORMAT_PASS_STEPS:
            with self.subTest(step=step):
                self.path.write_text(self.reference.replace(f"- **{step}:**", f"- **Omitted {step}:**"))
                self.assertTrue(any(f"step {step}" in item for item in vt.format_pass_findings(self.root)))

    def test_agents_md_references_the_canonical_procedure(self):
        write(self.root, "AGENTS.md", "No reference here")
        self.assertTrue(any("scoped format pass procedure reference" in item
                            for item in vt.format_pass_findings(self.root)))
        write(self.root, "AGENTS.md", "Scoped Kotlin format\npass")
        self.assertEqual([], vt.format_pass_findings(self.root))

    def test_labels_do_not_prove_scope_or_validate_ownership(self):
        # Arbitrary content carrying the required labels still passes: this
        # demonstrates the structural boundary instead of claiming enforcement.
        self.path.write_text(vt.FORMAT_PASS_HEADING + "\n" + "\n".join(
            f"- **{label}:** operator claim only" for label in vt.FORMAT_PASS_STEPS))
        self.assertEqual([], vt.format_pass_findings(self.root))


class MainTests(unittest.TestCase):
    def test_batch_lifecycle_structure_and_missing_steps_are_checked(self):
        source = MODULE_PATH.parents[1] / ".opencode/commands/batch.md"
        body = source.read_text()
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            path = write(root, ".opencode/commands/batch.md", body)
            self.assertEqual([], vt.batch_state_findings(root))
            for label in ("Initialize", "Attempt", "Resume", "Boundary", "Review", "Budget stop", "Publication"):
                with self.subTest(label=label):
                    path.write_text(body.replace(f"- **{label}:**", f"- **Omitted {label}:**"))
                    self.assertTrue(any(f"step {label}" in item for item in vt.batch_state_findings(root)))
            path.write_text(body.replace("boundary --expect", "boundary without revision"))
            self.assertTrue(any("boundary --expect" in item for item in vt.batch_state_findings(root)))
            path.write_text(body.replace("### Durable batch state", "### Removed section"))
            self.assertIn("missing durable batch state section", vt.batch_state_findings(root)[0])

    def test_each_durable_state_token_removal_is_reported(self):
        source = MODULE_PATH.parents[1] / ".opencode/commands/batch.md"
        body = source.read_text()
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            path = write(root, ".opencode/commands/batch.md", body)
            self.assertEqual([], vt.batch_state_findings(root))
            for token in ("scripts/task-state.py", "`amend`", "`extend`", "`checkpoint`", "`resume`",
                          "boundary --expect", "--require-evidence"):
                with self.subTest(token=token):
                    path.write_text(body.replace(token, "removed reference"))
                    self.assertTrue(any(f"missing protocol reference {token}" in item
                                        for item in vt.batch_state_findings(root)))

    def test_main_rejects_batch_without_state_protocol(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            write(root, ".opencode/commands/batch.md", "---\ndescription: batch\n---\nNo protocol\n")
            with contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(1, vt.main([str(root)]))

    def test_main_passes_a_clean_tree(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            write(root, "AGENTS.md", "See `docs/notes.md`.\n")
            write(root, "docs/notes.md", "notes\n")
            write(root, ".opencode/agents/worker.md", VALID_AGENT)
            with contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(0, vt.main([str(root)]))

    def test_main_reports_findings_for_a_broken_tree(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            write(root, "AGENTS.md", "See `docs/missing.md`.\n")
            output = io.StringIO()
            with contextlib.redirect_stdout(output):
                self.assertEqual(1, vt.main([str(root)]))
            self.assertIn("dangling reference", output.getvalue())

    def test_main_reports_missing_coder_workflow_section(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = pathlib.Path(tmp)
            write(root, "AGENTS.md", "text\n")
            write(root, ".opencode/agents/coder.md", VALID_AGENT)
            output = io.StringIO()
            with contextlib.redirect_stdout(output):
                self.assertEqual(1, vt.main([str(root)]))
            self.assertIn("missing coder workflow section", output.getvalue())


if __name__ == "__main__":
    unittest.main()
