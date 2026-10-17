# 0.9.5 merge restore — review snapshot (2026-10-17)

Scope: release `0.9.5` (merge restore, OF-01 extension) of the accepted
0.9.5–0.9.6 batch contract. Reviewed range `94f36361..abf0eded`; implementation
commits `65c8efac`, `54f1edbc`, `0904c743`, `68a4b460`, `1c7d51ac`, `600b18d4`.
Reviewer: independent read-only (`reviewer`), non-executing.

## Verdict

**APPROVED WITH FINDINGS.** No schema, dependency, module-graph or CI change was
present, and the contract's merge semantics, staged apply, database additivity
and rollback, feature wiring and tests were verified against source.

## Findings and closure

| ID | Severity | Classification | Finding | Closure |
| --- | --- | --- | --- | --- |
| HF-095-01 | low | mechanical | `keptLocalSessions` counted open sessions skipped from the file, overcounting "kept local" while also reporting them as skipped | CLOSED — count is `file.size − sessionsNotLocal`; regression tests for all four local/open combinations (`3702f61a`) |
| HF-095-02 | low–medium | mechanical | merged sets' dropped occurrence links were computed but never disclosed, contrary to the contract | CLOSED — `droppedOccurrenceLinks` surfaced in the preview with its own string and a ViewModel test (`4497091b`) |
| HF-095-03 | low | mechanical (claim) | `skippedUnattachedSets` and its line are unreachable: the pre-existing validator rejects any file containing a session-less set before selection | CLOSED WITH NOTE — recorded here and in `PLANS.md`; counter kept as defense-in-depth; the accepted contract bullet is not rewritten |
| HF-095-04 | low | mechanical | a file custom exercise whose id equals a local seeded exercise id hit a primary-key conflict and aborted the whole merge | CLOSED — merge keys now carry every local exercise id; policy keeps the local row; policy- and database-level regression tests (`3702f61a`) |
| HF-095-05 | low | design-leaning (copy) | "Not merged from this file:" read exhaustive while equipment, reminders, goal and other setup data also stay local | CLOSED WITH NOTE — static "other setup preferences also stay as they are" line; wording granularity accepted as-is (`4497091b`) |
| HF-095-06 | low | mechanical | the import-time identity read ran on the caller (main) dispatcher | CLOSED — `PreviewMergeUseCase` wraps the computation in an injectable dispatcher, bound with `Dispatchers.Default`; the feature test helper injects the test dispatcher (`3702f61a`, `4497091b`) |

Targeted closure at `4497091b`: **APPROVED — all six findings closed (two with
notes)**, no blocking new findings.

## Non-blocking observations (recorded, no action)

- **N-1:** `keptLocalRows` is not rendered anywhere, so an insert-if-absent row
  kept local is silent — consistent with the existing unrendered handling of
  those collections.
- **N-2:** the `mergeKeys` assertions were loosened to `contains(...)`, so no test
  pins the exact id set for that method.
- **N-3:** `BackupMergeKeys.customExerciseIds` now holds all local exercise ids
  (seeded and custom); the KDoc says so but the name is narrower than its
  contents.

## Evidence

- `integration-gate.log` at `4497091b`: `BUILD SUCCESSFUL` for
  `testAndroidHostTest`, `:core:database:verifySqlDelightMigration`,
  `ktlintCheck`, `:androidApp:assembleDebug` and both iOS simulator compiles.
- Per-suite results: `BackupMergePolicyTest` (11), `SqlDelightBackupRepositoryTest`
  (34), settings module 96 tests / 0 failures.
- Device acceptance and its limits: `docs/qa.md` ("0.9.5 merge restore — device
  acceptance").

## Limits

- The gate log marks test/migration/ktlint tasks UP-TO-DATE by input hash; it
  proves the corrected inputs previously passed, and per-suite XMLs carry the
  counts, but the log alone has no per-test results.
- The two disclosure lines added by the corrections were device-unverified at
  closure; they are covered by the 0.9.6 device pass (single final-candidate
  install for both folded scopes).
- iOS remains compile-only; HF-095-03's unreachability is established from source
  and unit tests, not by an end-to-end attempt.
- This snapshot grants no fix, commit, push or publication approval.
