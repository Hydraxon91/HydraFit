"""Offline tests for scripts/run-bounded.py.

Standard library only; every test uses a throwaway temporary directory and
short-lived child processes, so no emulator, network or Gradle run is involved.

Run: python3 -B -m unittest discover -s scripts/tests -p 'test_*.py'
"""

import contextlib
import importlib.util
import io
import json
import os
import pathlib
import signal
import subprocess
import sys
import tempfile
import time
import unittest
from unittest import mock

MODULE_PATH = pathlib.Path(__file__).resolve().parents[1] / "run-bounded.py"
# Loading the helper by path would otherwise drop a __pycache__ directory into scripts/.
sys.dont_write_bytecode = True
_spec = importlib.util.spec_from_file_location("run_bounded", MODULE_PATH)
rb = importlib.util.module_from_spec(_spec)
assert _spec.loader is not None
sys.modules["run_bounded"] = rb
_spec.loader.exec_module(rb)

SLEEP_FOREVER = "import os, sys, time; open(sys.argv[1], 'w').write(str(os.getpid())); time.sleep(30)"
IGNORE_TERM_AND_SLEEP = (
    "import os, signal, sys, time; "
    "open(sys.argv[1], 'w').write(str(os.getpid())); "
    "signal.signal(signal.SIGTERM, signal.SIG_IGN); "
    "time.sleep(30)"
)


def pid_is_gone(pid: int, deadline_seconds: float = 2.0) -> bool:
    """Poll until the pid disappears, or the deadline passes."""
    deadline = time.monotonic() + deadline_seconds
    while time.monotonic() < deadline:
        try:
            os.kill(pid, 0)
        except ProcessLookupError:
            return True
        time.sleep(0.02)
    return False


