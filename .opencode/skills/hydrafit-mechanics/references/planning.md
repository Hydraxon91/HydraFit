# Planning mechanics

User-authored routines, frozen activation/occurrence slots and accepted-plan slots support
exclusive reps or whole seconds. The engines themselves still prescribe reps only and never invent a
duration: an accepted-plan slot's seconds are always the user's own measure edit, made in
SplitBuilder's per-slot measure action (Reps/Seconds plus a value; invalid input writes nothing). A
draft edit is state-only and carries into acceptance; an accepted-plan edit persists in place like a
swap and keeps the plan's id and acceptance time. Swap preserves the replaced slot's measure,
plan-to-routine conversion carries it, and Regenerate produces a fresh rep-based plan, so a user-set
duration is not preserved across regeneration. A timed slot is skipped by rep-based load progression
rather than counted as zero reps, and performed timed sets do not enter rep-derived fatigue/load
inputs. Informational soreness/exhaustion storage is deferred; do not infer that the existing
fatigue-based skip/reduce behavior consumes user reports.

## Curated planner superset pairing (0.10.3)

`PlanRequest.plannerSupersetPreference` is null by default; a non-null
`PlannerSupersetPreference(transitionSeconds, pairRestSeconds)` opts in. `GenerateWeeklySplitUseCase`
applies `PlannerSupersetPairing` after the selected engine returns, so deterministic, Gemini and
local-LLM output follow one rule. Only already-selected **adjacent** curated biceps/triceps isolation
slots are grouped; nothing is reordered, added or re-prescribed, and an already-paired slot is left
alone. Custom exercises and `close-grip-bench-press` are excluded (the catalog classifies it as a
compound `HORIZONTAL_PUSH`, not an isolation pattern). Grouping never changes fatigue or
progression inputs. Accepted plans copy the frozen group through `AcceptWeeklyPlanUseCase`.

## Confirmed planning dates (decay-only projection)

SplitBuilder's separate date editor lets the user confirm the exact next N workout civil dates
from chosen weekdays plus a start date, shift them all by one week, or clear them to unknown.
Confirmed dates travel as `PlanRequest.workoutEpochDays` with `fatigueByWorkout`, one recorded-
history fatigue map per date, produced by `ObserveWorkoutPlanInputsUseCase` with the same local
clock time as `nowMillis` and the current fixed UTC offset; recorded sets after `nowMillis` are
excluded. Dates must be exactly `daysPerWeek`, strictly increasing, non-past and within the
supported epoch-day range, or the observer falls back to undated planning.

This is **decay only**: a later date sees the recorded history decayed further, and no
hypothetical fatigue from earlier planned workouts is added. The deterministic engine reads
`PlanRequest.fatigueForWorkout(dayIndex)` per day; AI prompts and every other consumer still use
`muscleFatigue`, so projection must not be described as complete schedule simulation. Undated
requests keep the previous behavior exactly.

Date, frequency or clock validity changes cancel in-flight generation and preserve the displayed
draft or accepted plan, setting `canRegenerate`; nothing regenerates or replaces a frozen plan
automatically — an ordinary source refresh (a logged set moves fatigue and load inputs) keeps an
accepted plan displayed and accepted, while an explicit frequency or sets change still regenerates
and resets the displayed plan; Regenerate applies refreshed inputs on demand. Regenerate then Accept
stays explicit, and Accept/schedule is refused while the shown draft's dates are stale,
re-checked when Confirm is pressed so a schedule dialog opened before the context changed cannot
activate it; an undated draft becomes stale once dates are confirmed. Generation dates are separate
from a persistent training block's schedule: confirming dates never moves an existing block, the
block dialog is seeded from the same configuration, and a preview that disagrees with the draft's
dates requires Regenerate first. Showing a historical accepted plan resets the shown draft's date
provenance to unknown rather than implying persisted dates. Per-date fatigue does not enter
substitution, which keeps ranking against current fatigue.

