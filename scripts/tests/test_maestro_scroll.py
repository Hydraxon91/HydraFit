"""Offline tests for scripts/maestro-scroll.py.

Standard library only; no emulator, device or Maestro CLI is used. The tests
cover the decision core and the geometry against synthetic hierarchies, plus
the observe/decide/swipe loop with injected fakes.

Run: python3 scripts/tests/test_maestro_scroll.py
"""

import contextlib
import importlib.util
import io
import os
import pathlib
import subprocess
import sys
import tempfile
import unittest
from unittest import mock

MODULE_PATH = pathlib.Path(__file__).resolve().parents[1] / "maestro-scroll.py"
# Loading the helper by path would otherwise drop a __pycache__ directory into scripts/.
sys.dont_write_bytecode = True
_spec = importlib.util.spec_from_file_location("maestro_scroll", MODULE_PATH)
ms = importlib.util.module_from_spec(_spec)
assert _spec.loader is not None
# Register before exec so dataclasses can resolve the module for their annotations.
sys.modules["maestro_scroll"] = ms
_spec.loader.exec_module(ms)


def bounds(x1, y1, x2, y2):
    return f"bounds=[{x1},{y1}][{x2},{y2}]"


def attrs(*parts):
    return "; ".join(parts)


def hierarchy(rows):
    """rows: list of (element_num, depth, attributes, parent or None)."""
    lines = ["element_num,depth,attributes,parent_num"]
    for number, depth, attributes, parent in rows:
        parent_text = "" if parent is None else str(parent)
        lines.append(f'{number},{depth},"{attributes}",{parent_text}')
    return "\n".join(lines) + "\n"


def logger_screen(*, picker_bounds, target_bounds, container_bounds=(0, 63, 1080, 1917), extra=None):
    """A Logger-like tree: container -> (content, nested picker -> row, target row)."""
    rows = [
        (0, 0, "", None),
        (1, 1, attrs("resource-id=logger_list", bounds(*container_bounds), "scrollable=true"), 0),
        (2, 2, attrs(bounds(42, 300, 1038, 400), "text=Workout history"), 1),
        (3, 2, attrs("resource-id=logger_exercise_picker", bounds(*picker_bounds), "scrollable=true"), 1),
        (4, 3, attrs("resource-id=logger_exercise_squat", bounds(42, 1080, 1038, 1200)), 3),
        (5, 2, attrs("resource-id=logger_warmup_prep_X", bounds(*target_bounds)), 1),
    ]
    if extra:
        rows.extend(extra)
    return hierarchy(rows)


class ParseTests(unittest.TestCase):
    def test_parses_header_attributes_and_parents(self):
        rows = ms.parse_hierarchy(logger_screen(picker_bounds=(42, 1071, 1038, 1544), target_bounds=(74, 1748, 680, 1874)))
        self.assertEqual(6, len(rows))
        container = [r for r in rows if r.resource_id == "logger_list"][0]
        self.assertEqual((0, 63, 1080, 1917), (container.bounds.left, container.bounds.top, container.bounds.right, container.bounds.bottom))
        self.assertTrue(container.scrollable)
        self.assertEqual(0, container.parent)
        root = rows[0]
        self.assertIsNone(root.bounds)
        self.assertIsNone(root.parent)

    def test_parse_bounds_rejects_garbage(self):
        self.assertIsNone(ms.parse_bounds(""))
        self.assertIsNone(ms.parse_bounds("bounds=[1,2]"))
        self.assertEqual(ms.Rect(1, 2, 3, 4), ms.parse_bounds("[1,2][3,4]"))