class RunBoundedTests(unittest.TestCase):
    def setUp(self):
        self.tmp = pathlib.Path(tempfile.mkdtemp())
        self.addCleanup(lambda: __import__("shutil").rmtree(self.tmp, ignore_errors=True))
        self.original_grace = rb.TERM_GRACE_SECONDS
        self.addCleanup(setattr, rb, "TERM_GRACE_SECONDS", self.original_grace)

    def run_main(self, arguments):
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            code = rb.main(arguments)
        return code, out.getvalue(), err.getvalue()

    def test_success_returns_zero_and_writes_log(self):
        log = self.tmp / "ok.log"
        code, out, _ = self.run_main(
            ["5", str(log), "--", sys.executable, "-c", "print('hello bounded')"]
        )
        self.assertEqual(0, code)
        self.assertIn("hello bounded", log.read_text())
        self.assertIn("outcome=completed", out)

    def test_nonzero_child_code_is_propagated(self):
        log = self.tmp / "fail.log"
        code, _, _ = self.run_main(
            ["5", str(log), "--", sys.executable, "-c", "import sys; sys.exit(7)"]
        )
        self.assertEqual(7, code)

    def test_timeout_returns_124_marks_log_and_kills_child(self):
        rb.TERM_GRACE_SECONDS = 0.5
        log = self.tmp / "timeout.log"
        pid_file = self.tmp / "sleeping.pid"
        code, out, _ = self.run_main(
            ["0.4", str(log), "--", sys.executable, "-c", SLEEP_FOREVER, str(pid_file)]
        )
        self.assertEqual(124, code)
        text = log.read_text()
        self.assertIn("TIMEOUT after", text)
        self.assertIn("SIGTERM", text)
        self.assertIn("outcome=timeout", out)
        pid = int(pid_file.read_text())
        self.assertTrue(pid_is_gone(pid), "child pid still alive after timeout")

    def test_sigkill_when_child_ignores_sigterm(self):
        rb.TERM_GRACE_SECONDS = 0.3
        log = self.tmp / "stubborn.log"
        pid_file = self.tmp / "stubborn.pid"
        code, out, _ = self.run_main(
            ["0.4", str(log), "--", sys.executable, "-c", IGNORE_TERM_AND_SLEEP, str(pid_file)]
        )
        self.assertEqual(124, code)
        self.assertIn("SIGKILL", log.read_text())
        self.assertIn("sigkill=True", out)
        pid = int(pid_file.read_text())
        self.assertTrue(pid_is_gone(pid), "child pid survived SIGKILL")

    def test_usage_errors_return_125(self):
        log = str(self.tmp / "usage.log")
        cases = {
            "missing separator": ["5", log, "true"],
            "non-numeric seconds": ["soon", log, "--", "true"],
            "non-positive seconds": ["0", log, "--", "true"],
            "non-finite seconds": ["nan", log, "--", "true"],
            "infinite seconds": ["inf", log, "--", "true"],
            "empty command": ["5", log, "--"],
        }
        for label, arguments in cases.items():
            with self.subTest(label=label):
                code, _, err = self.run_main(arguments)
                self.assertEqual(125, code)
                self.assertIn("run-bounded", err)

    def test_json_results_cover_completion_timeout_and_start_failure(self):
        rb.TERM_GRACE_SECONDS = 0.1
        cases = [([sys.executable, "-c", "raise SystemExit(7)"], "completed", 7),
                 ([sys.executable, "-c", "import time; time.sleep(30)"], "timeout", 124),
                 ([str(self.tmp / "not-a-command")], "startup-failure", 125)]
        for index, (command, outcome, status) in enumerate(cases):
            with self.subTest(outcome=outcome):
                result = self.tmp / f"result-{index}.json"
                code, _, _ = self.run_main(
                    ["--result-json", str(result), "0.5", str(self.tmp / "run.log"), "--", *command])
                data = json.loads(result.read_text())
                self.assertEqual(status, code)
                self.assertEqual(status, data["exit"])
                self.assertEqual(outcome, data["outcome"])
                self.assertEqual(1, data["version"])
                self.assertGreaterEqual(data["elapsed"], 0)
                self.assertEqual(data["pgid"] is None, outcome == "startup-failure")

    def test_stale_result_and_alias_of_log_refused_before_launch(self):
        result = self.tmp / "result.json"
        result.write_text('{"old":true}')
        sentinel = self.tmp / "should-not-exist"
        command = [sys.executable, "-c", "import pathlib,sys; pathlib.Path(sys.argv[1]).touch()", str(sentinel)]
        for log in (self.tmp / "log", result):
            code, _, _ = self.run_main(["--result-json", str(result), "2", str(log), "--", *command])
            self.assertEqual(125, code)
            self.assertFalse(sentinel.exists())
            self.assertEqual('{"old":true}', result.read_text())

    def test_result_sync_failure_is_visible_and_never_success(self):
        with mock.patch.object(rb.os, "fsync", side_effect=OSError("injected sync failure")):
            code, out, err = self.run_main(
                ["--result-json", str(self.tmp / "result.json"), "2", str(self.tmp / "run.log"),
                 "--", sys.executable, "-c", "pass"])
        self.assertEqual(125, code)
        self.assertIn("result write failed", err)
        self.assertIn("inspect command outcome before retry", err)
        self.assertIn("exit=125", out)

    def test_inconclusive_group_probe_never_claims_confirmed_and_is_bounded(self):
        rb.TERM_GRACE_SECONDS = 0.1
        with mock.patch.object(rb, "KILL_GRACE_SECONDS", 0.1), \
                mock.patch.object(rb, "group_is_gone", return_value=False):
            start = time.monotonic()
            code, out, _ = self.run_main(
                ["0.2", str(self.tmp / "uncertain.log"), "--", sys.executable,
                 "-c", "import time; time.sleep(30)"])
        self.assertEqual(124, code)
        self.assertIn("termination=not-guaranteed", out)
        self.assertLess(time.monotonic() - start, 3)

    def test_descendant_is_escalated_after_leader_exits_on_term(self):
        rb.TERM_GRACE_SECONDS = 0.2
        child = (
            "import signal,time,pathlib,sys; signal.signal(signal.SIGTERM,signal.SIG_IGN); "
            "pathlib.Path(sys.argv[1]).write_text('ready'); time.sleep(30)"
        )
        ready = self.tmp / "descendant-ready"
        leader = (
            "import subprocess,sys,time; "
            "subprocess.Popen([sys.executable,'-c',sys.argv[1],sys.argv[2]]); time.sleep(30)"
        )
        result = self.tmp / "descendant.json"
        code, _, _ = self.run_main(
            ["--result-json", str(result), "1", str(self.tmp / "descendant.log"), "--",
             sys.executable, "-c", leader, child, str(ready)])
        self.assertTrue(ready.exists(), "descendant must start before timeout")
        self.assertEqual(124, code)
        data = json.loads(result.read_text())
        self.assertTrue(data["sigkill"], "dead leader must not suppress descendant escalation")
        self.assertEqual(128 + signal.SIGTERM, data["child_exit"])

    def test_wrapper_signals_cleanup_child_and_report_interruption(self):
        for signum in (signal.SIGINT, signal.SIGTERM):
            with self.subTest(signum=signum):
                pid_file = self.tmp / f"child-{signum}.pid"
                result = self.tmp / f"interrupted-{signum}.json"
                wrapper = subprocess.Popen(
                    [sys.executable, "-B", str(MODULE_PATH), "--result-json", str(result), "30",
                     str(self.tmp / f"interrupt-{signum}.log"), "--", sys.executable,
                     "-c", SLEEP_FOREVER, str(pid_file)], stdout=subprocess.PIPE, stderr=subprocess.PIPE)
                try:
                    deadline = time.monotonic() + 5
                    while not pid_file.exists() and time.monotonic() < deadline:
                        time.sleep(0.02)
                    self.assertTrue(pid_file.exists(), "wait for child readiness before interrupting")
                    wrapper.send_signal(signum)
                    out, err = wrapper.communicate(timeout=10)
                    self.assertEqual(128 + signum, wrapper.returncode, (out, err))
                    data = json.loads(result.read_text())
                    self.assertEqual("interrupted", data["outcome"])
                    self.assertEqual(signum, data["signal"])
                    self.assertEqual("confirmed", data["termination"])
                    self.assertTrue(pid_is_gone(int(pid_file.read_text())))
                finally:
                    if wrapper.poll() is None:
                        wrapper.kill()
                        wrapper.communicate(timeout=5)
                    if pid_file.exists():
                        try:
                            os.killpg(int(pid_file.read_text()), signal.SIGKILL)
                        except ProcessLookupError:
                            pass

    def test_cleanup_log_failures_do_not_skip_signals_to_resistant_descendant(self):
        rb.TERM_GRACE_SECONDS = 0.1
        real_open = open

        class FailingLog:
            def __init__(self, handle, step, method):
                self.handle, self.step, self.method, self.notes = handle, step, method, 0

            def __enter__(self):
                return self

            def __exit__(self, *args):
                self.handle.close()

            def fileno(self):
                return self.handle.fileno()

            def write(self, message):
                self.notes += 1
                if self.notes == self.step and self.method == "write":
                    raise OSError("injected diagnostic write failure")
                return self.handle.write(message)

            def flush(self):
                if self.notes == self.step and self.method == "flush":
                    raise OSError("injected diagnostic flush failure")
                return self.handle.flush()

        for step, method in ((1, "write"), (1, "flush"), (2, "write"), (2, "flush")):
            with self.subTest(step=step, method=method):
                result = self.tmp / f"log-failure-{step}-{method}.json"
                ready = self.tmp / f"ready-{step}-{method}"
                child = (
                    "import os,signal,time,pathlib,sys; signal.signal(signal.SIGTERM,signal.SIG_IGN); "
                    "pathlib.Path(sys.argv[1]).write_text(str(os.getpid())); time.sleep(30)"
                )
                leader = (
                    "import subprocess,sys,time; "
                    "subprocess.Popen([sys.executable,'-c',sys.argv[1],sys.argv[2]]); time.sleep(30)"
                )

                def injected_open(path, mode):
                    handle = real_open(path, mode)
                    return FailingLog(handle, step, method) if mode == "w" else handle

                with mock.patch.object(rb, "open", side_effect=injected_open, create=True):
                    code, out, _ = self.run_main(
                        ["--result-json", str(result), "1", str(self.tmp / "diagnostics.log"), "--",
                         sys.executable, "-c", leader, child, str(ready)])
                self.assertTrue(ready.exists())
                data = json.loads(result.read_text())
                self.assertEqual(124, code)
                self.assertTrue(data["sigkill"])
                self.assertEqual(128 + signal.SIGTERM, data["child_exit"])
                self.assertNotEqual("not-started", data["termination"])
                self.assertEqual(1, len(data["diagnostic_errors"]))
                self.assertIn("diagnostic_errors=1", out)

    def test_signals_during_timeout_cleanup_override_timeout_result(self):
        rb.TERM_GRACE_SECONDS = 0.1
        real_terminate = rb.terminate_group
        for signum in (signal.SIGINT, signal.SIGTERM):
            with self.subTest(signum=signum):
                result = self.tmp / f"during-cleanup-{signum}.json"

                def interrupted_cleanup(*args):
                    signal.raise_signal(signum)
                    return real_terminate(*args)

                with mock.patch.object(rb, "terminate_group", side_effect=interrupted_cleanup):
                    code, _, _ = self.run_main(
                        ["--result-json", str(result), "0.3", str(self.tmp / "during-cleanup.log"),
                         "--", sys.executable, "-c", "import time; time.sleep(30)"])
                data = json.loads(result.read_text())
                self.assertEqual(128 + signum, code)
                self.assertEqual(128 + signum, data["exit"])
                self.assertEqual(signum, data["signal"])
                self.assertEqual("interrupted", data["outcome"])
                self.assertEqual("confirmed", data["termination"])


if __name__ == "__main__":
    unittest.main()
