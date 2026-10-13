#!/usr/bin/env python3
"""Container-aware, bounded scrolling for a HydraFit Maestro hierarchy.

Finds a target element by resource id or text, then scrolls its tagged outer
container until the target is visible, deriving each swipe from the *fresh*
hierarchy and excluding nested scroll containers (for example the Logger's
exercise picker). It only inspects and swipes: it never taps, logs a set,
restarts the app or clears state, so it cannot trigger a write.

Exit codes:
    0  target is visible (possibly after swiping)
    2  blocked (missing/duplicate container, ambiguous target, no safe gesture region)
    3  failed (target never became visible within the swipe/progress bounds)
    4  timeout or a hierarchy/inspection failure
"""

from __future__ import annotations

import argparse
import csv
import io
import os
import re
import subprocess
import sys
import tempfile
import time
from dataclasses import dataclass
from typing import Callable, Iterable, Optional

DEFAULT_CONTAINER_ID = "logger_list"
DEFAULT_APP_ID = "com.hydrafit.app"
DEFAULT_MAX_SWIPES = 8
DEFAULT_MAX_NO_PROGRESS = 2
DEFAULT_TIMEOUT_SECONDS = 120.0
MIN_GESTURE_PIXELS = 120
SWIPE_DURATION_MS = 400
SCROLL_MARGIN_PX = 8

EXIT_OK = 0
EXIT_BLOCKED = 2
EXIT_FAILED = 3
EXIT_TIMEOUT = 4

BOUNDS_RE = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")
Gesture = tuple[int, int, int, int]


@dataclass(frozen=True)
class Rect:
    left: int
    top: int
    right: int
    bottom: int

    @property
    def height(self) -> int:
        return self.bottom - self.top

    @property
    def width(self) -> int:
        return self.right - self.left


@dataclass(frozen=True)
class Row:
    index: int
    depth: int
    attrs: dict[str, str]
    parent: Optional[int]

    @property
    def bounds(self) -> Optional[Rect]:
        return parse_bounds(self.attrs.get("bounds", ""))

    @property
    def resource_id(self) -> Optional[str]:
        return self.attrs.get("resource-id") or None

    @property
    def text(self) -> Optional[str]:
        return self.attrs.get("text") or None

    @property
    def scrollable(self) -> bool:
        return self.attrs.get("scrollable") == "true"


@dataclass
class ScrollStep:
    """One decision for the current observation: done, swipe, blocked or failed."""

    action: str
    reason: str = ""
    gesture: Optional[Gesture] = None


def parse_bounds(value: str) -> Optional[Rect]:
    match = BOUNDS_RE.fullmatch(value.strip())
    if not match:
        return None
    x1, y1, x2, y2 = (int(group) for group in match.groups())
    return Rect(x1, y1, x2, y2)


def parse_hierarchy(csv_text: str) -> list[Row]:
    """Parse the `element_num,depth,attributes,parent_num` hierarchy listing."""
    rows: list[Row] = []
    reader = csv.reader(io.StringIO(csv_text))
    saw_header = False
    for record in reader:
        if not record or all(not cell.strip() for cell in record):
            continue
        if not saw_header:
            # The first non-empty record is the column header, not a node.
            saw_header = True
            continue
        if len(record) < 4:
            continue
        element = record[0].strip()
        if not element.isdigit():
            continue
        attrs: dict[str, str] = {}
        for part in record[2].split(";"):
            part = part.strip()
            if not part or "=" not in part:
                continue
            key, _, value = part.partition("=")
            attrs[key.strip()] = value.strip()
        parent_raw = record[3].strip()
        rows.append(
            Row(
                index=int(element),
                depth=int(record[1].strip() or 0),
                attrs=attrs,
                parent=int(parent_raw) if parent_raw.isdigit() else None,
            )
        )
    return rows


def find_targets(
    rows: Iterable[Row], target_id: Optional[str], target_text: Optional[str]
) -> list[Row]:
    if target_id is not None:
        return [row for row in rows if row.resource_id == target_id]
    pattern = re.compile(target_text or "", re.IGNORECASE)
    return [row for row in rows if row.text and pattern.search(row.text)]