class GeometryTests(unittest.TestCase):
    def _screen(self, picker_bounds, container_bounds=(0, 63, 1080, 1917)):
        rows = ms.parse_hierarchy(logger_screen(picker_bounds=picker_bounds, target_bounds=(74, 4000, 680, 4100), container_bounds=container_bounds))
        container = [r for r in rows if r.resource_id == "logger_list"][0]
        return rows, container

    def test_gesture_avoids_the_nested_picker(self):
        rows, container = self._screen((42, 1071, 1038, 1544))
        nested = ms.nested_scrollables(rows, container)
        gesture = ms.choose_gesture(container, nested)
        self.assertIsNotNone(gesture)
        x1, y1, x2, y2 = gesture
        self.assertEqual(x1, x2)
        low, high = sorted((y1, y2))
        picker = ms.Rect(42, 1071, 1038, 1544)
        overlaps = picker.left <= x1 <= picker.right and low < picker.bottom and high > picker.top
        self.assertFalse(overlaps, "gesture must not cross the nested picker")
        self.assertGreaterEqual(high - low, ms.MIN_GESTURE_PIXELS)

    def test_no_safe_region_when_container_is_too_short(self):
        rows, container = self._screen((0, 63, 1080, 1917), container_bounds=(0, 100, 400, 180))
        nested = ms.nested_scrollables(rows, container)
        self.assertIsNone(ms.choose_gesture(container, nested))

    def test_subtract_intervals_clips_and_merges(self):
        self.assertEqual([(0, 10), (20, 30)], ms.subtract_intervals((0, 30), [(10, 20)]))
        self.assertEqual([(0, 5)], ms.subtract_intervals((0, 30), [(5, 40)]))
        self.assertEqual([(0, 10)], ms.subtract_intervals((0, 10), [(12, 20)]))


