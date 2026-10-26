# 0.11.1 distance — review snapshot (2026-10-26)

Bounded post-execution review of the 0.11.1 distance slice on `release/0.11.0`.

## Scope

- Base `cb9ad315`, all work uncommitted: 144 modified paths plus 6 untracked files (contract
  `~/.local/state/hydrafit/0.11.1-release/contract.json` with three path amendments).
- Reviewed: exclusive measurement and every write/validation boundary; prescription snapshot and
  backup gates; schema `61.sqm`; metres/feet conversion; progression, e1RM, volume and warm-up
  exclusion; routines and SplitBuilder authoring; Logger manual/correction/draft; history, summary
  and progress rendering; CSV, interchange v6 and PDF portability.
- Evidence: `0111-full-matrix-final-4.log` (409 executed / 0 up-to-date, host suite +
  `verifySqlDelightMigration`), `0111-assemble-ios-final-2.log` (397 executed / 0 up-to-date), scoped
  ktlint pass/check logs, plus the earlier coder matrix and reruns.

## Full-range verdict: REJECTED (one blocker)

| Id | Finding | Classification | Resolution |
| --- | --- | --- | --- |
| R111-01 | `BackupValidator.prescriptionSnapshot` compared performed values to the prescription target, so any corrected or missed set made a whole backup fail validation | mechanical, blocker | **CLOSED** — comparison is now measurement *kind*; regression covers a missed rep, a shortened timed set and a partly completed carry |
| R111-02 | CSV import now rejects rows carrying reps plus a positive distance, and a distance with a blank unit | design decision | **accepted** — matches the contract's "reject mixed metric rows" and the domain exclusivity rule; recorded in `docs/qa.md` |
| R111-03 | Progression exclusion for distance was implicit and untested | mechanical, nit | **CLOSED** — explicit `distanceMeters == null` guard and a slot-level exclusion test |
| R111-04 | Routines distance error said "zero or positive" while zero is rejected | mechanical, nit | **CLOSED** — copy now says "greater than zero" |
| R111-05 | New `DistanceUnitRepository?` collaborators default to metres | design decision, nit | **accepted** — every production module binds it; the default only serves test construction |
| R111C-02 | The explicit distance guard in `isComparableWith` is unreachable (a valid snapshot cannot carry reps and distance) | mechanical, nit | **accepted** — kept as the documented comparability invariant |
| R111C-04 | No negative coverage for the distance direction of the backup kind check | mechanical, nit | **CLOSED** — both orientations asserted |

## Decisions recorded during review

- A prescription target and the performed set may differ in value; only the measurement kind must
  agree. Recorded targets describe the prescription, not the outcome.
- A correction that changes a set's measurement kind (reps ↔ seconds ↔ distance) clears the recorded
  target inside the same transaction, mirroring the Logger rule that a differently measured set
  captures no target. User decision 2026-10-26.
- CSV import stays strict about mixed-metric rows rather than importing the rep side and dropping
  distance.

## Closure

Targeted closure after the corrections returned **CLOSED** for R111-01, R111C-03 and R111C-04, with
R111-02/R111-05/R111C-02 dispositions accepted. One informational item remains:

- **R111D-01** — a backup exported by an unreleased pre-correction 0.11.0 developer build could hold a
  kind-mismatched set/target pair and would now fail restore validation; no published version can
  produce that shape. Recorded as a limit in `docs/qa.md`, not a code change.

## Unverified at closure

- No emulator or device acceptance for any distance surface.
- On-device `61.sqm` upgrade not exercised; device backup restore/merge carrying distance unverified.
- A `:shared:testAndroidHostTest` Main-dispatcher ordering race was observed twice and passed on three
  reruns and the final matrix; it predates this slice and was not modified.
