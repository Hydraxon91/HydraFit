"""Offline tests for scripts/run-bounded.py.

Standard library only; every test uses a throwaway temporary directory and
short-lived child processes, so no emulator, network or Gradle run is involved.

Run: python3 -B -m unittest discover -s scripts/tests -p 'test_*.py'
"""

import contextlib
import importlib.util
import io
import os
import pathlib
import sys
import tempfile
import time
import unittest

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


if __name__ == "__main__":
    unittest.main()