class DecisionTests(unittest.TestCase):
    def _target(self, *args, **kwargs):
        return ms.Scroller(target_id="logger_warmup_prep_X", *args, **kwargs)

    def test_target_already_visible_returns_done_without_swiping(self):
        text = logger_screen(picker_bounds=(42, 1071, 1038, 1544), target_bounds=(74, 800, 680, 900))
        step = self._target().plan(ms.parse_hierarchy(text))
        self.assertEqual("done", step.action)
        self.assertIsNone(step.gesture)

    def test_edge_clipped_target_is_not_visible(self):
        # Bounds cut to the container's bottom edge, as a real clipped LazyColumn row reports them:
        # a sliver at the edge is not a usable target even though it is contained.
        text = logger_screen(picker_bounds=(42, 1071, 1038, 1544), target_bounds=(74, 1885, 680, 1917))
        step = self._target().plan(ms.parse_hierarchy(text))
        self.assertEqual("swipe", step.action)

    def test_oversized_target_falls_back_to_containment(self):
        # A target at least as tall as the container can never be shown whole; containment suffices.
        text = hierarchy([
            (0, 0, "", None),
            (1, 1, attrs("resource-id=logger_list", bounds(0, 63, 1080, 1917), "scrollable=true"), 0),
            (2, 2, attrs("resource-id=logger_warmup_prep_X", bounds(0, 63, 1080, 1917)), 1),
        ])
        self.assertEqual("done", self._target().plan(ms.parse_hierarchy(text)).action)

    def test_target_outside_the_container_is_blocked(self):
        # A global id/text match outside the container is not this list's target: report, do not swipe.
        text = hierarchy([
            (0, 0, "", None),
            (1, 1, attrs("resource-id=logger_list", bounds(0, 63, 1080, 1917), "scrollable=true"), 0),
            (2, 1, attrs("resource-id=logger_warmup_prep_X", bounds(0, 900, 50, 950)), 0),
        ])
        step = self._target().plan(ms.parse_hierarchy(text))
        self.assertEqual("blocked", step.action)
        self.assertIn("outside the container", step.reason)

    def test_non_scrollable_container_is_blocked(self):
        text = hierarchy([
            (0, 0, "", None),
            (1, 1, attrs("resource-id=logger_list", bounds(0, 63, 1080, 1917)), 0),
            (2, 2, attrs("resource-id=logger_warmup_prep_X", bounds(0, 900, 50, 950)), 1),
        ])
        step = self._target().plan(ms.parse_hierarchy(text))
        self.assertEqual("blocked", step.action)
        self.assertIn("not a scroll container", step.reason)

    def test_partially_clipped_target_is_not_visible(self):
        # The target starts inside the container but runs past its bottom edge.
        text = logger_screen(picker_bounds=(42, 1071, 1038, 1544), target_bounds=(74, 1850, 680, 1990))
        step = self._target().plan(ms.parse_hierarchy(text))
        self.assertEqual("swipe", step.action)

    def test_missing_container_is_blocked(self):
        text = hierarchy([
            (0, 0, "", None),
            (1, 1, attrs("resource-id=logger_warmup_prep_X", bounds(0, 0, 10, 10)), 0),
        ])
        self.assertEqual("blocked", self._target().plan(ms.parse_hierarchy(text)).action)

    def test_duplicate_container_is_blocked(self):
        text = hierarchy([
            (0, 0, "", None),
            (1, 1, attrs("resource-id=logger_list", bounds(0, 0, 100, 1000), "scrollable=true"), 0),
            (2, 1, attrs("resource-id=logger_list", bounds(0, 0, 100, 1000), "scrollable=true"), 0),
            (3, 1, attrs("resource-id=logger_warmup_prep_X", bounds(0, 900, 50, 950)), 0),
        ])
        self.assertEqual("blocked", self._target().plan(ms.parse_hierarchy(text)).action)

    def test_ambiguous_target_is_blocked(self):
        text = hierarchy([
            (0, 0, "", None),
            (1, 1, attrs("resource-id=logger_list", bounds(0, 63, 1080, 1917), "scrollable=true"), 0),
            (2, 2, attrs("resource-id=logger_warmup_prep_X", bounds(0, 900, 50, 950)), 1),
            (3, 2, attrs("resource-id=logger_warmup_prep_X", bounds(0, 960, 50, 1000)), 1),
        ])
        self.assertEqual("blocked", self._target().plan(ms.parse_hierarchy(text)).action)

    def test_no_safe_gesture_region_is_blocked(self):
        text = logger_screen(
            picker_bounds=(0, 63, 1080, 1917),
            target_bounds=(74, 4000, 680, 4100),
        )
        self.assertEqual("blocked", self._target().plan(ms.parse_hierarchy(text)).action)

    def test_picker_moving_alone_is_not_progress(self):
        scroller = self._target(max_no_progress=2)
        near = logger_screen(picker_bounds=(42, 1071, 1038, 1544), target_bounds=(74, 4000, 680, 4100))
        far = logger_screen(
            picker_bounds=(42, 900, 1038, 1400),
            target_bounds=(74, 4000, 680, 4100),
            extra=[(6, 3, attrs("resource-id=logger_exercise_row", bounds(42, 910, 1038, 1000)), 3)],
        )
        self.assertEqual("swipe", scroller.plan(ms.parse_hierarchy(near)).action)
        self.assertEqual("swipe", scroller.plan(ms.parse_hierarchy(far)).action)
        self.assertEqual("failed", scroller.plan(ms.parse_hierarchy(near)).action)

    def test_container_content_moving_is_progress(self):
        scroller = self._target(max_no_progress=2)
        before = logger_screen(picker_bounds=(42, 1071, 1038, 1544), target_bounds=(74, 4000, 680, 4100))
        after = logger_screen(
            picker_bounds=(42, 1071, 1038, 1544),
            target_bounds=(74, 4000, 680, 4100),
            extra=[(6, 2, attrs("resource-id=logger_recent_set", bounds(42, 200, 1038, 260)), 1)],
        )
        scroller.plan(ms.parse_hierarchy(before))
        scroller.plan(ms.parse_hierarchy(after))
        self.assertEqual(0, scroller.no_progress_streak)

    def test_target_never_appears_fails_after_max_swipes(self):
        scroller = self._target(max_swipes=3, max_no_progress=99)
        # Content keeps moving (so no-progress never trips) but the target is never on screen.
        for shift in range(4):
            text = logger_screen(
                picker_bounds=(42, 1071, 1038, 1544),
                target_bounds=(74, 4000, 680, 4100),
                extra=[(6, 2, attrs("resource-id=logger_row", bounds(42, 200 + shift * 40, 1038, 260 + shift * 40)), 1)],
            )
            step = scroller.plan(ms.parse_hierarchy(text))
            if step.action == "swipe":
                scroller.swipes += 1
        self.assertEqual("failed", step.action)

    def test_absent_target_keeps_scrolling_then_fails(self):
        # An id that never appears (off-screen then exhausted) must fail bounded, not block: a scroll
        # container does not compose off-screen rows, so absence is not an error.
        scroller = self._target(max_no_progress=2)
        text = logger_screen(picker_bounds=(42, 1071, 1038, 1544), target_bounds=(74, 800, 680, 900))
        scroller.target_id = "logger_warmup_prep_MISSING"
        self.assertEqual("swipe", scroller.plan(ms.parse_hierarchy(text)).action)
        self.assertEqual("swipe", scroller.plan(ms.parse_hierarchy(text)).action)
        self.assertEqual("failed", scroller.plan(ms.parse_hierarchy(text)).action)

    def test_unknown_target_text_regex_is_rejected(self):
        # find_targets surfaces the compile error; main() pre-validates and returns EXIT_BLOCKED.
        with self.assertRaises(Exception):
            ms.find_targets([], None, "(?P<x>")
        with contextlib.redirect_stdout(io.StringIO()):
            code = ms.main(["--device", "emulator-5554", "--target-text", "(?P<x>"])
        self.assertEqual(ms.EXIT_BLOCKED, code)


