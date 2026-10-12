#!/usr/bin/env python3
"""Verifies the committed Unicode case-fold table against the official Unicode data.

Usage:
  python3 scripts/check-case-fold-table.py <CaseFolding.txt>

`CaseFolding.txt` is published per Unicode release at
https://www.unicode.org/Public/<version>/ucd/CaseFolding.txt; the table in
`core/domain/src/commonMain/kotlin/com/hydrafit/app/core/domain/workout/UnicodeCaseFold.kt` was
generated from release 17.0.0 (`CaseFolding-17.0.0.txt`, statuses `C` and `F`, with `F` winning where
both apply for the same code point).

The check is semantic, not textual: it rebuilds every code point's folded target from the Kotlin
ranges and exceptions and compares that mapping with the data file, so it also catches a
transcription slip inside a delta range. Exits non-zero and prints the first mismatches on failure.
It reads the Kotlin source only; it never writes to the repository and is not part of the build.
"""

import re
import sys

KOTLIN_TABLE = (
    "core/domain/src/commonMain/kotlin/com/hydrafit/app/core/domain/workout/UnicodeCaseFold.kt"
)
RANGES_MARKER = "private fun foldRangeData(): List<String> ="
EXCEPTIONS_MARKER = "private fun foldExceptionData(): List<String> ="


def ucd_mapping(path):
    mapping = {}
    for line in open(path, encoding="utf-8"):
        body = line.split("#")[0].strip()
        if not body:
            continue
        parts = [part.strip() for part in body.split(";")]
        if len(parts) < 3:
            continue
        code_point, status, target = parts[0], parts[1], parts[2]
        if status not in ("C", "F"):
            continue
        source = int(code_point, 16)
        # `F` wins where a code point carries both a simple and a full fold.
        if status == "F" or source not in mapping:
            mapping[source] = tuple(int(value, 16) for value in target.split())
    return mapping


def raw_block(source, marker):
    start = source.index(marker)
    open_at = source.index('"""', start) + 3
    close_at = source.index('"""', open_at)
    return source[open_at:close_at]


def kotlin_mapping(path):
    source = open(path, encoding="utf-8").read()
    mapping = {}
    ranges = []
    for token in raw_block(source, RANGES_MARKER).split():
        span, delta = token.split(":")
        step = 1
        if "/" in span:
            span, step = span.split("/")
            step = int(step)
        start, end = span.split("-")
        start, end, delta = int(start, 16), int(end, 16), int(delta, 16)
        ranges.append((start, end, step, delta))
        for code_point in range(start, end + 1, step):
            mapping[code_point] = (code_point + delta,)
    exceptions = {}
    for token in raw_block(source, EXCEPTIONS_MARKER).split():
        source_code_point, target = token.split(":")
        exceptions[int(source_code_point, 16)] = tuple(int(v, 16) for v in target.split(","))
    if set(exceptions) & {point for start, end, step, _ in ranges
                          for point in range(start, end + 1, step)}:
        raise SystemExit("the exception table overlaps a delta range")
    mapping.update(exceptions)
    return mapping


def main():
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    expected = ucd_mapping(sys.argv[1])
    actual = kotlin_mapping(KOTLIN_TABLE)
    missing = sorted(set(expected) - set(actual))
    extra = sorted(set(actual) - set(expected))
    wrong = sorted(
        code_point
        for code_point in set(expected) & set(actual)
        if expected[code_point] != actual[code_point]
    )
    if missing or extra or wrong:
        print(f"FAIL: {len(expected)} Unicode entries, {len(actual)} table entries")
        for label, points in (("missing", missing), ("extra", extra), ("wrong", wrong)):
            if points:
                shown = ", ".join(f"U+{point:04X}" for point in points[:10])
                print(f"  {label} ({len(points)}): {shown}")
        return 1
    print(f"PASS: {len(expected)} code points match {KOTLIN_TABLE}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
