# 0.11.2 code review — force/device and EX-02-A added-load progression

Reviewer: `reviewer` subagent on `deepseek/deepseek-flash#high`, read-only, 2026-10-26.
Base: `release/0.11.0` at `688a7f3f`; scope: the uncommitted 0.11.2 working-tree delta.
This record is written by the parent from the reviewer's returned verdict and findings; it is not a
reviewer-authored report.

## First full-range verdict: REJECTED

Two defects blocked, both in the write path rather than the model.

| ID | Class | What was wrong |
| --- | --- | --- |
| R112-01 | mechanical, blocker | `SqlDelightWorkoutLogRepository.add` guarded a recorded target with `(target.reps != null) == (set.durationSeconds == null)`. That expression is true only for reps and timed work, so **every** distance and force set carrying a target was rejected before the snapshot was encoded. Introduced with the measurement work, invisible because every logger test used a fake repository. |
| R112-02 | mechanical, major | Switching an accepted-plan or draft slot to Force kept `suggestedWeightKg`, producing a force prescription that carries a load, which routines, the bounds and the interchange all forbid. |
| R112-03 | design, decided by the user | A slot's ordered per-set targets survived a measure change, and `followsTarget` matches load kind but not measure, so a repr-targeted slot switched to force was logged as reps and the force reading was discarded. Decided 2026-10-26: clear the targets with the measure change. |
| R112-04 | mechanical, minor | Backup prescription validation checked exactly-one-measure but not force/weight exclusivity, so a format-27 file could restore a load-bearing force slot; and no force/device case existed in the backup validator, round-trip or merge tests, while `docs/qa.md` implied a round trip that did not exist. |
| R112-05 | mechanical, minor | `LogWorkoutSetUseCase.logInto` omitted `requireValidDevice`, so a device on a non-force set was rejected on the first backdated set and accepted on later ones. |
| R112-06 | mechanical, nit | A progress-calculator comment claimed force contributes to the day trend's value totals; it contributes only the set count. |

Verified accepted without change: force exclusivity and load-freedom, bounds on every authoring path,
`62.sqm` and the format-27/v7 gates, correction atomicity, the EX-02-A rule as a whole (no rep or
body-mass estimate, no deload scaling, no cross-kind judgement, no model number, no schema change),
the transfer surfaces, and the docs that were already written.

## Corrections applied

- R112-01: the guard compares presence across all four measures, so the target's values may differ
  from the performed work while the kind must agree. Real-repository tests cover a 250 N target on a
  240 N set and a 40 m target on a 38 m set, and keep the foreign-measure rejections.
- R112-02: `PlanHistory.sq` clears `suggestedWeightKg` when the new measure is force,
  `SetAcceptedExerciseMeasureUseCase` returns the matching cleared slot, and the draft
  `WeeklyPlan.withMeasure` mirrors it.
- R112-03: ordered targets survive only a reps measure at routines save, accepted-plan edit, draft
  edit and occurrence edit; the draft-measure editor cannot change a frozen measure, and guided
  logging already treats a measure difference as an override.
- R112-04: `prescriptionMetric` takes the slot's load and rejects a force slot carrying one, with
  validator, merge and real export→validate→restore tests at format 27.
- R112-05: `logInto` now enforces the device rule.
- R112-06: the comment matches the code.

## Targeted closure: CLOSED WITH NEW FINDINGS

The reviewer confirmed all six fixed and confirmed the regression checks (reps edit keeps load and
targets; timed/distance edit keeps a legitimate load; a target whose values differ is still accepted;
`prescriptionMetric` call sites pass the right field) except one evidence gap.

| ID | Class | Outcome |
| --- | --- | --- |
| R112C-01 | mechanical, non-blocking | The SQL keep-branches were verified only by reading. Closed with a real-repository assertion that a reps edit keeps the load and the ordered targets and a timed edit keeps the load, plus an occurrence test that a reps-preserving edit keeps its targets. |

## Verification after corrections

- `0112-r112-tests-3.log`: domain, database, routines and SplitBuilder host tests, 104 tasks
  executed, 0 up-to-date.
- `0112-full-matrix-3.log` and `0112-full-matrix-4.log`: full host suite, `verifySqlDelightMigration`
  and ktlint for every module, 644 tasks executed and 0 up-to-date in each run.
- `0112-assemble-ios-2.log`: `:androidApp:assembleDebug`, `:shared:compileKotlinIosSimulatorArm64`
  and `:core:network:compileKotlinIosSimulatorArm64` all executed.
- `0112-r112c-tests-1/2.log`: the closure test additions, first caught a fixture error (one target
  for a three-set slot), then green.

## Limits

No emulator acceptance for any force or added-load surface. The on-device `62.sqm` upgrade and a
device backup restore/merge carrying force remain unverified. Every backup and interchange test runs
inside one process against the real repositories, not against a second install. Nothing here is
publication approval.