def _children_by_parent(rows: Iterable[Row]) -> dict[int, list[int]]:
    children: dict[int, list[int]] = {}
    for row in rows:
        if row.parent is not None:
            children.setdefault(row.parent, []).append(row.index)
    return children


def descendants(rows: list[Row], root_index: int) -> list[int]:
    children = _children_by_parent(rows)
    out: list[int] = []
    stack = list(children.get(root_index, []))
    while stack:
        index = stack.pop()
        out.append(index)
        stack.extend(children.get(index, []))
    return out


def nested_scrollables(rows: list[Row], container: Row) -> list[Row]:
    by_index = {row.index: row for row in rows}
    found = []
    for index in descendants(rows, container.index):
        row = by_index[index]
        if row.scrollable and row.bounds is not None and row.bounds.height > 0:
            found.append(row)
    return found


def excluded_indexes(rows: list[Row], nested: Iterable[Row]) -> set[int]:
    """The nested scrollables and everything inside them: their own content moves."""
    excluded: set[int] = set()
    for row in nested:
        excluded.add(row.index)
        excluded.update(descendants(rows, row.index))
    return excluded


def is_visible(target: Row, container: Row) -> bool:
    target_bounds = target.bounds
    container_bounds = container.bounds
    if target_bounds is None or container_bounds is None:
        return False
    return (
        target_bounds.top >= container_bounds.top
        and target_bounds.bottom <= container_bounds.bottom
        and target_bounds.left >= container_bounds.left
        and target_bounds.right <= container_bounds.right
    )


def subtract_intervals(interval: tuple[int, int], blocks: Iterable[tuple[int, int]]) -> list[tuple[int, int]]:
    low, high = interval
    clips = []
    for block_low, block_high in blocks:
        a, b = max(low, block_low), min(high, block_high)
        if a < b:
            clips.append((a, b))
    clips.sort()
    merged: list[tuple[int, int]] = []
    for a, b in clips:
        if merged and a <= merged[-1][1]:
            merged[-1] = (merged[-1][0], max(merged[-1][1], b))
        else:
            merged.append((a, b))
    out: list[tuple[int, int]] = []
    cursor = low
    for a, b in merged:
        if a > cursor:
            out.append((cursor, a))
        cursor = max(cursor, b)
    if cursor < high:
        out.append((cursor, high))
    return out


def safe_bands(container: Row, nested: Iterable[Row], x: int) -> list[tuple[int, int]]:
    """Vertical bands of the container at column `x` that no nested scrollable covers."""
    bounds = container.bounds
    if bounds is None:
        return []
    blocks = []
    for row in nested:
        nested_bounds = row.bounds
        if nested_bounds is None:
            continue
        if nested_bounds.left <= x <= nested_bounds.right:
            blocks.append((nested_bounds.top, nested_bounds.bottom))
    return subtract_intervals((bounds.top, bounds.bottom), blocks)


def choose_gesture(container: Row, nested: Iterable[Row], direction: str = "down") -> Optional[Gesture]:
    """A swipe entirely inside a band that excludes every nested scrollable, or None."""
    bounds = container.bounds
    if bounds is None or bounds.height <= 0:
        return None
    x = (bounds.left + bounds.right) // 2
    bands = safe_bands(container, nested, x)
    if not bands:
        return None
    low, high = max(bands, key=lambda band: band[1] - band[0])
    if high - low < MIN_GESTURE_PIXELS:
        return None
    if direction == "up":
        return (x, low + SCROLL_MARGIN_PX, x, high - SCROLL_MARGIN_PX)
    return (x, high - SCROLL_MARGIN_PX, x, low + SCROLL_MARGIN_PX)


def content_signature(rows: list[Row], container: Row, excluded: set[int]) -> tuple:
    """Signature of the container's own content, ignoring nested scrollables' content."""
    by_index = {row.index: row for row in rows}
    parts = []
    for index in descendants(rows, container.index):
        if index in excluded:
            continue
        row = by_index[index]
        bounds = row.bounds
        if bounds is None:
            continue
        parts.append((row.resource_id or row.text or row.attrs.get("class", ""), bounds.top, bounds.bottom))
    return tuple(parts)