Paths are relative to the repository root; package shorthand is defined in
`../SKILL.md`. Read current source and relevant tests before using this reference.

## Planner inputs and weight semantics

`PlannerCandidateEligibility` excludes `TRAP_ISOLATION`/`NECK_ISOLATION` and the six
curated shrug/neck IDs even after pattern overrides. This manual-only policy applies to
deterministic selection, both AI prompt catalogs, sanitization and planner-backed replacements.
Manual logging and routine authoring retain the full catalog. Existing traps deficit deferral
is unchanged; don't infer automatic neck/traps prescriptions from the added seeds.

Tibialis anterior, serratus anterior and iliopsoas-focused hip flexors participate
in the existing weekly-volume/deficit and fatigue ranking, with the same heuristic
volume windows as other targetable groups. Only the existing `TRAPS` exclusion remains.
Tibialis currently has no seeded training source; a custom mapping can supply candidates.

Equipment availability and ceilings come from one active-profile snapshot. The profile/revision
context travels through `WorkoutPlanSources` and `WorkoutPlanInputs`, not historical prescriptions.
SplitBuilder preserves the displayed plan when that context changes and enables explicit
regeneration; in-flight old-context generation is cancelled and late results check cancellation.
Profile switching never rewrites an accepted plan or an active occurrence.

The input path is:

```text
SqlDelightWorkoutPlanSourcesRepository.observe()
  -> ObserveWorkoutPlanInputsUseCase
  -> WorkoutPlanInputs(request, requestedEngine)
  -> GenerateWeeklySplitUseCase
  -> WorkoutPlannerEngineProvider.get().generatePlan(request)
```

`engine/PlanRequest.kt` carries:

```text
daysPerWeek, availableEquipment, muscleFatigue, splitPreference, nowMillis,
goal, setsPerExercise, accessorySetsPerExercise,
recentExerciseIdsByPattern, suggestedWeightsKg, recentWeightCaps,
withheldWeightExerciseIds, equipmentMaxWeights, exercisePreferences,
includeWorkoutData, recentWeights, weekNumber, cycleNumber, isDeload
```

Important distinction: `PlanRequest.suggestedWeightsKg` contains estimated 1RM
baselines adjusted by progression, despite the name. The output field
`PlannedExercise.suggestedWeightKg` is a working-set load. AI prompt wording
must not accidentally present a 1RM baseline as a ready-to-lift working weight.

Manual PRs enter the baseline through `WorkoutPlanSources.personalRecords`.
They are not workout sets and do not add fatigue. An eligible external manual PR is a
**floor**, not an all-history exemption: the planner baseline is the recent-window
estimate (or the manual estimate when evidence is thin) raised to at least the eligible
manual value. See the "Recent-performance load policy" recipe below for the full rule.

AI history and weight suggestions are gated by `includeWorkoutData`.
Deterministic suggestions are local computations and do not require the AI
sharing toggle.

### Exercise load semantics (EX-02)

An exercise's **capability** (`Exercise.loadCapability`) is separate from a
recorded number's **kind** (`LoadKind`). Capability is `EXTERNAL`,
`BODYWEIGHT_ONLY`, `BODYWEIGHT_ADDABLE` or `UNSPECIFIED`; it is curated per
built-in (`ExerciseLoadDefaults`, applied in both `DefaultExercises.ex` helpers),
editable per built-in (`exerciseOverride.loadCapability`) and required for new
customs. Never infer it from equipment: Ab Roll and the calf-raise-on-a-dumbbell
use apparatus without carrying its weight. Kind is `EXTERNAL`, `BODYWEIGHT`,
`ADDED` or `LEGACY_UNSPECIFIED`; `null`/`0.0`/positive stay distinct.

