"""Hermetic durable-state tests: temporary Git checkouts, no network or devices."""

import contextlib
import importlib.util
import io
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import unittest
from unittest import mock

sys.dont_write_bytecode = True
SPEC = importlib.util.spec_from_file_location("task_state", Path(__file__).resolve().parents[1] / "task-state.py")
ts = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = ts
SPEC.loader.exec_module(ts)


class TaskStateTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name).resolve()
        self.repo = self.root / "repo"
        self.repo.mkdir()
        self.state = self.root / "state"
        self.git("init", "-q")
        self.git("config", "user.name", "Fixture")
        self.git("config", "user.email", "fixture@example.invalid")
        self.git("config", "commit.gpgsign", "false")
        (self.repo / "file.txt").write_text("baseline\n")
        self.git("add", "file.txt")
        self.git("commit", "-qm", "baseline")
        self.contract = {"objective": "test task", "owner": "main",
                         "allowed_paths": ["file.txt", "new.txt"],
                         "acceptance": ["fixture passes"], "approval_reference": "user message fixture"}
        self.contract_file = self.root / "contract.json"
        self.contract_file.write_text(json.dumps(self.contract))
        self.event = self.root / "event.json"
        self.record_path = self.state / "test.json"

    def git(self, *args):
        return subprocess.run(["git", "-C", str(self.repo), *args], check=True,
                              capture_output=True, timeout=10).stdout

    def cli(self, *args, directory=None):
        out, err = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(out), contextlib.redirect_stderr(err):
            code = ts.main(["--dir", str(directory or self.state), "--task", "test", *args])
        return code, out.getvalue(), err.getvalue()

    def init(self, seconds="600"):
        return self.cli("init", "--repo", str(self.repo), "--contract", str(self.contract_file),
                        "--budget-seconds", seconds)

    def record(self):
        return json.loads(self.record_path.read_text())

    def update(self, event, revision=None):
        self.event.write_text(json.dumps(event))
        revision = self.record()["revision"] if revision is None else revision
        return self.cli("update", "--expect", str(revision), "--event", str(self.event))

    def test_init_show_resume_and_duplicate_init(self):
        self.assertEqual(0, self.init()[0])
        record = self.record()
        self.assertEqual(0, record["revision"])
        self.assertEqual(self.contract, record["contract"])
        self.assertEqual(str(self.repo), record["baseline"]["repo"])
        self.assertEqual(0, self.cli("show")[0])
        code, out, _ = self.cli("resume")
        self.assertEqual(0, code)
        self.assertFalse(json.loads(out)["drift"])
        self.assertEqual(2, self.init()[0])
        self.assertEqual(record, self.record())

    def test_extension_preserves_contract_baseline_usage_history_and_original_budget(self):
        self.init()
        original = self.record()
        now = original["accounted_at"] + 10
        event = {"type": "extend", "add_seconds": 90,
                 "approval_reference": "user approved 90 seconds", "reason": "finish verification"}
        with mock.patch.object(ts.time, "time", return_value=now):
            self.assertEqual(0, self.update(event)[0])
        record = self.record()
        for key in ("contract", "baseline", "expected", "budget_seconds"):
            self.assertEqual(original[key], record[key])
        self.assertEqual(10, record["used_seconds"])
        self.assertEqual(690, ts.effective_budget_seconds(record))
        self.assertEqual(event, record["history"][-1]["event"])
        self.assertEqual(0, self.update(dict(event, add_seconds=30))[0])
        self.assertEqual(720, ts.effective_budget_seconds(self.record()))

    def test_extension_after_expiry_does_not_clear_pending_or_acknowledge_drift(self):
        self.init("10")
        self.update({"type": "begin", "id": "write", "description": "unknown outcome"})
        before = self.record()
        (self.repo / "file.txt").write_text("observed write\n")
        now = before["accounted_at"] + 20
        with mock.patch.object(ts.time, "time", return_value=now):
            self.assertEqual(0, self.update({"type": "extend", "add_seconds": 90,
                "approval_reference": "user approval", "reason": "inspect unknown outcome"})[0])
            code, out, _ = self.cli("resume")
        report = json.loads(out)
        self.assertEqual(2, code)
        self.assertTrue(report["drift"])
        self.assertEqual(["write"], report["unknown_operations"])
        self.assertGreater(report["remaining_seconds"], 0)
        self.assertEqual(before["expected"], self.record()["expected"])
        self.assertEqual(before["operations"], self.record()["operations"])
        self.assertEqual(before["history"], self.record()["history"][:-1])
        self.assertEqual(2, self.update({"type": "begin", "id": "again", "description": "refuse"})[0])

    def test_expired_nonterminal_can_extend_and_begin_only_when_reconciled(self):
        self.init("10")
        now = self.record()["accounted_at"] + 20
        with mock.patch.object(ts.time, "time", return_value=now):
            self.assertEqual(2, self.update({"type": "begin", "id": "no", "description": "expired"})[0])
            self.assertEqual(0, self.update({"type": "extend", "add_seconds": 30,
                "approval_reference": "approved", "reason": "more time"})[0])
            code, out, _ = self.cli("resume")
            self.assertEqual(0, code)
            self.assertEqual(20, json.loads(out)["remaining_seconds"])
            self.assertEqual(0, self.update({"type": "begin", "id": "yes", "description": "fresh"})[0])

    def test_extension_rejects_invalid_amount_text_and_terminal_phase_without_writing(self):
        self.init()
        good = {"type": "extend", "add_seconds": 60, "approval_reference": "approved", "reason": "more"}
        for field, values in (("add_seconds", (0, -1, True, "60", None, float("inf"))),
                              ("approval_reference", (None, " ")), ("reason", (None, ""))):
            for value in values:
                with self.subTest(field=field, value=value):
                    before = self.record_path.read_bytes()
                    self.assertEqual(125, self.update(dict(good, **{field: value}))[0])
                    self.assertEqual(before, self.record_path.read_bytes())
        for phase in ("complete", "blocked"):
            record = self.record()
            record["phase"] = phase
            with self.assertRaises(ts.Conflict):
                ts.apply_event(record, good, record["expected"], time.time())

    def test_extension_history_tampering_and_overflow_fail_closed(self):
        self.init()
        self.update({"type": "extend", "add_seconds": 60, "approval_reference": "approved", "reason": "more"})
        original = self.record()
        for field, value in (("add_seconds", 120), ("approval_reference", "invented"),
                             ("reason", "changed"), ("revision", 9), ("at", 0)):
            with self.subTest(field=field):
                record = json.loads(json.dumps(original))
                record["extensions"][0][field] = value
                with self.assertRaises(ts.Invalid):
                    ts.validate_record(record)
        record = json.loads(json.dumps(original))
        record.pop("extensions")
        with self.assertRaises(ts.Invalid):
            ts.validate_record(record)
        record = json.loads(json.dumps(original))
        record["history"][0]["event"]["add_seconds"] = -1
        with self.assertRaises(ts.Invalid):
            ts.validate_record(record)
        huge = {"type": "extend", "add_seconds": 1e308, "approval_reference": "approved", "reason": "finite"}
        first = ts.apply_event(original, huge, original["expected"], time.time())
        with self.assertRaises(ts.Invalid):
            ts.apply_event(first, huge, first["expected"], time.time())

    def test_extension_does_not_authenticate_an_approval_reference(self):
        self.init()
        # Deliberately fabricated audit text passes: real user consent is checked by
        # the operator, never authenticated by this cooperative helper.
        self.assertEqual(0, self.update({"type": "extend", "add_seconds": 60,
            "approval_reference": "fabricated fixture claim", "reason": "boundary demonstration"})[0])

    def test_rejects_internal_state_directory_before_creating_it(self):
        target = self.repo / "state"
        result = self.cli("init", "--repo", str(self.repo), "--contract", str(self.contract_file),
                          "--budget-seconds", "600", directory=target)
        self.assertEqual(125, result[0])
        self.assertFalse(target.exists())

    def test_invalid_contract_and_nonfinite_budget(self):
        for seconds in ("nan", "inf", "0", "-1"):
            with self.subTest(seconds=seconds):
                self.assertEqual(125, self.init(seconds)[0])
                self.assertFalse(self.record_path.exists())
        for path in ("../escape", "/absolute", ".git/config", "*.py", "a/../b"):
            with self.subTest(path=path):
                self.contract["allowed_paths"] = [path]
                self.contract_file.write_text(json.dumps(self.contract))
                self.assertEqual(125, self.init()[0])

    def test_temporary_root_detection(self):
        temp_root = Path(tempfile.gettempdir())
        self.assertTrue(ts.under_temporary_root(temp_root))
        self.assertTrue(ts.under_temporary_root(temp_root / "nested" / "state"))
        self.assertTrue(ts.under_temporary_root(Path("/tmp")))
        self.assertFalse(ts.under_temporary_root(Path.home() / "state"))

    def test_temporary_state_location_is_warned_about_without_blocking(self):
        # A platform cleanup evicted a live record once, so a temporary location is reported. It is
        # deliberately a warning: a short-lived record may legitimately live in a temporary directory,
        # so it must not stop the task or make the record look unreconciled.
        result = self.init()
        self.assertEqual(0, result[0])
        self.assertIn("temporary", result[2].lower())
        code, out, _ = self.cli("resume")
        report = json.loads(out)
        self.assertTrue(report["state_directory_temporary"])
        self.assertFalse(report["reconciliation_needed"])
        self.assertEqual(0, code)

    def test_stale_update_preserves_first_writer(self):
        self.init()
        self.assertEqual(0, self.update({"type": "phase", "value": "implementing"}, 0)[0])
        original = self.record_path.read_bytes()
        self.assertEqual(2, self.update({"type": "phase", "value": "verifying"}, 0)[0])
        self.assertEqual(original, self.record_path.read_bytes())

    def test_competing_process_updates_have_one_winner(self):
        self.init()
        event_files = []
        for value in ("implementing", "verifying"):
            event = self.root / f"{value}.json"
            event.write_text(json.dumps({"type": "phase", "value": value}))
            event_files.append(event)
        processes = []
        try:
            for event in event_files:
                processes.append(subprocess.Popen(
                    [sys.executable, "-B", str(SPEC.origin), "--dir", str(self.state),
                     "--task", "test", "update", "--expect", "0", "--event", str(event)],
                    stdout=subprocess.PIPE, stderr=subprocess.PIPE))
            for process in processes:
                process.communicate(timeout=15)
            self.assertEqual([0, 2], sorted(process.returncode for process in processes))
            self.assertEqual(1, self.record()["revision"])
        finally:
            for process in processes:
                if process.poll() is None:
                    process.kill()
                    process.communicate(timeout=5)

    def test_writer_lock_contends_and_releases_after_close(self):
        self.init()
        # A second file descriptor must not acquire the same advisory lock.
        with ts.locked(self.state, "test"):
            self.assertEqual(2, self.update({"type": "phase", "value": "implementing"})[0])
        self.assertEqual(0, self.update({"type": "phase", "value": "implementing"})[0])

    def test_pending_operation_is_unknown_after_reload_and_not_replayed(self):
        self.init()
        event = {"type": "begin", "id": "save-1", "description": "observe synthetic save"}
        self.assertEqual(0, self.update(event)[0])
        code, out, _ = self.cli("resume")
        self.assertEqual(2, code)
        self.assertEqual(["save-1"], json.loads(out)["unknown_operations"])
        self.assertEqual(2, self.update(event)[0])
        self.assertEqual(2, self.update({"type": "checkpoint", "reason": "cannot hide pending"})[0])
        self.assertEqual(125, self.update({"type": "resolve", "id": "save-1", "outcome": "failed"})[0])
        self.assertEqual(0, self.update({"type": "resolve", "id": "save-1", "outcome": "completed",
                                        "evidence": "explicit observation"})[0])
        self.assertEqual(2, self.update(event)[0])
        self.assertEqual(0, self.cli("resume")[0])

    def test_drift_requires_explicit_checkpoint(self):
        self.init()
        (self.repo / "file.txt").write_text("changed\n")
        self.assertEqual(2, self.cli("resume")[0])
        self.assertEqual(2, self.update({"type": "begin", "id": "next", "description": "next"})[0])
        self.assertEqual(0, self.update({"type": "checkpoint", "reason": "inspected expected edit"})[0])
        self.assertEqual(0, self.cli("resume")[0])
        self.assertNotEqual(self.record()["baseline"], self.record()["expected"])

    def test_outside_scope_paths_cannot_be_checkpointed(self):
        self.init()
        (self.repo / "outside.txt").write_text("unexpected")
        code, out, _ = self.cli("resume")
        self.assertEqual(2, code)
        self.assertEqual(["outside.txt"], json.loads(out)["outside_allowed_paths"])
        self.assertEqual(2, self.update({"type": "checkpoint", "reason": "not authority"})[0])

    def test_dirty_baseline_is_retained_and_untracked_content_is_hashed(self):
        dirty = self.repo / "existing.txt"
        dirty.write_text("user work")
        self.init()
        self.assertEqual(["existing.txt"], self.record()["baseline"]["changed_paths"])
        dirty.write_text("user work changed, same path")
        code, out, _ = self.cli("resume")
        self.assertEqual(2, code)
        self.assertTrue(json.loads(out)["drift"])

    def test_staged_unstaged_renamed_and_untracked_states_differ(self):
        first = ts.snapshot(self.repo)
        (self.repo / "file.txt").write_text("edit")
        second = ts.snapshot(self.repo)
        self.git("add", "file.txt")
        third = ts.snapshot(self.repo)
        self.git("mv", "file.txt", "renamed.txt")
        fourth = ts.snapshot(self.repo)
        self.assertEqual(4, len({item["dirty_sha256"] for item in (first, second, third, fourth)}))
        self.assertEqual(["file.txt", "renamed.txt"], fourth["changed_paths"])

    def test_head_change_detected_even_with_clean_checkout(self):
        self.init()
        self.git("commit", "--allow-empty", "-qm", "new head")
        self.assertEqual(2, self.cli("resume")[0])

    def test_identity_mismatch_is_reported_not_checkpointed(self):
        self.init()
        current = ts.snapshot(self.repo)
        current["common_dir"] += "-different"
        report = ts.reconcile(self.record(), current, self.record()["accounted_at"])
        self.assertFalse(report["identity_matches"])
        with self.assertRaises(ts.Conflict):
            ts.apply_event(self.record(), {"type": "checkpoint", "reason": "no"}, current, time_now(self.record()))

    def test_evidence_is_tied_to_observed_snapshot(self):
        self.init()
        self.assertEqual(0, self.update({"type": "evidence", "description": "test check", "outcome": "passed",
                                        "reference": "/external/test.log"})[0])
        evidence = self.record()["evidence"][0]
        self.assertEqual(ts.snapshot(self.repo), evidence["snapshot"])
        self.assertEqual("passed", evidence["outcome"])

    def test_expired_budget_persists_and_still_allows_recording_unknown_outcome(self):
        with mock.patch.object(ts.time, "time", return_value=100):
            self.init("5")
            self.update({"type": "begin", "id": "op", "description": "operation"})
        with mock.patch.object(ts.time, "time", return_value=110):
            self.assertEqual(2, self.cli("resume")[0])
            self.assertEqual(0, self.update({"type": "resolve", "id": "op", "outcome": "not-performed",
                                            "evidence": "observed no execution"})[0])
            self.assertEqual(2, self.update({"type": "phase", "value": "complete"})[0])
            self.assertEqual(0, self.update({"type": "phase", "value": "blocked"})[0])
        with mock.patch.object(ts.time, "time", return_value=90):
            self.assertEqual(2, self.cli("resume")[0])
            self.assertGreaterEqual(self.record()["used_seconds"], 10)

    def test_resume_and_show_do_not_write_or_clear_pending(self):
        self.init()
        self.update({"type": "begin", "id": "op", "description": "operation"})
        before = self.record_path.read_bytes()
        self.cli("show")
        self.cli("resume")
        self.assertEqual(before, self.record_path.read_bytes())

    def test_corrupt_and_unsupported_records_refused_without_overwrite(self):
        self.init()
        original = self.record()
        cases = ["{broken", '{"version":1,"version":1}',
                 json.dumps({**original, "version": 99}), json.dumps({**original, "revision": -1}),
                 json.dumps({**original, "operations": {"op": {"outcome": "completed"}}})]
        for raw in cases:
            with self.subTest(raw=raw[:40]):
                self.record_path.write_text(raw)
                self.assertEqual(125, self.cli("resume")[0])
                self.assertEqual(raw, self.record_path.read_text())

    def test_operation_history_contradictions_fail_closed_on_all_commands(self):
        self.init()
        self.update({"type": "begin", "id": "save", "description": "pending save"})
        pending = self.record()
        self.update({"type": "resolve", "id": "save", "outcome": "completed", "evidence": "observed"})
        resolved = self.record()
        cases = []
        missing_pending = json.loads(json.dumps(pending))
        missing_pending["operations"] = {}
        cases.append(missing_pending)
        missing_resolution = json.loads(json.dumps(resolved))
        missing_resolution["operations"]["save"] = pending["operations"]["save"]
        cases.append(missing_resolution)
        contradictory_resolution = json.loads(json.dumps(resolved))
        contradictory_resolution["operations"]["save"]["evidence"] = "different evidence"
        cases.append(contradictory_resolution)
        false_resolution = json.loads(json.dumps(pending))
        false_resolution["operations"] = resolved["operations"]
        cases.append(false_resolution)
        reuse = json.loads(json.dumps(resolved))
        reuse["revision"] += 1
        reuse["history"].append({"revision": 3, "event": {"type": "begin", "id": "save",
                                                       "description": "replay"}, "at": reuse["accounted_at"]})
        reuse["operations"] = {"save": {"description": "replay", "outcome": "pending"}}
        cases.append(reuse)
        phantom = json.loads(json.dumps(resolved))
        phantom["operations"]["extra"] = {"description": "no begin", "outcome": "pending"}
        cases.append(phantom)
        self.event.write_text(json.dumps({"type": "begin", "id": "save", "description": "must not replay"}))
        for index, record in enumerate(cases):
            with self.subTest(case=index):
                raw = json.dumps(record)
                self.record_path.write_text(raw)
                for arguments in (("show",), ("resume",),
                                  ("update", "--expect", str(record["revision"]), "--event", str(self.event))):
                    with self.subTest(command=arguments[0]):
                        self.assertEqual(125, self.cli(*arguments)[0])
                        self.assertEqual(raw, self.record_path.read_text())

    def test_symlink_record_refused(self):
        self.init()
        original = self.record_path.read_bytes()
        target = self.root / "other.json"
        target.write_bytes(original)
        self.record_path.unlink()
        self.record_path.symlink_to(target)
        self.assertEqual(125, self.cli("show")[0])
        self.assertEqual(original, target.read_bytes())

    def test_failed_replace_leaves_old_record_intact_and_no_partial_file(self):
        self.init()
        before = self.record_path.read_bytes()
        with mock.patch.object(ts.os, "replace", side_effect=OSError("injected interruption")):
            self.assertEqual(125, self.update({"type": "phase", "value": "implementing"})[0])
        self.assertEqual(before, self.record_path.read_bytes())
        self.assertEqual({"test.json", "test.lock"}, {path.name for path in self.state.iterdir()})

    def test_process_crash_before_replace_preserves_unknown_operation(self):
        self.init()
        self.update({"type": "begin", "id": "write", "description": "synthetic side effect"})
        self.event.write_text(json.dumps({"type": "resolve", "id": "write", "outcome": "completed",
                                         "evidence": "should not reach persistent record"}))
        before = self.record_path.read_bytes()
        code = (
            "import os, runpy, sys; module=runpy.run_path(sys.argv[1]); "
            "module['os'].replace=lambda *args: os._exit(77); "
            "module['main'](sys.argv[2:])"
        )
        result = subprocess.run(
            [sys.executable, "-B", "-c", code, str(SPEC.origin), "--dir", str(self.state),
             "--task", "test", "update", "--expect", "1", "--event", str(self.event)],
            capture_output=True, timeout=15)
        self.assertEqual(77, result.returncode)
        self.assertEqual(before, self.record_path.read_bytes())
        status, out, _ = self.cli("resume")
        self.assertEqual(2, status)
        self.assertEqual(["write"], json.loads(out)["unknown_operations"])
        # The crashed writer's OS lock is gone; explicit observed resolution works.
        self.assertEqual(0, self.update({"type": "resolve", "id": "write", "outcome": "not-performed",
                                        "evidence": "fresh inspection after crash"})[0])

    def test_failed_directory_sync_reports_uncertain_commit_and_does_not_replay(self):
        self.init()
        with mock.patch.object(ts.os, "fsync", side_effect=[None, OSError("directory sync failed")]):
            self.assertEqual(125, self.update({"type": "phase", "value": "implementing"})[0])
        # Replacement happened before failure; the caller must reload, never replay.
        self.assertEqual(1, self.record()["revision"])
        self.assertEqual(2, self.update({"type": "phase", "value": "verifying"}, revision=0)[0])

    def test_init_never_executes_contract_command(self):
        self.contract["command"] = "must never execute"
        self.contract_file.write_text(json.dumps(self.contract))
        self.assertEqual(0, self.init()[0])
        self.assertEqual("must never execute", self.record()["contract"]["command"])

    def test_boundary_is_read_only_and_requires_latest_revision_specific_pass(self):
        self.init()
        args = ("boundary", "--expect", "0", "--require-evidence", "host tests")
        before = self.record_path.read_bytes()
        code, out, _ = self.cli(*args)
        self.assertEqual(2, code)
        self.assertEqual(["host tests"], json.loads(out)["missing_evidence"])
        self.assertEqual(before, self.record_path.read_bytes())
        event = {"type": "evidence", "description": "host tests", "outcome": "passed",
                 "reference": "/external/host.log"}
        self.assertEqual(0, self.update(event)[0])
        self.assertEqual(2, self.cli(*args)[0], "old expected revision must fail")
        self.assertEqual(0, self.cli("boundary", "--expect", "1", "--require-evidence", "host tests")[0])
        self.update({**event, "outcome": "failed"})
        self.assertEqual(2, self.cli("boundary", "--expect", "2", "--require-evidence", "host tests")[0])
        self.update(event)
        (self.repo / "file.txt").write_text("changed")
        self.update({"type": "checkpoint", "reason": "inspected intended change"})
        self.assertEqual(2, self.cli("boundary", "--expect", "4", "--require-evidence", "host tests")[0],
                         "old tree's pass is not current evidence")

    def test_boundary_blocks_terminal_phase_unknown_drift_scope_and_expiry(self):
        with mock.patch.object(ts.time, "time", return_value=100):
            self.init("5")
        before = self.record_path.read_bytes()
        with mock.patch.object(ts.time, "time", return_value=110):
            self.assertEqual(2, self.cli("boundary", "--expect", "0")[0])
            self.assertEqual(2, self.update({"type": "begin", "id": "expired", "description": "no"})[0])
            self.assertEqual(before, self.record_path.read_bytes())
            self.assertEqual(0, self.update({"type": "phase", "value": "blocked"})[0])
        for phase in ("blocked", "complete"):
            record = self.record()
            record["phase"] = phase
            report = ts.boundary_report(record, ts.snapshot(self.repo), 100, record["revision"], [])
            self.assertFalse(report["boundary_clear"])
        record = self.record()
        current = ts.snapshot(self.repo)
        current["changed_paths"] = ["outside.txt"]
        report = ts.boundary_report(record, current, 100, record["revision"], [])
        self.assertEqual(["outside.txt"], report["outside_allowed_paths"])
        self.assertFalse(report["boundary_clear"])

    def test_real_kill_after_side_effect_resumes_without_replay(self):
        self.init()
        baseline = self.record()["baseline"]
        self.update({"type": "begin", "id": "sentinel-write", "description": "append sentinel once"})
        prefix = self.record()["history"]
        ready = self.root / "worker-ready"
        worker = (
            "import os,sys,time; "
            "f=open(sys.argv[1],'a'); f.write('sentinel\\n'); f.flush(); os.fsync(f.fileno()); f.close(); "
            "open(sys.argv[2],'w').close(); time.sleep(60)"
        )
        process = subprocess.Popen([sys.executable, "-B", "-c", worker,
                                    str(self.repo / "file.txt"), str(ready)])
        try:
            deadline = time.monotonic() + 5
            while not ready.exists() and time.monotonic() < deadline:
                time.sleep(0.02)
            self.assertTrue(ready.exists(), "worker must write before the real SIGKILL")
            process.kill()
            process.wait(timeout=5)
            self.assertEqual(-9, process.returncode)
            before = self.record_path.read_bytes()
            code, out, _ = self.cli("resume")
            self.assertEqual(2, code)
            self.assertEqual(["sentinel-write"], json.loads(out)["unknown_operations"])
            self.assertEqual(before, self.record_path.read_bytes())
            self.assertEqual(2, self.cli("boundary", "--expect", "1")[0])
            self.assertEqual(2, self.update({"type": "begin", "id": "replay", "description": "forbidden"})[0])
            observed = (self.repo / "file.txt").read_text()
            self.assertEqual("baseline\nsentinel\n", observed)
            self.assertEqual(0, self.update({"type": "resolve", "id": "sentinel-write", "outcome": "completed",
                                            "evidence": "fresh read: baseline plus exactly one sentinel"})[0])
            self.assertEqual(0, self.update({"type": "checkpoint", "reason": "inspected single append after kill"})[0])
            self.assertEqual(0, self.cli("boundary", "--expect", "3")[0])
            self.assertEqual(0, self.update({"type": "phase", "value": "complete"})[0])
            self.assertEqual(baseline, self.record()["baseline"])
            self.assertEqual(prefix, self.record()["history"][:len(prefix)])
            self.assertEqual(observed, (self.repo / "file.txt").read_text())
            print("kill-journey: SIGKILL observed; pending retained; fresh inspection; sentinel count=1; "
                  "no replay; baseline/history preserved; complete")
        finally:
            if process.poll() is None:
                process.kill()
                process.wait(timeout=5)

    def amendment(self, **overrides):
        event = {"type": "amend", "approval_reference": "user message fixture",
                 "note": "approved extra.txt after the contract was accepted",
                 "add_allowed_paths": ["extra.txt"]}
        event.update(overrides)
        return event

    def test_amendment_widens_scope_without_rewriting_the_contract(self):
        self.init()
        (self.repo / "extra.txt").write_text("approved after the contract\n")
        code, out, _ = self.cli("resume")
        self.assertEqual(2, code)
        self.assertEqual(["extra.txt"], json.loads(out)["outside_allowed_paths"])
        contract_before = json.dumps(self.record()["contract"], sort_keys=True)
        self.assertEqual(2, self.update({"type": "checkpoint", "reason": "no amendment yet"})[0])
        self.assertEqual(0, self.update(self.amendment())[0])
        record = self.record()
        self.assertEqual(contract_before, json.dumps(record["contract"], sort_keys=True))
        self.assertEqual(["extra.txt"], record["amendments"][0]["add_allowed_paths"])
        code, out, _ = self.cli("resume")
        self.assertEqual(2, code, "the tree is still unacknowledged drift")
        self.assertEqual([], json.loads(out)["outside_allowed_paths"])
        self.assertEqual(["extra.txt"], json.loads(out)["amended_paths"])
        self.assertEqual(0, self.update({"type": "checkpoint", "reason": "inspected the approved addition"})[0])
        self.assertEqual(0, self.cli("resume")[0])

    def test_boundary_clears_for_amended_scope_and_still_blocks_other_paths(self):
        self.init()
        (self.repo / "extra.txt").write_text("approved\n")
        self.update(self.amendment())
        self.update({"type": "checkpoint", "reason": "inspected the approved addition"})
        self.update({"type": "evidence", "description": "host tests", "outcome": "passed",
                     "reference": "/external/host.log"})
        args = ("boundary", "--expect", str(self.record()["revision"]), "--require-evidence", "host tests")
        self.assertEqual(0, self.cli(*args)[0])
        (self.repo / "other.txt").write_text("never approved\n")
        code, out, _ = self.cli(*args)
        self.assertEqual(2, code)
        self.assertEqual(["other.txt"], json.loads(out)["outside_allowed_paths"])

    def test_amendment_records_the_paths_it_legitimized_after_observation(self):
        self.init()
        (self.repo / "extra.txt").write_text("written before the amendment\n")
        self.update(self.amendment())
        record = self.record()
        amendment = record["amendments"][0]
        self.assertEqual(["extra.txt"], amendment["covered_dirty_paths"])
        self.assertEqual(record["revision"], amendment["revision"])
        self.assertEqual("amend", record["history"][-1]["event"]["type"])
        self.assertEqual("user message fixture", amendment["approval_reference"])

    def test_amendment_does_not_cover_a_path_it_did_not_name(self):
        self.init()
        (self.repo / "extra.txt").write_text("approved\n")
        (self.repo / "other.txt").write_text("not approved\n")
        self.update(self.amendment())
        code, out, _ = self.cli("resume")
        self.assertEqual(["other.txt"], json.loads(out)["outside_allowed_paths"])
        self.assertEqual(2, self.update({"type": "checkpoint", "reason": "still out of scope"})[0])

    def test_amendment_validation_and_acceptance_only_additions(self):
        self.init()
        cases = [self.amendment(approval_reference=""),
                 self.amendment(note="   "),
                 self.amendment(add_allowed_paths=["../escape"]),
                 self.amendment(add_allowed_paths=["*.py"]),
                 self.amendment(add_allowed_paths=["file.txt"]),
                 self.amendment(add_allowed_paths=["file.txt", "new.txt"]),
                 {"type": "amend", "approval_reference": "user message fixture", "note": "nothing added"}]
        for case in cases:
            with self.subTest(case=str(case)[:60]):
                self.assertEqual(125, self.update(case)[0])
        self.assertEqual([], self.record()["amendments"])
        self.assertEqual(0, self.update({"type": "amend", "approval_reference": "user message fixture",
                                        "note": "extra acceptance only", "add_acceptance": ["extra check"]})[0])
        amendment = self.record()["amendments"][0]
        self.assertEqual([], amendment["add_allowed_paths"])
        self.assertEqual(["extra check"], amendment["add_acceptance"])

    def test_amendment_refused_with_pending_outcome_or_terminal_phase(self):
        self.init()
        self.update({"type": "begin", "id": "op", "description": "pending side effect"})
        self.assertEqual(2, self.update(self.amendment())[0])
        self.assertEqual(0, self.update({"type": "resolve", "id": "op", "outcome": "completed",
                                         "evidence": "observed"})[0])
        self.assertEqual(0, self.update({"type": "phase", "value": "complete"})[0])
        self.assertEqual(2, self.update(self.amendment())[0])
        self.assertEqual([], self.record()["amendments"])

    def test_amendment_never_extends_the_budget(self):
        with mock.patch.object(ts.time, "time", return_value=100):
            self.init("5")
            self.assertEqual(0, self.update({"type": "phase", "value": "implementing"})[0])
        with mock.patch.object(ts.time, "time", return_value=110):
            self.assertEqual(0, self.update(self.amendment(note="scope only, no budget"))[0])
            self.assertEqual(5, self.record()["budget_seconds"])
            self.assertEqual(2, self.update({"type": "begin", "id": "later", "description": "expired"})[0])

    def test_amendment_history_contradictions_fail_closed(self):
        self.init()
        self.update(self.amendment())
        record = self.record()
        dropped = json.loads(json.dumps(record))
        dropped["amendments"] = []
        phantom = json.loads(json.dumps(record))
        phantom["amendments"].append({**record["amendments"][0], "revision": 99})
        renumbered = json.loads(json.dumps(record))
        renumbered["amendments"][0]["revision"] = 99
        widened = json.loads(json.dumps(record))
        widened["amendments"][0]["add_allowed_paths"] = ["*.py"]
        valid_widened = json.loads(json.dumps(record))
        valid_widened["amendments"][0]["add_allowed_paths"] = ["secret.txt"]
        smuggled = json.loads(json.dumps(record))
        smuggled["amendments"][0]["add_allowed_paths"] = []
        smuggled["amendments"][0]["already_allowed_paths"] = ["secret.txt"]
        retexted = json.loads(json.dumps(record))
        retexted["amendments"][0]["note"] = "a different reason"
        unreferenced = json.loads(json.dumps(record))
        unreferenced["amendments"][0]["approval_reference"] = ""
        self.event.write_text(json.dumps({"type": "phase", "value": "implementing"}))
        for index, tampered in enumerate((dropped, phantom, renumbered, widened, valid_widened,
                                          smuggled, retexted, unreferenced)):
            with self.subTest(case=index):
                raw = json.dumps(tampered)
                self.record_path.write_text(raw)
                for arguments in (("show",), ("resume",),
                                  ("update", "--expect", str(tampered["revision"]), "--event", str(self.event))):
                    with self.subTest(command=arguments[0]):
                        self.assertEqual(125, self.cli(*arguments)[0])
                        self.assertEqual(raw, self.record_path.read_text())


def time_now(record):
    return record["accounted_at"] + 1


if __name__ == "__main__":
    unittest.main()