class Scroller:
    """Pure decision state: feed it observations, read back the next action."""

    def __init__(
        self,
        container_id: str = DEFAULT_CONTAINER_ID,
        target_id: Optional[str] = None,
        target_text: Optional[str] = None,
        max_swipes: int = DEFAULT_MAX_SWIPES,
        max_no_progress: int = DEFAULT_MAX_NO_PROGRESS,
        direction: str = "down",
    ) -> None:
        self.container_id = container_id
        self.target_id = target_id
        self.target_text = target_text
        self.max_swipes = max_swipes
        self.max_no_progress = max_no_progress
        self.direction = direction
        self.swipes = 0
        self.no_progress_streak = 0
        self._last_signature: Optional[tuple] = None
        self._observed_once = False

    def plan(self, rows: list[Row]) -> ScrollStep:
        containers = [row for row in rows if row.resource_id == self.container_id]
        if len(containers) != 1:
            return ScrollStep("blocked", f"expected one {self.container_id}, found {len(containers)}")
        container = containers[0]

        targets = find_targets(rows, self.target_id, self.target_text)
        if len(targets) > 1:
            return ScrollStep("blocked", f"ambiguous target, found {len(targets)}")
        # A scroll container composes only what is on screen, so the target is normally absent until
        # it scrolls into view: absence means "keep going", never "blocked".
        if len(targets) == 1 and is_visible(targets[0], container):
            return ScrollStep("done", "target is visible")

        nested = nested_scrollables(rows, container)
        signature = content_signature(rows, container, excluded_indexes(rows, nested))
        if self._observed_once and signature == self._last_signature:
            self.no_progress_streak += 1
        else:
            self.no_progress_streak = 0
        self._last_signature = signature
        self._observed_once = True

        if self.swipes >= self.max_swipes:
            return ScrollStep("failed", f"target not visible after {self.swipes} swipes")
        if self.no_progress_streak >= self.max_no_progress:
            return ScrollStep("failed", "the container stopped moving")

        gesture = choose_gesture(container, nested, self.direction)
        if gesture is None:
            return ScrollStep("blocked", "no safe gesture region inside the container")
        return ScrollStep("swipe", "scrolling toward the target", gesture)


def drive(
    scroller: Scroller,
    inspect: Callable[[], Optional[str]],
    swipe: Callable[[Gesture], bool],
    deadline: Optional[float] = None,
    clock: Callable[[], float] = time.monotonic,
) -> ScrollStep:
    """Run the observe/decide/swipe loop under the deadline. `inspect` returns fresh text or None."""
    while True:
        if deadline is not None and clock() > deadline:
            return ScrollStep("timeout", "time budget exhausted")
        csv_text = inspect()
        if csv_text is None:
            return ScrollStep("timeout", "hierarchy inspection failed")
        step = scroller.plan(parse_hierarchy(csv_text))
        if step.action != "swipe":
            return step
        if not swipe(step.gesture):
            return ScrollStep("timeout", "swipe failed")
        scroller.swipes += 1


def _maestro_env() -> dict[str, str]:
    env = dict(os.environ)
    env["MAESTRO_CLI_NO_ANALYTICS"] = "1"
    env["MAESTRO_CLI_ANALYSIS_NOTIFICATION_DISABLED"] = "true"
    return env


def _run(cmd: list[str], timeout: float) -> Optional[subprocess.CompletedProcess]:
    try:
        return subprocess.run(cmd, capture_output=True, text=True, timeout=max(1.0, timeout), env=_maestro_env())
    except (subprocess.TimeoutExpired, OSError):
        return None


def make_inspector(maestro: str, device: str, artifacts_dir: str, remaining: Callable[[], float]):
    """A fresh hierarchy file per call, so a stale listing can never be reused."""
    counter = {"n": 0}

    def inspect() -> Optional[str]:
        counter["n"] += 1
        path = os.path.join(artifacts_dir, f"hierarchy-{counter['n']}.csv")
        cmd = [maestro, "--device", device, "hierarchy", "--no-reinstall-driver", "--compact"]
        with open(path, "w") as handle:
            try:
                subprocess.run(
                    cmd,
                    stdout=handle,
                    stderr=subprocess.DEVNULL,
                    text=True,
                    timeout=max(1.0, remaining()),
                    env=_maestro_env(),
                )
            except (subprocess.TimeoutExpired, OSError):
                return None
        try:
            with open(path) as handle:
                return handle.read()
        except OSError:
            return None

    return inspect


