"""Synthetic handoff contracts; no ADB, Maestro or app data access."""
import contextlib
import copy
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock

sys.dont_write_bytecode = True
SPEC = importlib.util.spec_from_file_location("ui_preflight", Path(__file__).parents[1] / "ui-journey-preflight.py")
ui = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(ui)


class PreflightTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name).resolve()
        self.evidence = self.root / "baseline.json"
        self.evidence.write_text('{"sessions":8}')
        self.flow = self.root / "flow.yaml"
        self.flow.write_text("# synthetic fixture, never executed\n- takeScreenshot: settings\n")
        def reference(path):
            return {"path": str(path), "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
        self.contract = {
            "version": 1, "device": "emulator-5554", "build": "debug-fixture",
            "starting_state": "Settings, no draft", "approval_reference": "synthetic contract",
            "allowed_interactions": "navigation only", "prior_writes": "none", "planned_writes": [],
            "unknown_writes": [], "safeguard": "none", "cleanup": "none", "restoration": "none",
            "screenshot_checkpoints": "Settings after display assertions",
            "baseline": {"observed_at": 100, "artifact": reference(self.evidence), "facts": {"sessions": 8}},
            "fixture_expectations": {"sessions": 8},
            "budgets": {"exploration": 60, "execution": 120}, "flows": [reference(self.flow)],
            "assertions": [{"kind": "durable", "expected": "8 sessions", "downstream_check": "history count"}],
            "destructive_categories": []}

    def check(self, contract=None, now=105):
        return ui.findings(self.contract if contract is None else contract, 30, now)

    def v2(self, **changes):
        contract = copy.deepcopy(self.contract)
        contract.update(version=2, screenshot_evidence=["settings"])
        contract.update(changes)
        return contract

    def rehash_flow(self):
        self.contract["flows"][0]["sha256"] = hashlib.sha256(self.flow.read_bytes()).hexdigest()

    def test_valid_and_read_only(self):
        before = {p: p.read_bytes() for p in self.root.iterdir()}
        self.assertEqual([], self.check())
        self.assertEqual(before, {p: p.read_bytes() for p in self.root.iterdir()})

    def test_every_required_field_missing_is_rejected(self):
        for key in self.contract:
            with self.subTest(key=key):
                value = copy.deepcopy(self.contract)
                del value[key]
                self.assertTrue(self.check(value))

    def test_stale_drifted_and_future_baseline(self):
        self.assertTrue(self.check(now=131))
        self.assertTrue(self.check(now=99))
        self.evidence.write_text('{"sessions":10}')
        self.assertTrue(self.check(), "changed baseline artifact invalidates handoff")

    def test_changed_flow_invalidates_handoff(self):
        self.flow.write_text("# changed flow\n")
        self.assertTrue(self.check())

    def test_wrong_count_assertion_is_rejected(self):
        self.contract["fixture_expectations"]["sessions"] = 9
        self.assertTrue(self.check())

    def test_boolean_is_not_numeric_fact_including_nested_values(self):
        for actual, expected in ((True, 1), (False, 0), ({"x": [True]}, {"x": [1]}),
                                  ([{"x": 0}], [{"x": False}])):
            with self.subTest(actual=actual, expected=expected):
                self.contract["baseline"]["facts"] = {"value": actual}
                self.contract["fixture_expectations"] = {"value": expected}
                self.assertTrue(self.check())
        self.assertTrue(ui.same_fact({"x": [1]}, {"x": [1.0]}))
        self.assertTrue(ui.same_fact(True, True))

    def test_runner_distinguishes_prepared_and_inspection_only_contracts(self):
        root = Path(__file__).resolve().parents[2]
        runner = (root / ".opencode/agents/ui-runner.md").read_text()
        command = (root / ".opencode/commands/ui-check.md").read_text()
        self.assertIn("For prepared acceptance journeys, also require", runner)
        self.assertIn("Inspection-only requests need a bounded read-only brief", runner)
        self.assertIn("Inspection-only", command)

    def test_unknown_prior_write_blocks_new_write_not_inspection(self):
        self.contract["unknown_writes"] = ["uncertain save"]
        self.assertEqual([], self.check())
        self.contract["planned_writes"] = ["reset"]
        self.assertTrue(self.check())

    def test_destructive_execution_requires_complete_recovery_contract(self):
        category = {"name": "training setup", "coverage": "execution-planned", "reason": "test reset",
                    "write": "reset", "authorization_reference": "approved synthetic mutation",
                    "safeguard": "verified backup fixture", "recovery": "restore then open Equipment",
                    "postcondition": "default equipment profile exists"}
        self.contract["planned_writes"] = ["reset"]
        self.contract["destructive_categories"] = [category]
        self.assertEqual([], self.check())
        for key in category:
            value = copy.deepcopy(self.contract)
            del value["destructive_categories"][0][key]
            self.assertTrue(self.check(value), key)

    def test_skipped_reset_is_not_executed_coverage(self):
        self.contract["destructive_categories"] = [{"name": "all data", "coverage": "gating-only",
                                                    "reason": "execution not approved"}]
        self.assertEqual([], self.check())
        self.contract["destructive_categories"][0]["coverage"] = "executed"
        self.assertTrue(self.check())

    def test_transient_feedback_is_not_a_durable_assertion(self):
        self.contract["assertions"][0]["kind"] = "toast"
        self.assertTrue(self.check())

    def test_v1_remains_valid_without_screenshot_evidence(self):
        self.assertNotIn("screenshot_evidence", self.contract)
        self.assertEqual([], self.check())

    def test_v2_requires_screenshot_evidence(self):
        contract = self.v2()
        del contract["screenshot_evidence"]
        self.assertTrue(self.check(contract))

    def test_v2_declared_screenshots_must_be_in_a_flow(self):
        self.assertEqual([], self.check(self.v2()))
        self.flow.write_text("- takeScreenshot: other\n")
        self.rehash_flow()
        self.assertTrue(self.check(self.v2()))

    def test_screenshot_scalar_quoted_and_inline_path_forms(self):
        self.flow.write_text('- takeScreenshot: "first"\n- assertScreenshot: { path: second }\n')
        self.rehash_flow()
        self.assertEqual([], self.check(self.v2(screenshot_evidence=["first", "second"])))

    def test_screenshot_none_requires_none_checkpoints(self):
        contract = self.v2(screenshot_evidence="none", screenshot_checkpoints=" NONE ")
        self.assertEqual([], self.check(contract))
        contract["screenshot_checkpoints"] = "Settings after assertions"
        self.assertTrue(self.check(contract))

    def test_screenshot_names_must_be_nonempty_unique_strings(self):
        for evidence in (["settings", "settings"], ["settings", ""], ["settings", 3], []):
            with self.subTest(evidence=evidence):
                self.assertTrue(self.check(self.v2(screenshot_evidence=evidence)))
        self.assertTrue(self.check(self.v2(screenshot_evidence="screenshots")))

    def test_cli_json_reports_validated_version(self):
        path = self.root / "contract.json"
        for version, evidence in ((1, None), (2, ["settings"])):
            contract = copy.deepcopy(self.contract)
            contract["version"] = version
            if evidence is not None:
                contract["screenshot_evidence"] = evidence
            path.write_text(json.dumps(contract))
            output = io.StringIO()
            with mock.patch.object(ui.time, "time", return_value=105):
                with contextlib.redirect_stdout(output), contextlib.redirect_stderr(io.StringIO()):
                    self.assertEqual(0, ui.main([str(path), "--max-age-seconds", "30"]))
            self.assertEqual(version, json.loads(output.getvalue())["version"])

    def test_brief_is_deterministic_read_only_and_declares_no_approval(self):
        path = self.root / "contract.json"
        path.write_text(json.dumps(self.v2()))
        before = {item: item.read_bytes() for item in self.root.iterdir()}
        outputs = []
        for _ in range(2):
            output = io.StringIO()
            with mock.patch.object(ui.time, "time", return_value=105):
                with contextlib.redirect_stdout(output), contextlib.redirect_stderr(io.StringIO()):
                    self.assertEqual(0, ui.main([str(path), "--max-age-seconds", "30", "--brief"]))
            outputs.append(output.getvalue())
        self.assertEqual(outputs[0], outputs[1])
        self.assertEqual(before, {item: item.read_bytes() for item in self.root.iterdir()})
        for expected in ("Status: clear", "emulator-5554", "flow.yaml", "Screenshot evidence: [\"settings\"]",
                         "not approval"):
            self.assertIn(expected, outputs[0])
        self.assertNotIn("preflight_clear", outputs[0])

    def test_brief_lists_findings(self):
        path = self.root / "contract.json"
        path.write_text(json.dumps(self.v2(screenshot_evidence=["missing"])))
        output = io.StringIO()
        with mock.patch.object(ui.time, "time", return_value=105):
            with contextlib.redirect_stdout(output), contextlib.redirect_stderr(io.StringIO()):
                self.assertEqual(2, ui.main([str(path), "--max-age-seconds", "30", "--brief"]))
        self.assertIn("## Findings", output.getvalue())
        self.assertIn("missing", output.getvalue())

    def test_cli_invalid_input_and_findings_exits(self):
        path = self.root / "contract.json"
        for raw, age, expected in (("{broken", "30", 125), ("{}", "30", 2),
                                    ('{"version":1,"version":1}', "30", 125), ("{}", "nan", 125)):
            path.write_text(raw)
            with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
                self.assertEqual(expected, ui.main([str(path), "--max-age-seconds", age]))


if __name__ == "__main__":
    unittest.main()
