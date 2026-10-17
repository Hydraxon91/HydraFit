"""Offline tests for scripts/lease.py.

Standard library only; every test uses a throwaway temporary directory as the
lease root, so no emulator, network or Gradle run is involved.

Run: python3 -B -m unittest discover -s scripts/tests -p 'test_*.py'
"""

import contextlib
import importlib.util
import io
import json
import pathlib
import shutil
import sys
import tempfile
import threading
import time
import unittest
from datetime import datetime

MODULE_PATH = pathlib.Path(__file__).resolve().parents[1] / "lease.py"
# Loading the helper by path would otherwise drop a __pycache__ directory into scripts/.
sys.dont_write_bytecode = True
_spec = importlib.util.spec_from_file_location("lease", MODULE_PATH)
lease = importlib.util.module_from_spec(_spec)
assert _spec.loader is not None
sys.modules["lease"] = lease
_spec.loader.exec_module(lease)


class LeaseTests(unittest.TestCase):
    def setUp(self):
        self.tmp = pathlib.Path(tempfile.mkdtemp())
        self.addCleanup(lambda: shutil.rmtree(self.tmp, ignore_errors=True))
        self.lease_dir = self.tmp / "res.lock.d"

    def run_main(self, arguments):
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            code = lease.main(arguments)
        return code, out.getvalue(), err.getvalue()

    def acquire(self, owner, wait="0"):
        return self.run_main(
            ["acquire", "--dir", str(self.tmp), "--name", "res", "--owner", owner, "--wait", wait]
        )

    def test_acquire_writes_owner_json(self):
        code, out, _ = self.acquire("alice")
        self.assertEqual(0, code)
        self.assertIn("acquired by alice", out)
        record = json.loads((self.lease_dir / "owner.json").read_text())
        self.assertEqual("alice", record["owner"])
        self.assertIsInstance(record["pid"], int)
        self.assertIsInstance(datetime.fromisoformat(record["acquired_at"]), datetime)

    def test_second_acquire_without_wait_is_refused_with_holder(self):
        self.acquire("alice")
        code, out, _ = self.acquire("bob")
        self.assertEqual(2, code)
        self.assertIn("alice", out)
        self.assertTrue(self.lease_dir.is_dir())

    def test_release_by_wrong_owner_is_refused_and_kept(self):
        self.acquire("alice")
        code, out, _ = self.run_main(
            ["release", "--dir", str(self.tmp), "--name", "res", "--owner", "bob"]
        )
        self.assertEqual(2, code)
        self.assertIn("alice", out)
        self.assertTrue(self.lease_dir.is_dir())
        self.assertEqual("alice", json.loads((self.lease_dir / "owner.json").read_text())["owner"])

    def test_release_by_holder_removes_lease(self):
        self.acquire("alice")
        code, out, _ = self.run_main(
            ["release", "--dir", str(self.tmp), "--name", "res", "--owner", "alice"]
        )
        self.assertEqual(0, code)
        self.assertIn("released by alice", out)
        self.assertFalse(self.lease_dir.exists())

    def test_status_free_held_and_corrupt(self):
        code, out, _ = self.run_main(["status", "--dir", str(self.tmp), "--name", "res"])
        self.assertEqual(0, code)
        self.assertIn("free", out)

        self.acquire("alice")
        code, out, _ = self.run_main(["status", "--dir", str(self.tmp), "--name", "res"])
        self.assertEqual(3, code)
        self.assertIn("alice", out)

        (self.lease_dir / "owner.json").write_text("{ not json")
        code, out, _ = self.run_main(["status", "--dir", str(self.tmp), "--name", "res"])
        self.assertEqual(2, code)
        self.assertIn("unreadable", out)
        self.assertTrue(self.lease_dir.is_dir())

    def test_release_with_unreadable_metadata_is_refused(self):
        self.acquire("alice")
        (self.lease_dir / "owner.json").write_text("garbage")
        code, _, _ = self.run_main(
            ["release", "--dir", str(self.tmp), "--name", "res", "--owner", "alice"]
        )
        self.assertEqual(2, code)
        self.assertTrue(self.lease_dir.is_dir())

    def test_wait_acquires_after_release(self):
        self.acquire("alice")

        def release_after_delay():
            time.sleep(0.3)
            with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
                lease.main(["release", "--dir", str(self.tmp), "--name", "res", "--owner", "alice"])

        worker = threading.Thread(target=release_after_delay)
        worker.start()
        code, _, _ = self.acquire("bob", wait="3")
        worker.join()
        self.assertEqual(0, code)
        self.assertEqual("bob", json.loads((self.lease_dir / "owner.json").read_text())["owner"])

    def test_release_missing_lease_is_refused(self):
        code, out, _ = self.run_main(
            ["release", "--dir", str(self.tmp), "--name", "res", "--owner", "alice"]
        )
        self.assertEqual(2, code)
        self.assertIn("no such lease", out)

    def test_non_finite_wait_returns_125(self):
        for wait in ("nan", "inf"):
            with self.subTest(wait=wait):
                code, _, err = self.run_main(
                    ["acquire", "--dir", str(self.tmp), "--name", "res", "--owner", "alice", "--wait", wait]
                )
                self.assertEqual(125, code)
                self.assertIn("lease:", err)

    def test_bad_names_return_125(self):
        for name in (".", "..", "a/b", "a b", ""):
            with self.subTest(name=name):
                code, _, err = self.run_main(
                    ["status", "--dir", str(self.tmp), "--name", name]
                )
                self.assertEqual(125, code)
                self.assertIn("lease:", err)


if __name__ == "__main__":
    unittest.main()