`WorkoutLoadPolicy` is the single pure rule. Automatic numeric generation
(`DeterministicWorkoutPlannerEngine`, `WeeklyPlanSanitizer`, `SubstituteExerciseUseCase`)
is **external-only** and skips `UNSPECIFIED` too. `BuildPlannerLoadInputsUseCase`
filters the baseline/PRs by `contributesToLoadMath` and feeds progression only
typed `EXTERNAL` sets against typed `EXTERNAL` prescriptions, so a pre-EX-02 plan
seeds the baseline (when the exercise is external today) but not a streak. Legacy
rows are never rewritten. `WorkoutLoadPolicy.reconcile` snaps routine/occurrence
edits onto the current capability. `LogWorkoutSetUseCase` enforces the shape at
persistence; the Logger resolves manual/plan/occurrence authority and requires an
explicit decision for `LEGACY_UNSPECIFIED` drafts before logging. Snapshot
capability is frozen on accepted plans and activation/occurrence entries.

Fatigue stays unchanged: `SqlDelightWorkoutLogRepository.mapLoggedSets` passes a
neutral relative load for `BODYWEIGHT`/`ADDED` (`weightKg = null`) but keeps
external/legacy weights, so historical replay figures do not move. Migration
`27.sqm` adds the columns and marks all existing load rows legacy/unspecified;
the seed backfills built-in capability.

### Exact weight and rep defaults

0.8.0 descriptive progress uses `workout/ExerciseProgressCalculator.kt`, not planner-baseline
calculations. It reuses unadjusted Epley with the existing 1–15-rep eligibility for positive typed
external loads, without recency/sample thresholds, progression, manual floors or working-load
rounding. Bodyweight/added comparisons are separate; legacy rows have no load-derived records.
Warm-ups/future rows do not aggregate. First observations establish baselines, ties retain holder
ids and strict improvements use earlier timestamps only. Requested occurrence events replay against
all earlier exercise history, not today's maximum or a chart window. The post-finish summary (C5)
reads those events through `workout/ObserveOccurrenceRecordsUseCase.kt`, which scopes membership to
the occurrence's own recorded exercises (`ExerciseProgressRepository.observeOccurrenceExerciseIds`),
restarts on a membership change, retracts a removed exercise and passes an unused single-day chart
range so events stay independent of any displayed window. Missing quality self-report
never means verified technique; partial workouts can have descriptive records without prescription
success. Manual PR writes and all existing planner policy remain unchanged; broader LT-10 decisions
remain open under the explicitly narrowed C1–C3 prerequisite in PLANS.md.

Sources: domain `engine/OneRepMax.kt`, `SuggestedWeightConfig.kt`,
`VolumeAwareReps.kt`, `TrainingGoal.kt`, and `ProgressionConfig.kt`.

```text
Epley estimated 1RM = weightKg * (1 + reps / 30.0)
maxRepsForEstimate = 15
rirBuffer = 0.10
roundToKg = 2.5
intensityForReps(reps) = curveFor(reps) * (1 - rirBuffer)
roundToIncrement(weightKg) = round(weightKg / roundToKg) * roundToKg
```

Verbatim NSCA curve, reps to fraction of 1RM:

```text
1:1.00  2:0.95  3:0.93  4:0.90  5:0.87
6:0.85  7:0.83  8:0.80  9:0.77  10:0.75
11:0.73 12:0.70 13:0.68 14:0.67 15:0.65
16:0.64 17:0.63 18:0.62 19:0.61 20:0.60
```

`curveFor` clamps reps to the table's bounds and uses `0.7` if a custom curve
lacks that key. It does not interpolate between entries.

The deterministic engine and model sanitizer share the same e1RM-to-working-load
conversion (`SuggestedWeightConfig.workingWeightFor`): prescribed reps, the existing
intensity/buffer, deload scale, nearest-increment rounding, then equipment ceiling.
The model sanitizer retains a below-bound proposal and clips only proposals above
the shared bound.

```text
working = roundToIncrement(progressed1RM * intensityForReps(reps) * deloadScale)
suggestedWeightKg = EquipmentWeightLimit.clamp(working, ceilingFor(exercise))
```

