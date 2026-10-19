# Review snapshot — 0.10.2 (ordered per-set prescriptions, presets, prescribed effort)

Independent read-only full-range review performed under the batch contract's pre-requested checkpoint
while the parent remained in Build Mode. The reviewer made no edits; this document consolidates its
verdict, findings and closure evidence.

## Scope and revision

- Base (committed): `ee71aa40` — the 0.10.1 release, already reviewed and closed. 0.10.1 was out of
  scope for this review.
- Reviewed content: the working tree at tracked-diff SHA-256
  `3295c66c970b2275d28330d0761b2a139708493244872de20407dee056fb0f37` plus six untracked new files
  (`SlotSetTargets.kt`, `SetPrescriptionRecipe.kt`, `SlotSetTargetsTest.kt`,
  `SetPrescriptionRecipeTest.kt`, `58.sqm`, `SlotSetTargetsMigrationTest.kt`). The reviewer confirmed
  the hash and every path before reading source.
- Scope containment was clean: every changed path sat inside the contract's `allowed_paths`.

## Verification the reviewer inspected

- `d102-full-matrix-01.log` — fresh `--rerun-tasks` matrix, 787 executed / 0 up-to-date, exit 0, no
  failures, with the last source edit preceding the test run.
- Format pre-pass logs (`d102-format-03.log`, the coder's `ktlintFormat-correction.log`) and the
  per-module ktlint tasks re-executed inside the matrix.
- Test XML for the new classes and the format-gate tests.
- The reviewer explicitly recorded the limits: no device or Compose-UI acceptance, no on-device
  58→59 upgrade.

## Verdict

**[REJECTED]** — two acceptance items were unmet and the ordered write path had two confirmed defects
that silently discarded or mis-attributed the very targets the release exists to carry. The domain
contract, migration, format gates and resolver semantics were otherwise correct and well tested.

## Findings

| Id | Severity | Status | Classification | Summary |
| --- | --- | --- | --- | --- |
| F-01 | blocker / unmet acceptance | confirmed | design decision | Automatic per-set generation applied by the shared sanitizer did not exist; the sanitizer would have dropped ordered targets a model returned. |
| F-02 | major | confirmed | design decision | The documented `sets == setTargets.sets` invariant was unenforced, so the Logger could derive a wrong slot offset and log the wrong targets. |
| F-03 | major | confirmed | design decision | The draft-level override check compared against `targets.first()` while drafts were seeded from the slot's scalar fields, so an ordinary untouched draft was read as an override and the ordered targets were discarded. |
| F-04 | minor | confirmed | mechanical | A bodyweight-capability slot could carry ordered targets, which were then recorded as external with a null load. |
| F-05 | minor | confirmed | mechanical | The equivalent-RPE presentation was not labelled as the resistance-training convention, was recomputed inline instead of using `SetTarget.equivalentRpe`, and the per-set summary omitted effort. |
| F-06 | minor | confirmed | process (parent-owned) | Living documentation was not updated in the change. |
| F-07 | nit | theorized | mechanical | `matchesOrderedTarget` compared loads with raw `==`, and `-0.0` was accepted as a target load. |

The reviewer also recorded what it verified as correct: the migration is additive with existing rows
keeping `NULL`; the format gates reject pre-format carriers; the resolver's comparability and
all-slots-satisfied semantics are right; prescribed effort is genuinely separate from actual effort;
and the recipe snapshots round-trip.

## Corrections applied

- **F-01** — implemented rather than deferred. `ApplySetPrescriptionRecipeUseCase` builds a slot's
  ordered targets from the user's own `AutomaticSetPrescription` (shape, `minReps`/`maxReps`, optional
  RIR) using the planner's existing estimated-1RM conversion for each set's reps, external-load-only.
  `PlanRequest.automaticSetPrescription` carries the opt-in, `ObserveWorkoutPlanInputsUseCase`
  assembles it, and **both** the deterministic engine and `WeeklyPlanSanitizer` apply the same stage,
  so no engine can bypass it. SplitBuilder owns the opt-in UI. Absent means no ordered prescription, so
  every existing plan shape is unchanged.
- **F-02** — the invariant is now enforced by `RoutineTemplateValidator`, `PrescriptionValidator` and
  `BackupValidator`, the routine editor's Apply writes the slot's set count from the prescription, and
  the occurrence editor keeps ordered targets only when the set count still matches.
- **F-03** — `DraftSet.orderedPrefill` records the target the draft's scalars were seeded from, both
  draft construction paths seed from the first remaining ordered target, and override detection
  compares against that baseline. A target is followed only when the slot's own load kind matches.
- **F-04** — a draft follows a target only when the target's load kind equals the draft's, and the
  Per-set targets action is offered only for counted external slots.
- **F-05** — the Logger uses `SetTarget.equivalentRpe`, labels RPE as the same closeness-to-failure
  scale (`10 − RIR`), includes each set's prescribed RIR in the per-set summary, and shows the effort
  line when sets carry differing RIRs.