class DriveTests(unittest.TestCase):
    def test_drive_reaches_done_and_inspects_fresh_each_time(self):
        texts = [
            logger_screen(picker_bounds=(42, 1071, 1038, 1544), target_bounds=(74, 4000, 680, 4100)),
            logger_screen(picker_bounds=(42, 1071, 1038, 1544), target_bounds=(74, 800, 680, 900)),
        ]
        calls = {"inspect": 0, "swipes": []}

        def inspect():
            calls["inspect"] += 1
            return texts[min(calls["inspect"] - 1, len(texts) - 1)]

        def swipe(gesture):
            calls["swipes"].append(gesture)
            return True

        scroller = ms.Scroller(target_id="logger_warmup_prep_X")
        step = ms.drive(scroller, inspect, swipe)
        self.assertEqual("done", step.action)
        self.assertEqual(2, calls["inspect"])
        self.assertEqual(1, len(calls["swipes"]))

    def test_drive_reports_timeout_when_hierarchy_is_unavailable(self):
        def inspect():
            return None

        step = ms.drive(ms.Scroller(target_id="logger_warmup_prep_X"), inspect, lambda gesture: True)
        self.assertEqual("timeout", step.action)

    def test_drive_reports_timeout_on_failed_swipe(self):
        text = logger_screen(picker_bounds=(42, 1071, 1038, 1544), target_bounds=(74, 4000, 680, 4100))
        step = ms.drive(ms.Scroller(target_id="logger_warmup_prep_X"), lambda: text, lambda gesture: False)
        self.assertEqual("timeout", step.action)

    def test_drive_respects_the_deadline(self):
        text = logger_screen(picker_bounds=(42, 1071, 1038, 1544), target_bounds=(74, 4000, 680, 4100))
        times = iter([0.0, 999.0])

        step = ms.drive(
            ms.Scroller(target_id="logger_warmup_prep_X"),
            lambda: text,
            lambda gesture: True,
            deadline=10.0,
            clock=lambda: next(times),
        )
        self.assertEqual("timeout", step.action)

    def test_no_swipe_when_inspection_exhausts_the_budget(self):
        # The gesture must never start once inspection has eaten the remaining budget.
        text = logger_screen(picker_bounds=(42, 1071, 1038, 1544), target_bounds=(74, 4000, 680, 4100))
        swipes = []
        times = iter([0.0, 999.0])
        step = ms.drive(
            ms.Scroller(target_id="logger_warmup_prep_X"),
            lambda: text,
            lambda gesture: swipes.append(gesture) or True,
            deadline=10.0,
            clock=lambda: next(times),
        )
        self.assertEqual("timeout", step.action)
        self.assertEqual([], swipes)

    def test_malformed_listing_is_not_usable(self):
        step = ms.drive(ms.Scroller(target_id="logger_warmup_prep_X"), lambda: hierarchy([]), lambda gesture: True)
        self.assertEqual("timeout", step.action)


