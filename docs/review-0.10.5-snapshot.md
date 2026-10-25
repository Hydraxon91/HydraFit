# 0.10.5 independent review — 2026-10-25

## Scope and verdict

**REJECTED** by independent `reviewer` session `ses_e8c21bafbffexuA4sMR2tPCUeZ`.
Base `54233af99bcb37aba08925c78ac86b386fec56ba`, HEAD
`be0c30432500eada080e52e4921126c84362d459`, including the uncommitted LF-08/LF-11
delta and new `PlannerLoadRoundingTest.kt`. Source inspection completed; this is
not a release approval. The full reviewer response remains in that session.

Tracked review-delta SHA-256:
`30f60f45780be19e0caf4bd14e1e90f9062875efa649083a8b11312fe4bc8776`.
The preserved delta is outside the checkout at
`/Users/hydraxon/.opencode/state/0.10.5/pre-review-preserved.diff`.

## Findings

All findings are source-confirmed, not runtime reproductions. No corrections or
closure checks have been performed after this checkpoint.

| ID | Severity / classification | Evidence and expected correction |
| --- | --- | --- |
| R105-01 | Major / mechanical | `BuildPlannerLoadInputsUseCase.kt:61–72` drops all ≤2-rep PRs before target reps are known. Approved exclusion applies to ≥3-rep prescriptions; preserve 1/2-rep target eligibility, including mixed ordered targets. No existing record is deleted. |
| R105-02 | Major / mechanical | `WorkoutLoggerViewModel.kt:1207–1214,1333–1339` retains applied weight text but reparses it using current units. KG→LB can turn 120 kg into about 54.43 kg. Preserve canonical load/input-unit meaning; new measurement integrity is affected. |
| R105-03 | Major / mechanical | `WorkoutLoggerViewModel.kt:1131–1150,1193–1195,1909–1918,1962–1967`: reopening loses applied effort/time metadata, Reset reuses the edited source, and an entry-ID-only overlay survives exercise replacement. Preserve metadata and expire/reset against the actual pending source identity. New logged data may be wrong. |
| R105-04 | Major / mechanical | `WeeklyPlanSanitizer.kt:60–83` passes an already-rounded progression bound to inventory resolution. A raw 27.1575 kg bound can yield 27.5 kg instead of 25 kg. Inventory parity needs raw bounds, including deload/LB tests. |
| R105-05 | Major / mechanical | `PlannerLoadRounding.kt:75–84` explicit-ID branch ignores required equipment, permitting a bar grid on a dumbbell replacement. Reject incompatible implement kinds without borrowing another grid; test both replacement consumers and ordered targets. |
| R105-06 | Major / mechanical, pre-existing omission | `ReplaceOccurrenceExerciseUseCase.kt:145–167` computes candidate provenance but returns `explanation = null`. Reviewer requires new candidate provenance through atomic occurrence persistence; old-explanation inheritance remains forbidden. The earlier chunk brief explicitly retained null; reconcile that conflicting brief against approved provenance scope before editing. |
| R105-07 | Minor / incomplete approved presentation | `OccurrenceReplacementDialog.kt:57–73`, `WorkoutLoggerScreen.kt:2049–2053,2741–2763` omit known dumbbell basis on Logger replacement/guided targets. SplitBuilder alone is insufficient. Additional file permissions require an approved scope amendment. |
| R105-08 | Minor / mechanical | `DeterministicWorkoutPlannerEngine.kt:413–416`, `SplitBuilderViewModel.kt:1036–1055`: no reachable inventory yields no number and no reason; numeric-only disclosure misses all-unreachable and ordered-only cases. Missing inventory, unreachable inventory and insufficient history must remain distinguishable. |
| R105-09 | Major / mechanical, relevant pre-existing defect | `WorkoutLoggerViewModel.kt:1715–1728,2628–2632`: guided write sets draft count to one before ordered-offset calculation, choosing the final target repeatedly. Separate submission count from remaining-target position. Incorrect new measurements and target indices have data-integrity impact. |
| R105-10 | Minor / **design decision** | `WorkoutLoggerUiState.kt:454–477`, `WorkoutLoggerViewModel.kt:2414–2420`: Start A → Skip A leaves started A owning actions despite next-turn selection moving to B. Decide whether Skip cancels A's live timing, preserves resumable timing, or is unavailable until cancellation. No direct data loss. |
| R105-11 | Major / mechanical | `OccurrenceLifecycleUseCases.kt:71–77,167–175`, `SqlDelightWorkoutScheduleRepository.kt:339–346`: warm-up-only/unattributed-work skip becomes SKIPPED and bypasses closure. Distinguish recorded session-owning work from prescribed-working-set completion; preserve untouched/foreign sessions. Session boundary integrity is affected. |

## Verification and limits

- Fresh matrix: `0105-full-matrix.log`, 113.5 seconds, exit 0,
  **787 tasks executed, zero inherited**. Host tests, migration verification,
  ktlint, debug assembly and both required iOS compiles executed.
- Relevant XML suites inspected by reviewer had zero failures/errors/skips;
  passing tests did not cover the confirmed failures above.
