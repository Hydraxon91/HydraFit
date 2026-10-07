# Live testing — 2026-10-07: load semantics, advanced set methods and logging timing

## Scope and approval

The user reported live-training observations on 2026-10-07 (on an older build) and
approved recording the findings and the recommended direction. This record is
based on **read-only code inspection of current `main`**; no emulator reproduction
or database pull was performed. It does not authorize implementation chunks, new
dependencies, schema changes, commits or pushes. `PLANS.md` owns current
scheduling. The user approved promoting the load-semantics item to a near-term
priority and extending OF-02/OF-10B/OF-13/item 9; implementation remains separately
gated.

Source baseline: `e5c0f4c`. **Confirmed** means traced in current source. The
reported `32.5 kg` Ab Roll prescription was produced by an older build and is
**not reproduced** here; its exact origin is unconfirmed (see §1).

## Observations and homes

| Observation | Finding / importance | Home / next gate |
| --- | --- | --- |
| Ab Roll prescribed `32.5 kg` | Bodyweight/no-added-load exercises can still receive a numeric suggestion | **EX-02** — near-term; catalog/editor/planner/sanitizer/Logger |
| Supersets (e.g. biceps ↔ triceps) | Time-efficient; chronic outcomes broadly similar, pairing type matters | **OF-10B** — after guided logging is stable |
| Pyramid sets | Popular, not proven superior; needs per-set prescriptions | **PYR-01** — after per-set prescriptions + OF-10A |
| Warn on consistently short rests | Needs trustworthy live timing; small benefit >60 s, plateau >90 s | **OF-02** — after the basic timer |
| Log before/after; delayed batch entry | Settles what "rest" means measurement-wise | **OF-02-P0** — prerequisite for rest analysis |
| "Is RIR reps in reserve?" | Yes; explanation-only, no data change | **item 9 B1** (M4) |
| Border between sessions in Recent sets | Small Logger improvement; `sessionId` not carried into rows | **OF-13** linked; can be brought forward separately |

---

## 1. Bodyweight / no-added-load exercises (Ab Roll)

### Current behavior (confirmed in source)

- The seed correctly tags Ab Roll as bodyweight + ab roller:
  `core/database/.../DefaultExercises.kt:508-516`.
- `Exercise.isAvailableWith` ignores `BODYWEIGHT`, so it imposes no equipment
  requirement (`core/domain/.../equipment/Exercise.kt:18-20`).
- **Manual logging** hides the weight field for a bodyweight exercise until the
  user reveals it, so a hidden field records no weight
  (`feature/logger/.../WorkoutLoggerUiState.kt:93-99`,
  `feature/logger/.../WorkoutLoggerViewModel.kt:292-304`).
- **The deterministic planner does not enforce that distinction.** Any exercise
  with a positive baseline receives a numeric suggestion derived from the logged
  e1RM, regardless of load type
  (`core/domain/.../engine/DeterministicWorkoutPlannerEngine.kt:220-228`).
- The baseline is `max(logged-set Epley 1RM, manual PR)`
  (`core/domain/.../engine/SuggestWeightsUseCase.kt:12-19`,
  `core/domain/.../engine/ObserveWorkoutPlanInputsUseCase.kt:36-42,64-72`).
  Neither path establishes whether those kilograms are a meaningful external load
  for this exercise.
- The AI path's sanitizer only bounds the number and clamps the equipment ceiling;
  it applies no exercise load-type check
  (`core/domain/.../engine/WeeklyPlanSanitizer.kt:37-56`).
- **Draft confirmation records `draft.weightKg` directly**, unlike manual logging's
  hidden-field guard (`feature/logger/.../WorkoutLoggerViewModel.kt:488-508`).

**Conclusion:** the reported value is not necessarily a pure legacy artifact. An
old accepted plan can retain its frozen load, **and current generation can still
produce the same class of suggestion** if a positive baseline exists for a
bodyweight exercise. The exact `32.5 kg` value is not reproduced; confirming it
would require the old build plus that exercise's history in a read-only copy.

### Recommended design — exercise load semantics

