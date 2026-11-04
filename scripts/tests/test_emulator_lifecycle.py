"""Exercise lifecycle decisions offline; no emulator or real adb command runs."""

import importlib.util
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import Mock, patch

SCRIPT = Path(__file__).resolve().parents[1] / "emulator-lifecycle.py"
SPEC = importlib.util.spec_from_file_location("emulator_lifecycle", SCRIPT)
lifecycle = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(lifecycle)


class LifecycleTests(unittest.TestCase):
    def setUp(self):
        self.controller = lifecycle.Lifecycle("adb", "emulator", 10)
        self.run = Mock()
        self.controller.run = self.run

    def test_ready_avd_identity(self):
        self.run.side_effect = ["List of devices attached\nemulator-5554\tdevice", "Pixel_Fold\nOK"]
        self.assertEqual(("emulator-5554", "Pixel_Fold"), self.controller.current())
        self.assertEqual(["adb", "-s", "emulator-5554", "emu", "avd", "name"],
                         self.run.call_args.args[0])

    def test_rejects_physical_offline_multiple_and_unknown_devices(self):
        for rows in ("phone123\tdevice", "emulator-5554\toffline",
                     "emulator-5554\tdevice\nemulator-5556\tdevice",
                     "emulator-wrong\tdevice"):
            with self.subTest(rows=rows):
                self.run.reset_mock(side_effect=True)
                self.run.return_value = "List of devices attached\n" + rows
                with self.assertRaises(lifecycle.LifecycleError):
                    self.controller.current()
                self.assertEqual(1, self.run.call_count)

    def test_rejects_unallowlisted_identity(self):
        self.run.side_effect = ["List of devices attached\nemulator-5554\tdevice", "Other_AVD\nOK"]
        with self.assertRaisesRegex(lifecycle.LifecycleError, "not allowlisted"):
            self.controller.current()

    def test_rejects_malformed_device_and_identity_responses(self):
        for response in ("", "daemon error", "List of devices attached\ninvalid entry extra"):
            self.run.return_value = response
            with self.assertRaises(lifecycle.LifecycleError):
                self.controller.devices()
        self.run.return_value = "Pixel_Fold"
        with self.assertRaises(lifecycle.LifecycleError):
            self.controller.avd_name("emulator-5554")

    def test_detects_emulator_processes_before_adb_registration(self):
        self.run.side_effect = ["List of devices attached", "10 /sdk/emulator/qemu/darwin-aarch64/qemu-system-aarch64"]
        with patch.object(lifecycle.subprocess, "Popen") as launch:
            with self.assertRaisesRegex(lifecycle.LifecycleError, "process exists"):
                self.controller.start("Pixel_Fold", Path("unused"))
            launch.assert_not_called()

    def test_start_refuses_existing_device_without_launching_or_stopping(self):
        self.run.side_effect = ["List of devices attached\nemulator-5554\tdevice", "Pixel_Fold\nOK"]
        with patch.object(lifecycle.subprocess, "Popen") as launch:
            with self.assertRaisesRegex(lifecycle.LifecycleError, "already connected"):
                self.controller.start("Pixel_Tablet", Path("unused"))
            launch.assert_not_called()
        self.assertFalse(any("kill" in call.args[0] for call in self.run.call_args_list))

    def test_start_checks_installed_avd(self):
        self.run.side_effect = ["List of devices attached", "1 /sbin/launchd", "Medium_Phone"]
        with self.assertRaisesRegex(lifecycle.LifecycleError, "not installed"):
            self.controller.start("Pixel_Fold", Path("unused"))

    def test_start_waits_for_boot_and_returns_verified_serial(self):
        self.run.side_effect = [
            "List of devices attached", "1 /sbin/launchd", "Pixel_Fold",
            "List of devices attached\nemulator-5554\tdevice", "Pixel_Fold\nOK", "1",
        ]
        process = Mock(pid=123)
        process.poll.return_value = None
        with tempfile.TemporaryDirectory() as directory:
            with patch.object(lifecycle.subprocess, "Popen", return_value=process) as launch:
                self.assertEqual("emulator-5554", self.controller.start("Pixel_Fold", Path(directory) / "start.log"))
                self.assertEqual(["emulator", "-avd", "Pixel_Fold"], launch.call_args.args[0])
                self.assertTrue(launch.call_args.kwargs["start_new_session"])

    def test_start_mismatched_identity_is_unknown_without_recovery(self):
        self.run.side_effect = [
            "List of devices attached", "1 /sbin/launchd", "Pixel_Fold",
            "List of devices attached\nemulator-5554\tdevice", "Pixel_Tablet\nOK",
        ]
        process = Mock(pid=123)
        process.poll.return_value = None
        with tempfile.TemporaryDirectory() as directory:
            with patch.object(lifecycle.subprocess, "Popen", return_value=process):
                with self.assertRaisesRegex(lifecycle.LifecycleError, "differs"):
                    self.controller.start("Pixel_Fold", Path(directory) / "start.log")
        process.kill.assert_not_called()
        process.terminate.assert_not_called()

    def test_stop_requires_matching_identity(self):
        self.run.side_effect = ["List of devices attached\nemulator-5554\tdevice", "Pixel_Tablet\nOK"]
        with self.assertRaisesRegex(lifecycle.LifecycleError, "requested AVD"):
            self.controller.stop("Pixel_Fold")
        self.assertFalse(any("kill" in call.args[0] for call in self.run.call_args_list))

    def test_stop_checks_disconnection_and_process_exit(self):
        self.run.side_effect = [
            "List of devices attached\nemulator-5554\tdevice", "Pixel_Fold\nOK", "OK",
            "List of devices attached", "10 /sdk/qemu-system-aarch64",
            "List of devices attached", "1 /sbin/launchd",
        ]
        with patch.object(lifecycle.time, "sleep"):
            self.assertEqual("emulator-5554", self.controller.stop("Pixel_Fold"))
        self.assertEqual(["adb", "-s", "emulator-5554", "emu", "kill"],
                         self.run.call_args_list[2].args[0])

    def test_deadline_stops_without_recovery(self):
        with patch.object(lifecycle.time, "monotonic", return_value=self.controller.deadline + 1):
            with self.assertRaisesRegex(lifecycle.LifecycleError, "Deadline exceeded"):
                self.controller.remaining()

    def test_adb_command_timeout_is_bounded_and_not_retried(self):
        controller = lifecycle.Lifecycle("adb", "emulator", 10)
        with patch.object(lifecycle.subprocess, "run", side_effect=subprocess.TimeoutExpired("adb", 10)) as run:
            with self.assertRaises(subprocess.TimeoutExpired):
                controller.devices()
            self.assertEqual(1, run.call_count)
            self.assertLessEqual(run.call_args.kwargs["timeout"], 10)

    def test_cli_rejects_missing_ack_unknown_avd_and_internal_log(self):
        for args in (("stop", "Pixel_Fold"), ("start", "Other_AVD"),
                     ("start", "Pixel_Fold", "--log", str(SCRIPT.parent / "start.log"))):
            with self.subTest(args=args), patch.object(lifecycle.sys, "argv", [str(SCRIPT), *args]):
                with patch.object(lifecycle, "sdk_binary") as resolve:
                    with self.assertRaises(SystemExit) as caught:
                        lifecycle.main()
                    self.assertEqual(2, caught.exception.code)
                    resolve.assert_not_called()


if __name__ == "__main__":
    unittest.main()