class ClosureRegressionTests(unittest.TestCase):
    def test_identical_repeated_node_is_one_node(self):
        valid = logger_screen(picker_bounds=(42, 1071, 1038, 1544), target_bounds=(74, 800, 680, 900))
        repeated = valid + valid.splitlines()[-1] + "\n"
        self.assertEqual(ms.parse_hierarchy(valid), ms.parse_hierarchy(repeated))
        self.assertEqual("done", ms.Scroller(target_id="logger_warmup_prep_X").plan(ms.parse_hierarchy(repeated)).action)

    def test_malformed_hierarchies_never_succeed_or_swipe(self):
        valid = logger_screen(picker_bounds=(42, 1071, 1038, 1544), target_bounds=(74, 800, 680, 900))
        malformed = [
            valid.replace("element_num,depth,attributes,parent_num", "WRONG HEADER"),
            valid + "broken,row\n",
            valid.replace("1,1,", "1,bad,"),
            valid.replace("[0,63][1080,1917]", "[1080,63][0,1917]"),
            valid.replace("[0,63][1080,1917]", "not bounds"),
            valid + '1,1,"",0\n',
            valid + '6,2,"",999\n',
            valid + '6,2,"",6\n',
            valid + '6,2,"",7\n7,3,"",6\n',
            valid + '6,2,"",bad\n',
            valid + '6,2,"unterminated\n',
        ]
        for text in malformed:
            with self.subTest(text=text):
                swipe = mock.Mock(return_value=True)
                step = ms.drive(ms.Scroller(target_id="logger_warmup_prep_X"), lambda: text, swipe)
                self.assertEqual("timeout", step.action)
                swipe.assert_not_called()

    def test_visible_target_cannot_succeed_after_inspection_deadline(self):
        text = logger_screen(picker_bounds=(42, 1071, 1038, 1544), target_bounds=(74, 800, 680, 900))
        times = iter([0.0, 11.0])
        swipe = mock.Mock(return_value=True)
        step = ms.drive(ms.Scroller(target_id="logger_warmup_prep_X"), lambda: text, swipe,
                        deadline=10.0, clock=lambda: next(times))
        self.assertEqual("timeout", step.action)
        swipe.assert_not_called()

    def test_abbreviated_device_override_is_rejected_before_inspection(self):
        with mock.patch.object(ms, "make_inspector") as inspector:
            with contextlib.redirect_stderr(io.StringIO()), self.assertRaises(SystemExit) as error:
                ms.main(["--device", "emulator-5554", "--expected-device", "emulator-5554",
                         "--dev", "emulator-9999", "--expected-dev", "emulator-9999",
                         "--target-id", "target"])
            self.assertEqual(2, error.exception.code)
            inspector.assert_not_called()

    def test_obstructions_block_both_visible_and_missing_targets(self):
        obstructions = [
            attrs("resource-id=keyboard", bounds(0, 900, 1080, 1917)),
            attrs("class=android.inputmethodservice.KeyboardView"),
            attrs("resource-id=dialog", bounds(200, 500, 900, 1200)),
            attrs("resource-id=unknown-window", bounds(0, 900, 1080, 1917)),
        ]
        for obstruction in obstructions:
            for target in ("logger_warmup_prep_X", "absent"):
                with self.subTest(obstruction=obstruction, target=target):
                    text = logger_screen(picker_bounds=(42, 1071, 1038, 1544),
                                         target_bounds=(74, 800, 680, 900),
                                         extra=[(6, 1, obstruction, 0)])
                    swipe = mock.Mock(return_value=True)
                    step = ms.drive(ms.Scroller(target_id=target), lambda: text, swipe)
                    self.assertEqual("blocked", step.action)
                    swipe.assert_not_called()

    def test_system_bars_outside_list_do_not_block(self):
        text = logger_screen(picker_bounds=(42, 1071, 1038, 1544), target_bounds=(74, 800, 680, 900),
                             extra=[(6, 1, attrs(bounds(0, 0, 1080, 63)), 0),
                                    (7, 1, attrs(bounds(0, 1917, 1080, 2400)), 0)])
        self.assertEqual("done", ms.Scroller(target_id="logger_warmup_prep_X").plan(ms.parse_hierarchy(text)).action)


