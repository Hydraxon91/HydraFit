"""Offline tests for scripts/flake-repeat.py.

Standard library only; every test drives the real wrapper and the real
`run-bounded.py` child in a temporary sweep directory, so no repository state,
emulator or external tool is touched.

Run: python3 scripts/tests/test_flake_repeat.py
"""

import contextlib
import importlib.util
import io
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest import mock

MODULE_PATH = Path(__file__).resolve().parents[1] / "flake-repeat.py"
# Loading the helper by path would otherwise drop a __pycache__ directory into scripts/.
sys.dont_write_bytecode = True
_spec = importlib.util.spec_from_file_location("flake_repeat", MODULE_PATH)
fr = importlib.util.module_from_spec(_spec)
assert _spec.loader is not None
sys.modules["flake_repeat"] = fr
_spec.loader.exec_module(fr)


class FlakeRepeatTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name).resolve()
        self.sweep = self.root / "sweep"

    def run_main(self, arguments):
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            code = fr.main(arguments)
        return code, out.getvalue(), err.getvalue()

    def sweep_args(self, runs, command):
        return ["30", str(runs), str(self.sweep), "--", *command]

    def summary(self):
        return json.loads((self.sweep / "summary.json").read_text())

    def test_passing_command_repeated_passes_and_persists_a_summary(self):
        code, out, _ = self.run_main(self.sweep_args(3, [sys.executable, "-c", "pass"]))
        self.assertEqual(0, code)
        self.assertIn("runs=3 attempted=3 failed=0 fresh=true", out)
        summary = self.summary()
        self.assertEqual(3, summary["runs"])
        self.assertEqual([], summary["failed_runs"])
        self.assertTrue(summary["fresh"])
        self.assertEqual([1, 2, 3], [record["run"] for record in summary["results"]])
        self.assertTrue((self.sweep / "run-2.log").is_file())

    def test_failing_command_reports_each_run_and_the_distinct_failure(self):
        command = [sys.executable, "-c",
                   "print('ExerciseProgressRecordsIntegrationTest > aCase FAILED'); "
                   "raise SystemExit(1)"]
        code, out, _ = self.run_main(self.sweep_args(2, command))
        self.assertEqual(2, code)
        self.assertIn("runs=2 attempted=2 failed=2", out)
        self.assertIn("aCase", out)
        summary = self.summary()
        self.assertEqual([1, 2], summary["failed_runs"])
        self.assertEqual(["ExerciseProgressRecordsIntegrationTest > aCase"],
                         summary["distinct_failures"])

    def test_gradle_sweep_that_executed_nothing_is_inconclusive(self):
        command = [sys.executable, "-c", "print('7 actionable tasks: 7 up-to-date')"]
        code, out, _ = self.run_main(self.sweep_args(2, command))
        self.assertEqual(3, code)
        self.assertIn("fresh=false", out)
        self.assertFalse(self.summary()["fresh"])
        self.assertEqual(0, self.summary()["results"][0]["executed"])

    def test_gradle_sweep_with_executed_work_is_fresh(self):
        command = [sys.executable, "-c", "print('7 actionable tasks: 7 executed')"]
        code, out, _ = self.run_main(self.sweep_args(2, command))
        self.assertEqual(0, code)
        self.assertIn("fresh=true", out)
        self.assertEqual(7, self.summary()["results"][1]["executed"])

    def test_unconfirmed_termination_stops_the_sweep_without_starting_more(self):
        records = [{"run": 1, "wrapper_exit": 0, "exit": 0, "outcome": "timeout",
                    "termination": "not-guaranteed", "executed": 1, "up_to_date": 0,
                    "log_match": None, "failures": []}]
        with mock.patch.object(fr, "run_once", side_effect=records) as run_once:
            code, out, _ = self.run_main(self.sweep_args(3, ["true"]))
        self.assertEqual(1, run_once.call_count, "no run may start after an uncertain one")
        self.assertEqual(3, code)
        self.assertIn("aborted_at=1", out)
        summary = self.summary()
        self.assertEqual(1, summary["runs_attempted"])
        self.assertFalse(summary["complete"])
        self.assertFalse(summary["fresh"])

    def test_missing_result_is_unconfirmed_and_aborts_the_sweep(self):
        records = [{"run": 1, "wrapper_exit": 125, "exit": None, "outcome": None,
                    "termination": None, "executed": None, "up_to_date": None,
                    "log_match": None, "failures": []}]
        with mock.patch.object(fr, "run_once", side_effect=records) as run_once:
            code, _, _ = self.run_main(self.sweep_args(2, ["true"]))
        self.assertEqual(1, run_once.call_count)
        self.assertEqual(2, code, "a wrapper failure is still a failed run")

    def test_one_fresh_run_with_cached_repeats_is_not_fresh(self):
        base = {"wrapper_exit": 0, "exit": 0, "outcome": "completed",
                "termination": "confirmed", "up_to_date": 0, "log_match": None,
                "failures": []}
        records = [{**base, "run": 1, "executed": 1}, {**base, "run": 2, "executed": 0}]
        with mock.patch.object(fr, "run_once", side_effect=records):
            code, out, _ = self.run_main(self.sweep_args(2, ["true"]))
        self.assertEqual(3, code)
        self.assertIn("fresh=false", out)
        self.assertTrue(self.summary()["complete"])

    def test_required_log_marker_must_appear_in_every_run(self):
        command = [sys.executable, "-c", "print('TARGET-CHECK-RAN')"]
        code, out, _ = self.run_main(
            ["--require-log-match", "TARGET-CHECK-RAN", *self.sweep_args(2, command)])
        self.assertEqual(0, code)
        self.assertIn("fresh=true", out)
        self.assertEqual("TARGET-CHECK-RAN", self.summary()["required_log_match"])
        self.assertTrue(all(record["log_match"] for record in self.summary()["results"]))

    def test_missing_required_log_marker_is_inconclusive(self):
        command = [sys.executable, "-c", "print('something unrelated')"]
        code, out, _ = self.run_main(
            ["--require-log-match", "TARGET-CHECK-RAN", *self.sweep_args(2, command)])
        self.assertEqual(3, code)
        self.assertIn("fresh=false", out)
        self.assertEqual([False, False],
                         [record["log_match"] for record in self.summary()["results"]])

    def test_invalid_required_log_match_regex_returns_125(self):
        code, _, err = self.run_main(
            ["--require-log-match", "[", *self.sweep_args(1, ["true"])])
        self.assertEqual(125, code)
        self.assertIn("not a valid regex", err)

    def test_target_task_marker_rejects_nonexecuted_task_statuses(self):
        for index, status in enumerate(("UP-TO-DATE", "FROM-CACHE", "SKIPPED", "NO-SOURCE")):
            with self.subTest(status=status):
                self.sweep = self.root / f"cached-{index}"
                output = (f"> Task :module:test {status}\n"
                          "2 actionable tasks: 1 executed, 1 up-to-date\n")
                command = [sys.executable, "-c", f"print({output!r})"]
                code, _, _ = self.run_main(
                    ["--require-log-match", ":module:test", *self.sweep_args(2, command)])
                self.assertEqual(3, code)
                self.assertFalse(self.summary()["fresh"])

    def test_target_task_marker_accepts_executed_task(self):
        command = [sys.executable, "-c",
                   "print('> Task :module:test\\n1 actionable task: 1 executed')"]
        code, _, _ = self.run_main(
            ["--require-log-match", ":module:test", *self.sweep_args(2, command)])
        self.assertEqual(0, code)

    def test_stale_sweep_directory_is_refused_without_running_anything(self):
        self.sweep.mkdir(parents=True)
        (self.sweep / "run-1.json").write_text('{"old":true}')
        code, _, err = self.run_main(self.sweep_args(2, [sys.executable, "-c", "pass"]))
        self.assertEqual(125, code)
        self.assertIn("already holds results", err)
        self.assertEqual(['{"old":true}'], [path.read_text() for path in self.sweep.glob("*.json")])
        self.assertEqual([], list(self.sweep.glob("run-*.log")))

    def test_usage_errors_return_125(self):
        cases = [["30", "2", str(self.sweep)],
                 ["30", "2", str(self.sweep), "--"],
                 ["nope", "2", str(self.sweep), "--", "true"],
                 ["30", "0", str(self.sweep), "--", "true"],
                 ["0", "2", str(self.sweep), "--", "true"],
                 ["--require-log-match"],
                 ["30", "2", str(self.root / "missing" / ".."), "--", "true"]]
        for arguments in cases:
            with self.subTest(arguments=arguments):
                code, _, err = self.run_main(arguments)
                self.assertEqual(125, code)
                self.assertIn("usage:", err)

    def test_sweep_path_may_not_be_a_symlink_or_file(self):
        target = self.root / "target"
        target.mkdir()
        link = self.root / "link"
        link.symlink_to(target, target_is_directory=True)
        code, _, err = self.run_main(["30", "1", str(link), "--", "true"])
        self.assertEqual(125, code)
        self.assertIn("not a symlink", err)
        plain = self.root / "plain.txt"
        plain.write_text("not a directory")
        self.assertEqual(125, self.run_main(["30", "1", str(plain), "--", "true"])[0])
        self.assertEqual([], list(target.iterdir()))


if __name__ == "__main__":
    unittest.main()
