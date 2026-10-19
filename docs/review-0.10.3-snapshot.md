# Review snapshot — 0.10.3 supersets

Base: `33fe53a00a42abb4623b6c3f3f4f5b247c3351b2` (clean tree).
Review revision: uncommitted working tree for the folded 0.10.3 scope.
Method: independent read-only full-range review, then targeted closure (batch checkpoint).

## Initial verdict — [REJECTED]

Blockers:
- **SS-01** `RoutinesViewModel.toEditor` omitted `superset`, so reopening a saved paired routine
  showed no pair and the next save cleared both members — silent loss of user-authored grouping.
- **SS-02** `WorkoutLoggerRoute` never passed `nextGuidedEntryId` / `supersetSkippedEntryId`, so the
  next-turn label and Resume action were unreachable and Skip changed unread state.

Other findings: **SS-03** (major) accepted-plan swap produced a half pair that later threw;
**SS-04** (minor) unequal counts used transition rest after the partner completed; **SS-05** (minor,
design decision) the unreachable occurrence-edit path rebuilds entries without pairs; **SS-06**
missing Logger tests; **SS-07** prepared device flow absent; **SS-08** docs overstated behavior;
**SS-09** validator test did not isolate the pre-24 gate; **SS-10/SS-11/SS-12** UX nits.

Verified then: domain validation, migration `59.sqm`, backup 24 / interchange v5 gates, planner
pairing consistency, and no fatigue/progression/eligibility change.

## Corrections

- SS-01: `toEditor` carries `superset`; save→reopen→save regression added.
- SS-02: route passes both values; `WorkoutLoggerSupersetTest` added.
- SS-03: `SubstituteExerciseUseCase` preserves the frozen pair; paired-swap regression added.
- SS-04: transition requires the partner to be incomplete; pure rest-selection tests added.
- SS-06: Logger superset tests added.
- SS-08: architecture section renumbered to 1.23; close-grip rationale corrected as a compound
  `HORIZONTAL_PUSH`; roadmap base wording fixed.
- A-01: skip assertion made discriminating. A-04: acceptance citations moved to
  `full-matrix-05.log`.

Evidence: `fix-check4.log` (126 executed, 0 inherited) and `full-matrix-05.log` (787 executed,
0 inherited: host suite, migration verification, ktlint, debug assembly, both iOS compiles).

## Closure — CLOSED

SS-01/02/03/04/06/08 and A-01/A-02/A-03/A-04 are closed. No new blocker.

## Still open (not waived)

- **SS-05** design decision: occurrence-edit pair semantics; path has no production caller.
- **SS-07** no prepared `.maestro` flow and no device acceptance; Routines pairing, Logger guidance
  and the SplitBuilder toggle are device-unverified.
- **SS-09** backup validator test does not isolate the pre-24 gate with a valid pair; no
  plan/activation/occurrence restore round-trip or PDF pair line.
- **SS-10** silent refused move; **SS-11** no feedback when pairing generates nothing; **SS-12**
  superset rest shown as the editable per-slot rest without an override marker.
- Pre-existing (not caused by 0.10.3): `AcceptWeeklyPlanUseCase` and `SubstituteExerciseUseCase`
  still omit `progressionPolicy`/`setTargets`; deferred, not fixed here.
