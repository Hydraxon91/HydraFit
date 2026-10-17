"""Offline guard-bridge integration and Node wrapper contracts; temporary repos only."""

import contextlib
import importlib.util
import io
import json
from pathlib import Path
import shutil
import subprocess
import sys
import unittest
from unittest import mock

import test_task_state as fixtures

sys.dont_write_bytecode = True
SCRIPT = Path(__file__).resolve().parents[1] / "task-guard-state.py"
SPEC = importlib.util.spec_from_file_location("task_guard_bridge", SCRIPT)
bridge = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = bridge
SPEC.loader.exec_module(bridge)


class TaskGuardTests(unittest.TestCase):
    def setUp(self):
        self.fixture = fixtures.TaskStateTests()
        self.fixture.setUp()
        self.addCleanup(self.fixture.doCleanups)
        self.assertEqual(0, self.fixture.init()[0])
        self.binding = {"stateDir": str(self.fixture.state), "repo": str(self.fixture.repo), "task": "test"}

    def request(self, action, **extra):
        return bridge.handle({**self.binding, "action": action, **extra})

    def test_lifecycle_and_duplicate_admission(self):
        self.assertTrue(self.request("inspect")["ok"])
        self.request("begin", operation="call-1", tool="read")
        self.assertEqual("pending", self.fixture.record()["operations"]["call-1"]["outcome"])
        with self.assertRaises(bridge.state.Conflict):
            self.request("inspect")
        self.assertTrue(self.request("inspect", operation="call-1")["ok"])
        self.request("complete", operation="call-1")
        self.assertEqual("completed", self.fixture.record()["operations"]["call-1"]["outcome"])
        with self.assertRaises(bridge.state.Conflict):
            self.request("begin", operation="call-1", tool="read")

    def test_foreign_pending_and_forged_completion_refused(self):
        self.request("begin", operation="first", tool="read")
        before = self.fixture.record_path.read_bytes()
        for action in ("begin", "inspect", "complete"):
            with self.subTest(action=action), self.assertRaises(bridge.state.Conflict):
                self.request(action, operation="other", tool="read")
        self.assertEqual(before, self.fixture.record_path.read_bytes())

    def test_error_or_restart_keeps_unknown_until_explicit_reconciliation(self):
        self.request("begin", operation="lost", tool="read")
        # A new bridge process has no admitted token and must see an unknown outcome.
        payload = json.dumps({**self.binding, "action": "inspect"})
        result = subprocess.run([sys.executable, "-B", str(SCRIPT)], input=payload,
                                text=True, capture_output=True, timeout=20)
        self.assertEqual(2, result.returncode)
        self.assertIn("unknown operation", result.stdout)
        self.assertEqual("pending", self.fixture.record()["operations"]["lost"]["outcome"])

    def test_budget_expired_blocks_admission_but_records_observed_completion(self):
        record = self.fixture.record()
        with mock.patch.object(bridge.time, "time", return_value=record["accounted_at"] + 1):
            self.request("begin", operation="read-1", tool="read")
        with mock.patch.object(bridge.time, "time", return_value=record["accounted_at"] + 1000):
            with self.assertRaises(bridge.state.Conflict):
                self.request("inspect", operation="read-1")
            self.request("complete", operation="read-1")
            with self.assertRaises(bridge.state.Conflict):
                self.request("begin", operation="read-2", tool="read")

    def test_wrong_repo_and_repository_drift_refused(self):
        wrong = {**self.binding, "repo": str(self.fixture.root), "action": "inspect"}
        with self.assertRaises(bridge.state.Conflict):
            bridge.handle(wrong)
        (self.fixture.repo / "file.txt").write_text("unexpected drift")
        with self.assertRaises(bridge.state.Conflict):
            self.request("begin", operation="read-1", tool="read")

    def test_corrupt_missing_and_unsupported_state_fail_closed(self):
        record = self.fixture.record()
        for data in ("broken", json.dumps({**record, "version": 999})):
            self.fixture.record_path.write_text(data)
            with self.assertRaises(bridge.state.Invalid):
                self.request("begin", operation="read-1", tool="read")
            self.assertEqual(data, self.fixture.record_path.read_text())
        self.fixture.record_path.unlink()
        with self.assertRaises(OSError):
            self.request("inspect")

    def test_bridge_stdin_protocol(self):
        for value in ("broken", '[]', '{"action":"inspect","action":"begin"}'):
            with self.subTest(value=value):
                out = io.StringIO()
                with mock.patch.object(sys, "stdin", io.StringIO(value)), contextlib.redirect_stdout(out):
                    self.assertEqual(125, bridge.main())
                self.assertFalse(json.loads(out.getvalue())["ok"])

    def test_node_wrapper_contracts(self):
        node = shutil.which("node")
        self.assertIsNotNone(node, "Node is required for the opt-in guard's offline checks")
        result = subprocess.run([node, str(Path(__file__).with_name("task_guard_cases.mjs"))],
                                capture_output=True, text=True, timeout=30)
        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertIn("wrapper contracts passed", result.stdout)


if __name__ == "__main__":
    unittest.main()