def make_swiper(maestro: str, device: str, app_id: str, artifacts_dir: str, remaining: Callable[[], float]):
    counter = {"n": 0}

    def swipe(gesture: Gesture) -> bool:
        counter["n"] += 1
        x1, y1, x2, y2 = gesture
        flow_path = os.path.join(artifacts_dir, f"swipe-{counter['n']}.yaml")
        with open(flow_path, "w") as handle:
            handle.write(
                f"appId: {app_id}\n---\n- swipe:\n"
                f"    start: \"{x1}, {y1}\"\n"
                f"    end: \"{x2}, {y2}\"\n"
                f"    duration: {SWIPE_DURATION_MS}\n"
            )
        out_dir = os.path.join(artifacts_dir, f"swipe-out-{counter['n']}")
        cmd = [maestro, "--device", device, "test", "--no-reinstall-driver", "--test-output-dir", out_dir, flow_path]
        result = _run(cmd, remaining())
        return result is not None and result.returncode == 0

    return swipe


def main(argv: Optional[list[str]] = None) -> int:
    parser = argparse.ArgumentParser(description="Container-aware, bounded Maestro scrolling.")
    parser.add_argument("--device", required=True, help="explicit emulator serial, e.g. emulator-5554")
    parser.add_argument("--maestro", default=os.environ.get("MAESTRO_BIN", os.path.expanduser("~/.maestro/bin/maestro")))
    parser.add_argument("--app-id", default=DEFAULT_APP_ID)
    parser.add_argument("--container-id", default=DEFAULT_CONTAINER_ID)
    target = parser.add_mutually_exclusive_group(required=True)
    target.add_argument("--target-id", help="resource id of the element to reveal")
    target.add_argument("--target-text", help="regular expression matched against element text")
    parser.add_argument("--direction", choices=["down", "up"], default="down")
    parser.add_argument("--max-swipes", type=int, default=DEFAULT_MAX_SWIPES)
    parser.add_argument("--max-no-progress", type=int, default=DEFAULT_MAX_NO_PROGRESS)
    parser.add_argument("--timeout", type=float, default=DEFAULT_TIMEOUT_SECONDS)
    parser.add_argument("--artifacts-dir", default=None)
    args = parser.parse_args(argv)

    artifacts_dir = args.artifacts_dir or tempfile.mkdtemp(prefix="hydrafit-scroll-", dir=tempfile.gettempdir())
    os.makedirs(artifacts_dir, exist_ok=True)

    if args.target_text is not None:
        try:
            re.compile(args.target_text)
        except re.error as error:
            print(f"BLOCKED: invalid --target-text regex: {error}")
            return EXIT_BLOCKED

    deadline = time.monotonic() + max(1.0, args.timeout)
    remaining = lambda: deadline - time.monotonic()

    scroller = Scroller(
        container_id=args.container_id,
        target_id=args.target_id,
        target_text=args.target_text,
        max_swipes=args.max_swipes,
        max_no_progress=args.max_no_progress,
        direction=args.direction,
    )
    inspect = make_inspector(args.maestro, args.device, args.artifacts_dir or artifacts_dir, remaining)
    swipe = make_swiper(args.maestro, args.device, args.app_id, artifacts_dir, remaining)

    step = drive(scroller, inspect, swipe, deadline=deadline)
    print(f"--device {args.device} container {args.container_id} swipes {scroller.swipes} artifacts {artifacts_dir}")
    if step.action == "done":
        print(f"OK: {step.reason}")
        return EXIT_OK
    if step.action == "blocked":
        print(f"BLOCKED: {step.reason}")
        return EXIT_BLOCKED
    if step.action == "failed":
        print(f"FAILED: {step.reason}")
        return EXIT_FAILED
    print(f"TIMEOUT: {step.reason}")
    return EXIT_TIMEOUT


if __name__ == "__main__":
    sys.exit(main())