- **F-06** — this snapshot plus the mechanics references, `PLANS.md`, `docs/roadmap.md`, `docs/qa.md`
  and the automation record.
- **F-07** — loads are compared on the gram in `matchesOrderedTarget`, and a negative-zero target load
  is rejected.
- **F-03 regression tests** — three ordered write-path tests added to `WorkoutLoggerViewModelTest.kt`;
  the reviewer confirmed two would fail against the pre-correction `logDraft` and that the third is
  honestly labelled as a guard rather than a pre-correction failure.
- **C-01/C-03/C-04** — the automatic path no longer records a manual recipe at all. A recipe describes
  the manual builder's inputs, and the manual builder interpolates loads between endpoints while the
  automatic path converts each set's own reps, so any recorded recipe would promise a reopen it cannot
  reproduce. The editor already shows a recipe-less slot's stored targets with empty inputs.

Three further paths were needed for the F-01 wiring (SplitBuilder's five files, `PlanRequest.kt` and
`DeterministicWorkoutPlannerEngine.kt`, and `ObserveWorkoutPlanInputsUseCase.kt`) and were recorded as
amendments. The boundary gate caught the last two itself: `resume` named `PlanRequest.kt` and
`DeterministicWorkoutPlannerEngine.kt` as outside the allowed set before any commit.

## Acceptance-criteria coverage after corrections

- **Covered:** ordered targets stored and propagated through the four stores with the list and set
  count as one enforced value; recorded snapshot format 3 with a slot index; backup format 23 and
  interchange v4 with pre-format rejection; manual shapes with concrete numeric ladders and a recorded
  recipe; automatic generation from the user's range through the planner's conversion, applied by the
  deterministic engine and the sanitizer; ordered progression requiring every slot satisfied, with a
  surplus set unable to substitute; prescribed effort separate from actual effort with a derived RPE
  presentation; migration 58 additive with existing rows keeping `NULL`; fresh full matrix on the final
  candidate; independent review and closure; living documentation.
- **Code-inspected only:** the Compose surfaces (Routines dialog, SplitBuilder opt-in, Logger display)
  have no device or Compose-UI acceptance; `resolveOrdered` has no production consumer yet, so ordered
  progression is a domain contract rather than a live suggestion.
- **Disclosed limits:** `57.sqm` and `58.sqm` have no on-device upgrade exercise; `resolveOrdered`
  advances a prescription without its recipe, so an advanced slot would reopen with empty recipe
  inputs.

## Closure

Targeted closure ran over three rounds against the same reviewer session
(`ses_eb1106e53ffea3vbmFGNDh9eEf`), each read-only and bounded.

**Round 1 — NOT CLOSED.** F-01, F-02, F-04, F-05 and F-07 closed; F-03 was source-fixed but had no
regression test, `docs/qa.md` still contradicted the implemented state, and a new finding C-01
identified that the automatic PYRAMID recipe recorded flat endpoints.

**Round 2 — NOT CLOSED.** F-03 closed (three ordered write-path tests added; the reviewer confirmed
two would fail against the pre-correction `logDraft` and that the third is honestly labelled as a
guard rather than a pre-correction failure). F-06 closed. The flake classification for
`KoinModulesVerificationTest` and `DefaultWorkoutPlannerEngineProviderTest` was supported: no
`shared/` path is in the change set, nothing alters the Koin graph, and the module passes 273/273 in
isolation. Two further findings appeared: C-03 (the automatic STRAIGHT recipe recorded a rep endpoint
its targets did not use) and C-04 (the automatic PYRAMID recipe recorded the wrong load endpoint).

**Round 3 — CLOSED.** C-01, C-03 and C-04 were resolved at the cause rather than by patching
endpoints: the automatic path now records **no** manual recipe, because the manual builder
interpolates loads between endpoints while the automatic path converts each set's own reps, so no
manual recipe could reproduce an automatic prescription. The reviewer accepted this as the better
resolution and confirmed the manual path's exact round-trip assertion is intact, no consumer of
`recipe` broke, and the frozen revision has fresh full-matrix evidence (787 executed / 0 up-to-date,
exit 0, after the format pass).

**Remaining, nit-level and folded in without another round:** three documentation precision
corrections — naming only the routine validator for the load-kind clause, calling the recipe
"optional" where an automatic prescription has none, and citing the final matrix log rather than an
earlier one.

**Disclosed and accepted:** the Compose surfaces have no device or Compose-UI acceptance;
`resolveOrdered` has no production consumer; `resolveOrdered` advances a prescription without its
recipe; an automatic prescription's generation inputs are not persisted anywhere; and two intended
test cases (a warm-up ordered draft, and the bodyweight-versus-external kind guard) are unreachable
through the public actions, so the guards remain code-inspected only.

This closure grants no fix, commit, push or publication authority.
