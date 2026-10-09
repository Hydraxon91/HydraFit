# Planning mechanics

Paths are relative to the repository root; package shorthand is defined in
`../SKILL.md`. Read current source and relevant tests before using this reference.

## Planner inputs and weight semantics

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
`roundToKg = 2.5`, `maxIncrements = 5`. Completion is judged against the latest
accepted prescription, not merely the heaviest logged set.
The success grouping is by exercise/local calendar day, not explicit session id;
historical sets are compared with the latest accepted prescription. Missing
quality/effort data is not execution verification. Planned improvements are in
PLANS.md (LT-10 / OF-10A-P0), not part of the current calculation.

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
triceps/shoulder isolation plus chest fly; pull adds biceps isolation; upper adds
biceps/triceps/shoulder isolation; legs/lower adds leg isolation, calf raises and
core; full-body is the union. Lunge competes in the leg/lower squat group rather
than being full-body-only. These are product reachability choices informed by
general resistance-training evidence (e.g. pec-deck/chest-fly regional
hypertrophy), not a claim that each isolation exercise is optimal.

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
