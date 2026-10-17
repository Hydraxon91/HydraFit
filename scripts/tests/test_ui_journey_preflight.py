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
        self.flow.write_text("# synthetic fixture, never executed\n")
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

    def test_cli_invalid_input_and_findings_exits(self):
        path = self.root / "contract.json"
        for raw, age, expected in (("{broken", "30", 125), ("{}", "30", 2),
                                    ('{"version":1,"version":1}', "30", 125), ("{}", "nan", 125)):
            path.write_text(raw)
            with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
                self.assertEqual(expected, ui.main([str(path), "--max-age-seconds", age]))


if __name__ == "__main__":
    unittest.main()