Put the distinction on the **exercise**, not on equipment ("this equipment has no
weight"). The same equipment supports differently loaded movements, and a
bodyweight movement can have an added-load variant.

- **External load** — kilograms are the external resistance.
- **Bodyweight / no added load** — display "Bodyweight"; no numeric load.
- **Bodyweight with added load** — kilograms mean **added** weight.

A "Add external weight" checkbox is an acceptable simple UI, but it must map to
defined semantics.

**Do not collapse load to `0 kg`.** Existing semantics already distinguish
`null` (unspecified), `0.0` (explicit zero external load) and positive kilograms
(`core/domain/.../routine/RoutineTemplate.kt:40-41`). Bodyweight is not the same
as "zero resistance".

The chunk must cover: catalog/editor metadata; deterministic suggestions; AI
validation; substitution; Logger manual and draft paths; routine/activation
snapshots; PR/e1RM eligibility; and backup representation. In particular,
**added weight alone must not automatically become total effective resistance**
in the ordinary Epley calculation.

Preserve performed history. Decide explicitly how to display or correct old
erroneous prescriptions; never silently rewrite frozen activations or erase
recorded weights.

**ID:** propose **EX-02 — exercise load semantics** (sibling to EX-01). The user
approved bringing this forward ahead of the VOL-01 implementation and the final
OF-01 format contract, because it governs trustworthy prescriptions and the fields
backups must preserve.

---

## 2. Supersets

**Support them, but as an explicit workflow, and only after guided logging is
stable.**

A 2025 systematic review/meta-analysis of 19 studies found supersets can shorten
sessions and increase training efficiency with broadly similar chronic maximal
strength, strength-endurance and hypertrophy outcomes in the included studies, but
with higher perceived exertion and internal load. **Pairing type matters:**
agonist–antagonist supersets preserved repetition counts, while
similar-biomechanical pairings reduced volume load. See
[Zhang et al. 2025](#ref-superset).

**Recommendation: retain and expand existing OF-10B; do not add a duplicate item.**

Direction:

1. Let users explicitly group two routine entries.
2. Preserve each exercise's own prescription and each performed set's identity.
3. Guide `A1 → B1 → rest → A2 → B2`, with configurable transition/pair rest.
4. Handle unequal set counts, unavailable equipment, skipping one exercise, resume
   and unpairing.
5. Add optional planner pairing only after the workflow works.

For automatic pairing, start with curated compatibility plus an opt-in
time-efficiency preference. Muscle involvement weights alone are not a sufficient
pairing policy.

**Skip:** supersets as a default, any claim of superior growth, or arbitrary
superset fatigue multipliers. Reordered performance can naturally change
timestamp-based replay; it must still use the existing model unless a separate
change is approved.

---

## 3. Pyramid sets

**"Pyramid" must be defined:** increasing load with decreasing reps, reverse
pyramids, or up-and-down sequences. Warm-up ramp sets are a separate concept.

A 2023 review of 15 studies found the pyramid protocol was **not superior** to
traditional training for acute responses, strength gains or hypertrophy within the
studied repetition zones (8–12) and intensities (67–85% 1RM); the choice can be
framed as periodization, motivation or preference. See
[Cardozo & Destro 2023](#ref-pyramid) and, secondarily,
[Liu et al. 2024](#ref-rt-modalities).

### Implementation direction

Current prescriptions use one `sets × reps @ load` value per exercise entry
(`core/domain/.../engine/PlannedExercise.kt`, `AcceptedPlan.kt`,
`RoutineEntry`). A genuine pyramid needs an **ordered list of per-set targets**,
e.g. set 1 = 12 reps at load A, set 2 = 10 at load B, set 3 = 8 at load C. That
representation must survive routine editing, activation snapshots, logging
attribution, completion checks, progression and export.

**ID:** propose **PYR-01 — per-set prescriptions and pyramid sets** (sibling to
OF-10A–D). Support manual per-set targets first; defer automatic pyramid generation
until the OF-10A prescription/progression integrity contract is settled.

**Skip:** encoding a pyramid as duplicate exercise entries to work around the
uniform prescription model, or treating warm-up ramps as working sets.

---

## 4. Rest warnings and logging timing

### The measurement problem (confirmed)

- Manual logging stamps "now" unless a backdated time is supplied, and the backdated
  path is explicit (`feature/logger/.../WorkoutLoggerViewModel.kt:313,337-346`).
- Draft/batch logging can record several sets close together
  (`WorkoutLoggerViewModel.kt:488-508`).
- Therefore **timestamp gaps can describe data entry, not performance**.
- Even accurately recorded **set-completion gaps include the next set's duration**
  and are not themselves exact rest intervals.

### Recommended OF-02 workflow

**Prepare before; confirm actual performance after.**

- Before the set: show/edit the target, optionally "Start set".
- After the set: confirm actual reps/load/RIR.
- Planned/start actions never create performed sets.
- Offer "Log earlier sets" for catch-up entry; catch-up entries remain valid
  history but start no live countdown and produce no measured-rest coaching.
- If a user forgets to log, retain **unknown timing**; do not invent regular
  spacing.

The basic timer can start from a live completion action, with its limitation made
clear. Precise rest measurement needs a preceding completion and the next set's
start event, or another explicitly approved measurement mechanism.

### Coaching

A 2024 Bayesian meta-analysis suggests a small hypertrophy benefit for rests
longer than 60 s, with substantial uncertainty, and did not detect appreciable
additional differences beyond 90 s. See [Singer et al. 2024](#ref-rest). This does
**not** establish a universal "too little rest" threshold per exercise or goal.

Add optional, non-blocking coaching, e.g. "You've started several comparable sets
before your chosen rest target; consider more recovery if performance is
dropping." Only evaluate eligible live intervals — exclude catch-up logging,
edited/unknown timing, warm-ups and intentional within-superset transitions. The
minimum sample, window and target policy are decided at the OF-02 design gate.

**Skip:** judging users from batch-entry gaps, diagnosing poor recovery,
penalizing long rests, or changing fatigue/progression from the timer.

---

## 5. RIR

**Confirmed terminology:** reps in reserve = the additional reps the user
estimates they could have completed **at the end of that set**, with comparable
technique and range of motion (0 = none left, 1 ≈ one more, 2 ≈ two more). It is a
subjective estimate, not derivable from kilograms and reps.

This already maps to **item 9 B1** (explanation + quick-picks). Keep a blank RIR as
unreported; never store a presumed value as a measured report. No new plan item.

---

## 6. Session dividers in Recent sets

**Confirmed:** `WorkoutSet` carries `sessionId`
(`core/domain/.../workout/WorkoutSet.kt:17`), but `LoggedSetRow` drops it
(`feature/logger/.../WorkoutLoggerUiState.kt:16-27`); `refreshRecentSets` sorts by
performed time then id and renders a flat list
(`WorkoutLoggerViewModel.kt:637-657`, `WorkoutLoggerScreen.kt:431-459`).

The OF-13 session-history plan does not automatically add boundaries to the
current list. A small separate Logger slice could:

- Carry `sessionId` into row state and add a divider/header when adjacent rows
  change session.
- Preserve the performed-time-descending, insertion-id tie order.
- Label legacy/null-session rows honestly; **do not invent one session** for all
  null rows.
- Verify multiple same-day sessions, corrected times, deletion and legacy rows.

This can be brought forward independently of the full OF-13 browser. It is
separate from the deferred LT-03 tied-time emulator check.

---

## Research references

Facts only; no text or figures are reproduced.

- <a name="ref-superset"></a>Zhang X, Weakley J, Li H, Li Z, García-Ramos A.
  *Superset Versus Traditional Resistance Training Prescriptions: A Systematic
  Review and Meta-analysis Exploring Acute and Chronic Effects on Mechanical,
  Metabolic, and Perceptual Variables.* Sports Med. 2025;55(4):953–975.
  doi:10.1007/s40279-025-02176-8 (PMID 39903375).
- <a name="ref-rest"></a>Singer A, Wolf M, Generoso L, et al. *Give it a rest: a
  systematic review with Bayesian meta-analysis on the effect of inter-set rest
  interval duration on muscle hypertrophy.* Front Sports Act Living.
  2024;6:1429789. doi:10.3389/fspor.2024.1429789.
- <a name="ref-pyramid"></a>Cardozo DC, Destro DS. *Pyramidal resistance training:
  A brief review of acute responses and long-term adaptations.* J Bodyw Mov Ther.
  2023;35:21–27. doi:10.1016/j.jbmt.2023.04.070 (PMID 37330772).
- <a name="ref-rt-modalities"></a>Liu P, Yuan H, Lu Y, Gao Z. *Resistance training
  modalities: comparative analysis of effects on physical fitness, isokinetic
  muscle functions, and core muscle biomechanics.* Front Physiol.
  2024;15:1424216. doi:10.3389/fphys.2024.1424216 (PMID 39072216).

These are sources of facts and model rationale, not proof of a universal optimum.
No measurement is invented and no fatigue/progression change is authorized by
citing them.

## Delivery order and unresolved decisions

**Recommended order:**

1. **EX-02 — load semantics** (near-term, before VOL-01 implementation and the
   final OF-01 format).
2. **OF-02** logging/timing contract, then the rest timer, then optional coaching.
3. **OF-10B — supersets** once guided logging is stable.
4. **PYR-01 — per-set prescriptions / pyramids** after OF-10A's integrity contract.
5. Session dividers as a small optional slice linked to OF-13.
6. RIR stays in item 9 B1; no new work.

**Unresolved decisions (do not default silently):**

- EX-02: metadata shape; added-load vs total-resistance in e1RM/PR; how to surface
  or correct existing erroneous prescriptions without rewriting history.
- OF-02: completion/start event model; whether the timer can measure rest at all,
  or only prompt; coaching thresholds, sample and window.
- OF-10B: pairing storage, rest timing, and whether grouping changes any fatigue
  calculation.
- PYR-01: per-set prescription representation and its effect on progression,
  completion and export.
- Session dividers: label and grouping policy for null/legacy sessions.