`BuildPlannerLoadInputsUseCase` uses the best eligible external or compatible-legacy
Epley estimate in the inclusive `[now − 42 days, now]` interval, with at least two
sets. Horizon and sample size are product/engineering defaults, not validated
capacity thresholds; same-session sets count but do not prove maximum capacity.
Future-dated sets are excluded from planner baseline, recency and progression inputs.
No execution-quality, actual RIR, intentionally-light-work or deload-intent inference
is made; qualifying light work can lower the suggestion. Insufficient or expired
evidence withholds an automatic numeric load instead of falling back to all-time
history. An eligible external manual PR is a floor, not an exemption. Progression
follows tempering, and its resulting e1RM bound is carried in
`PlanRequest.recentWeightCaps` for both Deterministic and `WeeklyPlanSanitizer`.
Yang et al. 2022 (https://doi.org/10.1123/japa.2020-0493) concerns lower-limb
strength retention in middle-aged/older adults; it does not establish this lookback
window, sample threshold or prescription ceiling. See PLANS.md C2D for the approved
contract and its scope limits.

Reps under shipped Option C: chosen set count is the volume knob; reps stay fixed
at the goal's compound/isolation values. The older volume-constant formula is
superseded. `VolumeAwareReps.repsFor` validates a positive set count but does not
trade reps against it.

```text
reps = if (isCompound) goal.compoundReps else goal.isolationReps
DEFAULT_SETS_PER_EXERCISE = 3  // declared in PlanRequest.kt
```

| Goal | Compound sets | Accessory sets | Compound reps | Isolation reps |
| --- | --- | --- | --- | --- |
| BALANCED | 3 | 2 | 6 | 12 |
| STRENGTH | 4 | 3 | 5 | 8 |
| HYPERTROPHY | 3 | 2 | 8 | 12 |
| ENDURANCE | 2 | 1 | 15 | 15 |

Progression defaults: `successStreak = 3`, `failureStreak = 3`,
`roundToKg = 2.5`, `maxIncrements = 5`. Completion is judged against each set's own
**recorded target**, never merely the heaviest logged set and never today's accepted
plan.

`WorkoutSet.prescriptionSnapshot` carries the target captured at submission
(`WorkoutPrescriptionSnapshotJson`, version 1: exercise, prescribed sets, reps or
duration, load kind, nullable target load, deload flag). The Logger captures the
tapped occurrence entry, else today's accepted-plan slot, and writes it in the same
statement as the set; a backdated set with no attributable slot records no target.
Corrections rewrite performance only — a target is never rewritten.

`ProgressionHistory` (`engine/ProgressionHistory.kt`) groups one exercise's
non-warmup recorded work by **explicit session**, orders sessions by earliest
performed time, and classifies each exposure against its own target:

- `SUCCESS` — the session is closed at or before `nowMillis`, every set in it names
  the same comparable target, the target is a non-deload typed external rep target
  with a positive load, and at least `sets` recorded sets reached both the target
  reps and load.
- `MISS` — the same, but fewer sets reached the target.
- `UNKNOWN` — an open session, a session not fully recorded, a blank or
  explicit-zero recorded load (which cannot establish weighted target success or
  failure), mixed or absent targets, a legacy/bodyweight/timed target, or a deload
  target.

An `UNKNOWN` exposure **breaks the streak without counting as a miss**: missing
evidence never becomes demonstrated failure, and no reduction is applied. A change
of comparable target also restarts the streak, so work performed against a different
prescription is never stitched into one block. Two sessions on the same local day
stay separate; they are never combined into one success.

Legacy rows have no recorded target and stay `UNKNOWN`. `SqlDelightWorkoutPlanSourcesRepository`
supplies one coherent `ProgressionHistory` (sets, sessions, and occurrence-derived
targets for rows written before format 21) and invalidates on set, session,
occurrence and activation writes. Occurrence-derived targets are used only when the
attribution is unambiguous (matching entry, exercise and occurrence), so a
replacement or an unattributed row is never guessed. The 2026-10-15 narrowing that
let 0.8.0 charts ship independently is now superseded: the integrity contract is
implemented, and remaining 0.10.x progression **policy** stays in PLANS.md.

Sources: domain `engine/OneRepMax.kt`, `SuggestedWeightConfig.kt`,
`VolumeAwareReps.kt`, `TrainingGoal.kt`, `ProgressionConfig.kt`,
`ProgressionHistory.kt`; `workout/WorkoutPrescriptionSnapshot.kt`.

### Approved slot progression policy (0.10.1)

A prescription slot may carry a user-authored `SlotProgressionPolicy`
(`engine/SlotProgressionPolicy.kt`, strict versioned JSON): `minReps`, `maxReps`, an
`incrementKg` load step and an optional `equipmentId`. Both numbers are user inputs — the
app never invents a rep range or an increment, and no range here is claimed optimal. The
policy is stored on `routineEntry`, `planHistoryEntry`, `activationEntry` and
`occurrenceEntry` (additive migration `57.sqm`), travels into the recorded target as
snapshot format 2, and is carried by backup format 22 and prescription interchange v3.

`ResolveSlotProgressionUseCase` (`engine/SlotProgressionResolver.kt`) evaluates one slot
against its own recorded history and returns `ADVANCE_REPS`, `ADVANCE_LOAD`, `HOLD` or
`REVIEW` with a recorded reason. Comparability is the slot's current prescription: an
exposure counts only when its recorded target names the same exercise, set count, rep
count and load, is a typed external rep target, is not a deload, and carries the same
approved policy. Any other exposure — a changed prescription or policy, a legacy row with
no target, a bodyweight or timed target, an unjudgeable session — breaks the run without
counting as a miss.

- `ADVANCE_REPS` after `successStreak` (3) comparable successes while `reps < maxReps`.
- `ADVANCE_LOAD` after the same run at `maxReps`: the load takes `incrementKg` and reps
  return to `minReps`.
- `REVIEW` after `failureStreak` (3) comparable misses. A miss run never removes load
  automatically.
- `HOLD` for anything else, with `NOT_ELIGIBLE` (timed, non-external, blank or zero
  load), `PAUSED_DELOAD`, `INSUFFICIENT_COMPARABLE_WORK`, `EQUIPMENT_UNRESOLVED`,
  `NO_REACHABLE_INCREMENT` or `LOAD_CEILING_REACHED`.

A named `equipmentId` resolves the step onto that bar's or dumbbell's real grid using the
existing `calculatePlateLoading`/`calculateDumbbellLoading` helpers, and only when the
active inventory's unit is kilograms; the exact weight is used when loadable, otherwise
the nearest loadable weight above it. An identity absent from the inventory holds rather
than borrowing another grid. Without an identity the numeric step applies directly. A
known `equipmentCeilingKg` blocks any increase the equipment cannot provide.

The resolver changes nothing by itself: no frozen occurrence, activation, accepted plan or
recorded history is rewritten, and applying a suggestion is a separate explicit edit. The
existing NSCA/e1RM load math, the manual-PR floor, the recent-window baseline, the deload
pause and automatic-load withholding are unchanged; the policy never feeds
`PlanRequest.suggestedWeightsKg`.

Limits for 0.10.1: the suggestion is computed and unit-tested but has no UI surface — the
Routines editor authors the policy only, and `equipmentCeilingKg` is supplied by callers
that already hold ceiling context, which the authoring UI does not.

### Ordered per-set prescriptions (0.10.2)

A slot may carry an ordered per-set target list instead of one repeated prescription.
`SlotSetTargets` (`engine/SlotSetTargets.kt`) holds an ordered `List<SetTarget>` plus an
optional `SetPrescriptionRecipeSnapshot` describing the manual recipe that produced it. `SetTarget` carries its own reps or
seconds, load kind, load and optional `targetRir`; `equivalentRpe` is the derived
presentation of the same target (`RPE = 10 − RIR`), never a second stored value. A slot
with no ordered list keeps its existing single repeated target, so a legacy or simply
repeated prescription is never reinterpreted as an ordered one.

**The list and the set count are one value.** `SlotSetTargets.sets` is always the list size,
and `RoutineTemplateValidator`, `PrescriptionValidator` and `BackupValidator` all reject a
slot whose `setTargets.sets` disagrees with its `sets`; `RoutineTemplateValidator`
additionally rejects targets whose load kind differs from the slot's, since the routine
editor is the authoring path for that pair. The Logger derives a set's slot index from the
count agreement, so a mismatch would address the wrong slot.

Storage is additive migration `58.sqm` (nullable `setTargets` on the four prescription
stores, schema 58→59). The recorded target is snapshot format 3, which adds `setIndex` and
`targetRir`; formats 1 and 2 stay readable and reject the new fields. Backup format 23 and
prescription interchange v4 carry the ordered list, and pre-format carriers are rejected.

**Manual recipes.** `BuildSetPrescriptionUseCase` turns a `SetPrescriptionRequest` (shape,
sets, start/end reps, start/end loads, rounding step, optional RIR) into ordered targets and
records the recipe, so reopening a slot shows what the user entered rather than an inferred
shape. Shapes are `STRAIGHT`, `ASCENDING_LOAD`, `DESCENDING_LOAD` and `PYRAMID`; the pyramid
places the peak load and trough reps at the middle set. They are configurable recipes, not
claims of superiority.

**Automatic generation.** `ApplySetPrescriptionRecipeUseCase` builds a slot's ordered targets
from an `AutomaticSetPrescription` (the user's shape, `minReps`/`maxReps` and optional RIR)
using the planner's existing estimated-1RM conversion for each set's reps — never an
arbitrary percentage ladder. It is external-load-only and produces nothing without a rep
count, an external slot or an estimated-1RM bound. `PlanRequest.automaticSetPrescription`
carries the opt-in (absent means no automatic ordered prescription, so every existing plan
shape is unchanged), `ObserveWorkoutPlanInputsUseCase` assembles it, and **both** the
deterministic engine and `WeeklyPlanSanitizer` apply the same stage, so no engine can bypass
it. SplitBuilder owns the opt-in UI.

An automatically generated prescription records **no** `SetPrescriptionRecipeSnapshot`: a
recipe describes the manual builder's own inputs, and the manual builder interpolates loads
between endpoints while this path converts each set's own reps, so a recorded recipe would
promise a reopen it cannot reproduce. The editor shows a recipe-less slot's stored targets
with empty inputs rather than inventing a shape.

**Ordered progression.** `ProgressionHistory.orderedExposures` judges a session slot by slot.
A session is comparable only when every recorded set names one of that prescription's own
slots exactly once and its recorded target matches that slot (loads compared on the gram);
success requires **every** prescribed slot to have reached its own reps and load, so a
surplus set can never substitute for a missed one. `ResolveSlotProgressionUseCase.resolveOrdered`
advances every set by one rep while the whole prescription stays inside the policy's range,
then takes the policy's load step with reps back at the floor; misses ask for review.

### Selection, schedule, and periodization

Deterministic candidate ordering: explicit user preference (`PlanRequest.exercisePreferences`,
`PREFER` < `NEUTRAL` < `PREFER_LESS`; absent ids are neutral), largest remaining
weighted volume deficit, weighted fatigue, freshness within the generated week,
previous-plan compound rotation, equipment rank, then exercise id. Preference is a soft
first tier among candidates that already passed the equipment, soreness and direct-arm
coverage gates; it is never inferred, never decays and `PREFER_LESS` never removes a
candidate (it is not EX-01 exclusion). Coverage is the next key so a negligible difference
in the unvalidated fatigue estimate cannot override a much larger coverage deficit; fatigue
is used within a class, not as a tolerance.

EX-01 exclusions are a separate, harder gate: `PlanRequest.excludedExerciseIds` (active
exclusions only, computed by `ObserveWorkoutPlanInputsUseCase` from `WorkoutPlanSources.exerciseExclusions`)
removes candidates before ranking in the Deterministic engine, `WeeklyPlanSanitizer`,
`SubstituteExerciseUseCase` and both model engines' available-id lists. Exclusions cannot be
bypassed; `Prefer-less` is not an exclusion. If a requested focus has no equipment/exclusion-eligible
work, the engines preflight `PlannerCandidateEligibility.requireWorkouts` and fail with a non-transient
`NO_ELIGIBLE_EXERCISES` before generating; an empty output that still had eligible work is reported
separately as `NO_USABLE_EXERCISES`.
The weighted ledger accumulates
selected sets × involvement weight; it is planned volume, not performed-history
volume or a validated direct/indirect hypertrophy conversion. Accessories fill
toward four exercises/day, then chase deficits up to six. Unfilled weighted
targets remain possible. The weekly `minSets`/`targetSets`/`maxSets` window is a
soft heuristic: the selector chases `targetSets`, treats `maxSets` as a pre-pick
gate with possible whole-slot overshoot, and ignores `minSets` (report-only).

The isolation pool is focus-scoped and determines reachability (a catalog
exercise whose pattern is in no pool is never auto-selected): push adds
triceps/shoulder isolation plus chest fly; pull adds biceps/forearm isolation; upper adds
biceps/triceps/forearm/shoulder isolation; legs/lower adds leg isolation, calf raises and
core; full-body is the union. Lunge competes in the leg/lower squat group rather
than being full-body-only. These are product reachability choices informed by
general resistance-training evidence (e.g. pec-deck/chest-fly regional
hypertrophy), not a claim that each isolation exercise is optimal.
FOREARM_ISOLATION (0.8.1) uses ordinary accessory set counts and weighted deficits,
never the dedicated direct-arm priority/target. Existing exclusions, soreness and
daily caps still apply; no TRAPS policy or fatigue constants change.

Compounds are chosen one per focus group by largest deficit; a compound not yet
used this week is preferred, but one repeats when no alternative exists so a later
day is not left incomplete.

The deterministic planner also targets four direct isolation sets each for
BICEPS and TRICEPS per normal generated week, across all goals. A set qualifies
only when the exercise uses the matching `BICEPS_ISOLATION` or
`TRICEPS_ISOLATION` pattern and has positive effective involvement for that
muscle; the involvement-weight tier is not a biological cutoff. While an arm
target is unmet, compatible arm isolation candidates take priority over
discretionary accessories, even when compound weighted credits already meet that
muscle's ordinary weighted target. When both arms are still below target, the
arm with fewer direct sets is fed first so one arm cannot take every slot.
Equipment, soreness skip/reduction rules,
selected set counts, and the six-exercise daily cap still apply; whole accessory
slots may overshoot the target, and constrained plans report unmet coverage.
Normal arm targets are not chased during deloads. `WeeklyPlan.armCoverage`
separates direct isolation sets from estimated other involvement credits and
reports a bounded unmet reason; it is not performed volume or a growth guarantee.
On acceptance, `AcceptWeeklyPlanUseCase` freezes this assessment with a
`PlanAttribution` (`DETERMINISTIC` vs an `AI_GENERATED` assessment, never a
claimed model rationale) into the `planVolumeExplanation` table, read back into
`AcceptedPlan.armCoverage`/`volumeAttribution` (legacy plans have none). A
`planVolumeExplanationState` row (migration `31.sqm`) records `AVAILABLE` or
`INVALIDATED_BY_SUBSTITUTION`, so a substituted plan is distinguished from a
legacy one after reload; both acceptance paths (`accept` and `acceptAndActivate`)
share one writer. A confirmed substitution clears the assessment rows and marks
the state row in the same transaction; the UI then says the explanation is
unavailable rather than reconstructing it from today's catalog. Only a recorded
deterministic skip reports `ALL_COMPATIBLE_CANDIDATES_SKIPPED_FOR_FATIGUE`; an
`AI_GENERATED` assessment reports `COMPATIBLE_CANDIDATES_ABOVE_SORENESS_THRESHOLD`
instead, because the sanitizer does not run the planner's selection branch.

Gemini and local-model prompts receive advisory arm-coverage guidance, and their
sanitized plans receive the same coverage assessment without rejection for
missing direct work. Coverage assessment is persisted with accepted plans (see
above); it is not performed volume. The
four-set value is a product default, not a validated minimum or optimum.
The ordering lives in `DeterministicWorkoutPlannerEngine.rankCandidates` (with
`pickFirstNonSore` for the skip); `SubstituteExerciseUseCase` reuses both to swap
one slot of an accepted plan, so a replacement is ranked like a fresh pick.
`ReplaceOccurrenceExerciseUseCase` is a separate manual occurrence-only action: same-pattern
equipment/exclusion/soreness-eligible alternatives are listed by name/id, and a candidate's own
current baseline is converted at the unchanged reps with the occurrence activation's deload flag
and equipment ceiling. Missing/withheld history produces no numeric load. Confirmation rechecks
the preview and delegates to the atomic untouched-slot write; accepted-plan substitution is not
reused because it mutates the master plan and preserves the old load.
Hard equipment/exclusion eligibility is shared through
`PlannerCandidateEligibility` (used by the deterministic engine, sanitizer,
substitution and both model engines; the model engines and the deterministic
engine preflight `requireWorkouts` and fail with `NO_ELIGIBLE_EXERCISES` before
generating when a requested focus has no eligible work).
When replacing a qualifying biceps/triceps isolation slot, coverage-preserving same-pattern
replacements rank first; other same-pattern options remain available for explicit user choice.
Weighted fatigue is the maximum of
`involvementWeight * muscleFatigue`; skip/reduce decisions instead use raw
fatigue of targeted muscles. The engine reads `reduceThreshold`, `skipThreshold`
and `targetedInvolvementCutoff` from `FatigueConfig.kt`; inspect those fields for
current values rather than introducing separate planner threshold constants.

```text
MIN_DAYS = 2; MAX_DAYS = 6
MIN_SETS = 1; MAX_SETS = 8
CUSTOM_EQUIPMENT_RANK = 20
```

`PreferredWeekdaysRepository` (`:core:userdata`) stores undecided (`null`) or 2–6 preferred weekdays.
`SqlDelightPreferredWeekdaysRepository` saves a selection and its planning frequency atomically;
clearing to undecided preserves frequency. It creates or modifies no training block. The Routines
activation dialog resolves the saved preferences when it opens and uses the Mon/Wed/Fri
`DefaultWeekdays` while they are undecided or when the read fails; opening it writes nothing and
confirmation remains
explicit. The Plan tab's Start-block scheduler still derives its weekdays from the plan rather than
from these preferences.

`SplitResolver` resolves AUTO to FULL_BODY for 2–3 days, UPPER_LOWER for 4,
and PUSH_PULL_LEGS otherwise. Foci cycle through the selected split.

`PeriodizationConfig` defaults:

```text
cycleLength = 4
deloadWeek = 4
deloadVolumeScale = 0.7
deloadIntensityScale = 0.8
isDeload(weekNumber) = weekNumber == deloadWeek
nextWeek(currentWeek) = (currentWeek % cycleLength) + 1
```

A week is an accepted-plan ordinal, not a calendar week. Deload sets are rounded
and bounded to at least one. The next request advances from the latest accepted
plan; progression pauses increments when that accepted plan is a deload.