- Log root:
  `/private/var/folders/hc/1f1kkpq52q93p0bwfqr9zbrr0000gn/T/opencode/`.
- Required prepared runner acceptance and viewed screenshots remain absent.
  Parent installed/launched the debug candidate and navigated to Log on
  `emulator-5554`; no fixture or workout-set writes were made.
- DB inventory-producer assertion and explicit-ID replacement-consumer tests
  remain missing. Tooling validator/264 Python tests are coder-reported, not
  independently verified from retained logs by the reviewer.
- No push/publication, no release-ready verdict. Further work requires the
  R105-10 decision, scope reconciliation/amendments where necessary, bounded
  corrections and independent targeted closure. Data-integrity finding R105-09
  invokes the documented review reopen criteria if its correction materially
   broadens the reviewed scope.

## Correction authorization — 2026-10-25

The user selected option 1 for R105-10: Skip cancels the started member's live
timing without logging, transfers the turn to the partner, and requires a fresh
Start when that member returns. The user also approved adding
`OccurrenceReplacementDialog.kt` to scope and recording the candidate's new
occurrence-replacement explanation (R105-06), superseding the earlier null rule.

R105-01–05, R105-07–09 and R105-11 are localized mechanical restorations of the
approved contract; corrections and regressions remain coder-owned. Split into
serialized domain/data/presentation plumbing (01/04/05/06/08/11), then Logger
lifecycle/ordered-write/UI (02/03/07/09/10). Parent owns status/QA consolidation.
Each group runs scoped formatting, separate lint, affected producer/consumer
tests; the stable combined tree requires a fresh full matrix, UI acceptance and
independent targeted closure. No finding is marked closed by this authorization.

## Targeted closure attempts — 2026-10-25

The original reviewer performed two targeted closures after the first corrective
group, without a new full-range sweep. Both returned **REJECTED**. The latest
inspected tracked-delta SHA-256 was
`26a1c2ecb2169c40ad06c38d972a11a789e682f5c9027f016775bf997c375e13`.

- Source/host closure: R105-02/04/05/09. R105-06/07/10/11 are source-corrected
  with outstanding visual/documentation checks. R105-03 is source-corrected but
  lacks the exact Apply-overlay → manual scalar Log consumption regression.
- **R105-01 remains open (major, mechanical):** the latest no-inventory ordered
  low-rep-floor recalculation at `PlannerLoadRounding.kt:168–172` rounds without
  applying the equipment ceiling. A 60 kg e1RM floor at two reps can prescribe
  52.5 kg against a 40 kg ceiling. Both engine and sanitizer call this branch.
- **R105-08 remains open (minor, mechanical):** SplitBuilder hides a numeric
  candidate's missing-inventory fallback disclosure behind `weight.isEmpty()`;
  Logger's no-load text still ignores the accepted-AI inventory-withholding
  signal that SplitBuilder recognizes. Locations: `SplitBuilderScreen.kt:990–1018`
  and `WorkoutLoggerScreen.kt:2114–2120`.
- Corrected full matrix `0105-corrected-full-matrix.log` passed fresh: 787 tasks
  executed, zero inherited, about 105 seconds. This does not close the defects.
  Domain 1,122, database 521, Logger 501 and SplitBuilder 79 test cases were green.
- Prepared UI acceptance, render assertions/viewed screenshots, DB inventory
  producer assertion and stale `WorkoutSchedule.kt` KDoc remain outstanding.
  That KDoc's path is outside the current allowed set; do not silently amend it.

The builder stopped under the two-unsuccessful-fixes rule for the remaining
blockers. No third correction attempt, final commit or publication occurred.
All legitimate implementation work remains preserved for explicit next action.

## Device acceptance — 2026-10-25 (S10e, debug build)

User-run acceptance on a physical Galaxy S10e, throwaway routine with a paired
superset (`Barbell Curl` + `Dumbbell Triceps Overhead Extension`):

| Check | Result |
| --- | --- |
| 1. Rest survives editing a logged set | **Pass** |
| 2. Finish ends the current session | **Pass** |
| 3. Draft Apply edits without logging | **Pass** |
| 4. Completed exercise collapses; removal re-expands | **Pass** |
| 5. One superset control set + Skip moves the turn without logging | **Pass** |
| 6. Per-implement load basis and withheld-load explanations | **Pass** (after logging two dumbbell sets; see below) |

Reported during check 5: neither paired member showed controls until every
earlier prescribed set was complete. Traced to
`GuidedWorkoutProgress.nextExercise`, which selects the first incomplete,
unskipped slot, and `WorkoutLoggerUiState.showsGuidedActions`, which grants a
paired member controls only while it owns the turn. This is **intentional
sequential guidance**, not a regression; it is a UX note (a later pair looks
inert until reached), not an acceptance failure.

Check 6 closed on 2026-10-25: after two logged dumbbell sets (about 12 kg × 10),
the per-hand/per-dumbbell labels and the withheld-reason copy rendered as
intended. **Device acceptance is 6 of 6.** The full matrix at
`0105-repair-full-matrix.log` covers the committed source; only documentation
changed afterwards, so no further Gradle run is required for these edits.
Publication remains unapproved; the branch is a local candidate.

