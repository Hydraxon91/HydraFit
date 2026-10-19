"""Exercise scripts/maestro-cli.sh without a Maestro install, device or app state."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest

SCRIPT = Path(__file__).resolve().parents[1] / "maestro-cli.sh"
BASH = "/bin/bash"


class MaestroCliTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)

    def run_cli(self, env):
        return subprocess.run([BASH, str(SCRIPT), "test", "flow.yaml"],
                              capture_output=True, text=True, env=env, check=False)

    def test_configured_binary_forwards_arguments(self):
        stub = self.root / "maestro"
        stub.write_text('#!/bin/sh\nprintf \'%s\\n\' "$@"\n')
        stub.chmod(0o755)
        env = dict(os.environ, MAESTRO_BIN=str(stub), HOME=str(self.root / "home"))
        result = self.run_cli(env)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(["test", "flow.yaml"], result.stdout.splitlines())

    def test_missing_binary_exits_127_with_clear_message(self):
        env = dict(os.environ, HOME=str(self.root / "home"),
                   PATH=str(self.root / "empty-path"))
        env.pop("MAESTRO_BIN", None)
        result = self.run_cli(env)
        self.assertEqual(127, result.returncode)
        self.assertIn("Maestro binary not found", result.stderr)
        self.assertEqual("", result.stdout)


if __name__ == "__main__":
    unittest.main()