class InspectorTests(unittest.TestCase):
    def test_failed_inspection_output_is_never_consumed(self):
        # A failing hierarchy run can still emit partial bytes; exit status must win.
        text = logger_screen(picker_bounds=(42, 1071, 1038, 1544), target_bounds=(74, 800, 680, 900))

        def fake_run(cmd, stdout=None, **kwargs):
            if stdout is not None:
                stdout.write(text)
            return subprocess.CompletedProcess(cmd, 7)

        with tempfile.TemporaryDirectory() as tmp:
            inspector = ms.make_inspector("maestro", "emulator-5554", tmp, lambda: 10.0)
            with mock.patch.object(ms.subprocess, "run", side_effect=fake_run):
                self.assertIsNone(inspector())

    def test_inspector_refuses_to_run_without_budget(self):
        with tempfile.TemporaryDirectory() as tmp:
            inspector = ms.make_inspector("maestro", "emulator-5554", tmp, lambda: 0.0)
            with mock.patch.object(ms.subprocess, "run") as run:
                self.assertIsNone(inspector())
            run.assert_not_called()

    def test_swiper_refuses_to_run_without_budget(self):
        with tempfile.TemporaryDirectory() as tmp:
            swiper = ms.make_swiper("maestro", "emulator-5554", "com.hydrafit.app", tmp, lambda: 0.0)
            with mock.patch.object(ms.subprocess, "run") as run:
                self.assertFalse(swiper((1, 2, 3, 4)))
            run.assert_not_called()

    def test_swiper_reports_a_failed_run_and_writes_its_flow(self):
        with tempfile.TemporaryDirectory() as tmp:
            swiper = ms.make_swiper("maestro", "emulator-5554", "com.hydrafit.app", tmp, lambda: 10.0)
            with mock.patch.object(ms.subprocess, "run", return_value=subprocess.CompletedProcess([], 1)):
                self.assertFalse(swiper((1, 2, 3, 4)))
            self.assertTrue(os.path.exists(os.path.join(tmp, "swipe-1.yaml")))


class MainGuardTests(unittest.TestCase):
    def test_phone_serial_is_refused(self):
        with contextlib.redirect_stdout(io.StringIO()):
            code = ms.main(["--device", "RF8M1234ABC", "--target-id", "logger_list"])
        self.assertEqual(ms.EXIT_BLOCKED, code)

    def test_device_that_overrides_the_verified_emulator_is_refused(self):
        # argparse takes the last --device, so the mismatch guard is what keeps the verified target.
        with contextlib.redirect_stdout(io.StringIO()):
            code = ms.main(
                ["--device", "emulator-9999", "--expected-device", "emulator-5554", "--target-id", "logger_list"]
            )
        self.assertEqual(ms.EXIT_BLOCKED, code)


if __name__ == "__main__":
    unittest.main()
