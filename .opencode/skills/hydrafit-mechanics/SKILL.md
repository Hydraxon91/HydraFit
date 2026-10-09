---
name: hydrafit-mechanics
description: Use when implementing or debugging HydraFit fatigue, workout planning, SQLDelight repositories or migrations, Koin wiring, or feature UI. Provides project-specific formulas, defaults, source locations, and implementation recipes complementary to AGENTS.md.
---

# HydraFit mechanics

This is a code-level reference. Behavioral rules, approvals, module boundaries,
and verification requirements remain in `AGENTS.md`; future work remains in
`PLANS.md`.

Recipes are references, not permission to change behavior. Read the named source
and relevant tests before using a recipe; source is authoritative when it differs
from this skill or an older design note. Schema versions are
discovered from the migration directory rather than treated as fixed constants.
All paths are relative to the repository root.

## Source lookup

Kotlin package roots:

- Domain: `core/domain/src/commonMain/kotlin/com/hydrafit/app/core/domain/`
- Database: `core/database/src/commonMain/kotlin/com/hydrafit/app/core/database/`
- Network: `core/network/src/commonMain/kotlin/com/hydrafit/app/core/network/`
- Local inference: `core/llm/src/commonMain/kotlin/com/hydrafit/app/core/llm/`
- User-data ports: `core/userdata/src/commonMain/kotlin/com/hydrafit/app/core/userdata/`
- Shell: `shared/src/commonMain/kotlin/com/hydrafit/app/`
- Feature: `feature/<name>/src/commonMain/kotlin/com/hydrafit/app/feature/<name>/`

| Task | Starting points under these roots |
| --- | --- |
| Fatigue scores | Domain `fatigue/FatigueCalculator.kt`, `FatigueConfig.kt` |
| Planner request construction | Domain `engine/ObserveWorkoutPlanInputsUseCase.kt`; database `SqlDelightWorkoutPlanSourcesRepository.kt` |
| Exercise selection | Domain `engine/DeterministicWorkoutPlannerEngine.kt`, `SplitResolver.kt` |
| AI output validation | Domain `engine/WeeklyPlanJson.kt`, `WeeklyPlanSanitizer.kt`, `PlanVarietyEnforcer.kt` |
| Catalog edits and seeding | Database `SqlDelightExerciseCatalog.kt`, `ExerciseEncoding.kt`, `DefaultExercises.kt`, `SeedExerciseCatalog.kt` |
| Workout storage and snapshots | Database `SqlDelightWorkoutLogRepository.kt`; domain `workout/WorkoutSet.kt` |
| Engine selection | Shell `DefaultWorkoutPlannerEngineProvider.kt`, `DomainModule.kt` |
| Routine templates and scheduling | Domain `routine/RoutineTemplate.kt`, `routine/SaveRoutineTemplateUseCase.kt`, `schedule/ActivationUseCases.kt`, `schedule/OccurrenceLifecycleUseCases.kt`, `schedule/TrainingBlockUseCases.kt`; database `SqlDelightRoutineTemplateRepository.kt`, `SqlDelightWorkoutScheduleRepository.kt` |
| Platform adapters | `shared/src/androidMain/kotlin/com/hydrafit/app/AndroidDatabaseModule.kt` and corresponding `iosMain/IosDatabaseModule.kt` |

## Fatigue: bounded, session-aware calculation

Sources: domain `fatigue/FatigueCalculator.kt`, `FatigueConfig.kt`, `LoggedSet.kt`,
and `MuscleTarget.kt`. Tests: `fatigue/FatigueCalculatorTest.kt` and
`FatigueReplayTest.kt` under `core/domain/src/commonTest/kotlin/com/hydrafit/app/core/domain/`.
`docs/fatigue-formula.md` explains the bounded response, but its inferred-session
description predates explicit session ids; use the current source for segmentation.

The implementation is not a decayed set count divided by a reference volume:

- Ignore warm-ups; compute relative-load and RIR effort factors for working sets.
- Sort by timestamp and batch by `(timestampMillis, sessionId)`. Equal timestamps
  with different session ids remain separate batches. All muscle groups are returned.
- Each muscle has compound and isolation components sharing one bounded headroom
  and one cumulative session stimulus. Decay both components between batches using
  their respective effective half-lives; apply isolation before compound within a batch.
- Reset session stimulus when explicit ids change or on a null/non-null boundary.
  Only two null ids use the legacy `sessionGap`; resetting stimulus does not reset fatigue.
- Sum involvement × rep factor × relative load × effort for each muscle/type batch
  in canonical order, then add a diminishing dose to that type's component.

Conceptual dose/update (read source for batching and floating-point handling):

```text
repsFactor = clamp((reps / referenceReps)^repExponent, minRepMultiplier, maxRepMultiplier)
stimulus = sum(involvement * repsFactor * relativeLoad * effort)
dose = diminishingScale * ln1p(stimulus / (diminishingScale + sessionStimulus))
increment = (1 - isolationFatigue - compoundFatigue) * -expm1(-dose / capacityScale)
fatigueOfThisType += increment
sessionStimulus += stimulus
decay(elapsedMillis, H) = 2 ^ (-max(elapsedMillis, 0) / H)
```

Relative load uses the best eligible Epley estimate from strictly earlier sets of
the same exercise within `referenceWindow`. Missing weight/reference or incomparable
reps makes it neutral (`1.0`). Missing RIR uses `defaultRir` internally; it is never
written back as measured effort. Reps, load and RIR do affect the calculation.

After the last batch, decay both components to supplied `nowMillis`; the score
is their sum, bounded strictly below `1.0` even after floating-point rounding.
Empty working input produces zero. `CalculateMuscleFatigueUseCase` is the entry point.
Read current capacity/diminishing scales, base half-lives, compound/isolation scales,
load/RIR factors and thresholds from `FatigueConfig.kt` rather than copying constants
from a recipe. Its base half-lives are distinct from the scaled effective half-lives.

## Muscle mapping and catalog persistence

The database encodes involvements as comma-separated `MUSCLE:weight` pairs,
for example `CHEST_UPPER:0.7,CHEST_LOWER:0.7,TRICEPS:0.5`.
`ExerciseEncoding.kt` owns encoding and decoding; encoding sorts by muscle name.
`MuscleGroup` has 21 groups (`ADDUCTORS`, `HIP_ABDUCTORS`, `TRAPS`, `NECK` added
2026-10-06; `TRAPS` is excluded from planner volume-deficit targeting and must not
double-count with `UPPER_BACK`); `decodeInvolvements` expands legacy broad names
(`CHEST`/`BACK`/`SHOULDERS`/`CORE`) on read, so historical rows keep contributing.

There are three distinct locations for this map:

1. `exercise.involvements`: catalog defaults or custom exercise data.
2. `exerciseOverride.involvements`: an optional built-in exercise edit.
3. `workoutSet.involvements`: effective muscle mapping snapshotted when logged.

`CustomExerciseDedupe` runs at startup after seeding: a custom exercise whose name
matches a seeded exercise after normalization (trim, collapse whitespace, lowercase) is
merged into the seeded id. History and plan entries are reassigned; the custom's
differing equipment, pattern, unilateral flag, load capability and involvement weights are
preserved as a canonical override (values matching the built-in are not materialized, and a
legacy `UNSPECIFIED` custom never overwrites the curated default); personal records are
merged by weight, then reps, then timestamp; the custom override and row are removed.
The 21-group set and the muscle-split mechanism are recorded in PLANS.md and
`docs/exercise-catalog-sources.md`.

`SqlDelightExerciseCatalog` overlays nullable override fields onto catalog rows.
Display groups are derived from the resolved map: weight `>= 0.7` is primary,
and lower positive weights are secondary. The SQL legacy primary/secondary
columns were removed; these remain domain/display concepts.

Domain `equipment/Exercise.kt` provides `effectiveInvolvements`. Its fallback
for an empty explicit map is primary muscles at `1.0` plus secondary muscles at
`0.5`. Fatigue uses `MuscleTarget(muscle, weight)` values.

Editor tiers: None, Low `0.3`, Mid `0.5`, High `0.7`, Primary `1.0`.
Stored values are doubles, not a tier enum.

`DefaultExercises.kt` is the seed catalog (baseline + `DefaultExercisesCatalogC1`).
`SeedExerciseCatalog` inserts missing rows, backfills involvements only where null,
then idempotently normalizes legacy built-in weights onto the editor tier scale
(`0.6 → 0.7`, `0.4 → 0.5`, `0.2 → 0.3`). It then rewrites built-in rows that still
hold pre-MUS-P1 broad names (`CHEST`/`BACK`/`SHOULDERS`/`CORE`) from the current
seed (`repairLegacyInvolvementNames`; anchored LIKE patterns avoid matching the split
names), so an upgraded install matches a fresh one. Custom rows, user overrides and
historical set snapshots are left untouched. It runs off-main at startup via
`DatabaseStartupMaintenance`. `DefaultExercisesDataQualityTest` asserts every
built-in row satisfies `MovementPatternGuardrail` except five pinned fresh-install
classification conflicts (tracked in `docs/live-testing-2026-10-08.md`).

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

## AI output and runtime mechanics

Both AI engines use domain `engine/WeeklyPlanJson.kt` to parse output, followed
by `WeeklyPlanSanitizer`. The parser extracts a balanced JSON object from prose,
collects day objects, clamps sets to `1..10` and reps to `1..100`, and maps an
unknown focus to FULL_BODY. Parsing success is not plan validation.

The sanitizer removes unknown/unavailable exercises, applies app-controlled
sets and reps, gates suggested weights, scales deload loads, clamps equipment
ceilings, and stamps request week/cycle values. Suggested weights must be
positive and at most `1_000.0` kg before scaling/clamping.

`PlanVarietyEnforcer` removes within-day duplicate exercises and repeated compound
exercise ids across days; accessories may repeat. It allows repeated foci for
cyclic splits and rejects a week with fewer distinct foci than the resolved split
expects, insufficient days, or a day below the exercise floor after filtering.
It does not choose substitutes or repair the focus schedule. The local schema's
`items.anyOf` does not pin focus by array position; distinguish schema generation
constraints from post-generation validation when diagnosing fallback.

`PlannerExerciseCounts.kt` owns the distinct prompt/schema targets and looser
validation floor. Read its values and comments before diagnosing count failures;
do not assume the generation target and post-filter floor should be identical.

- Gemini implementation: network `GeminiWorkoutPlannerEngine.kt` and
  `GeminiDtos.kt`. HTTP transient retries: `MAX_RETRIES = 2`, base delay
  `1_000` ms, maximum delay `8_000` ms. HTTP failures surface as
  `PlanGenerationException`; the current sanitizer-rejection branch calls its
  deterministic fallback. This describes existing source, not a pattern to copy:
  follow AGENTS.md's explicit engine-substitution rule for new/changed rejection paths.
- Local implementation: `LocalLlmWorkoutPlannerEngine.kt`, with
  `MAX_ATTEMPTS = 2` used only to retry a parsed-but-rejected (incomplete) plan.
  A generation exception, unavailability or OOM falls straight back; cancellation
  is rethrown rather than converted into fallback. Prompt numbers are 1-based
  catalog indexes mapped back to ids before sanitization.
- Native runtime: `core/llm/src/androidMain/kotlin/com/hydrafit/app/core/llm/LiteRtLmTextGenerator.kt`.
  It caches the Engine but creates/closes a Conversation per call. Reusing a
  failed Conversation can cause "roles must alternate" errors. `release()` drops
  the cached Engine; it is called on a generation failure/timeout, on model
  removal, and (off main, via `OnDeviceEngineLifecycle` collecting
  `EnginePreferenceRepository.engineFlow()`) whenever a non-local engine is selected.
- Generation wait: the waiting thread runs `awaitGeneration` and aborts on the
  absolute `GENERATION_TIMEOUT_MILLIS` (300 s) or on no output for
  `STALL_TIMEOUT_MILLIS` (90 s). It calls `conversation.cancelProcess()`, waits out
  the cancellation grace period, then returns the abort reason — a separate
  watchdog never signals completion. Only real output growth resets the stall
  clock.
- Imported models live at `filesDir/on_device_llm.litertlm`; filename target
  classification is persisted separately. Real devices try NPU → GPU → CPU (NPU
  packs) or GPU → CPU (portable); **emulators use CPU only** (`looksLikeEmulator`),
  because the GPU path degrades to WebGPU and spins the host. Installed-file
  presence is not proof that native inference works.

## SQLDelight recipe and migration fixtures

Schema directory:
`core/database/src/commonMain/sqldelight/com/hydrafit/app/core/database/`.

Query files: `Equipment.sq`, `Exercise.sq`, `ExerciseExclusion.sq`, `ExerciseOverride.sq`,
`ExercisePreference.sq`, `PersonalRecord.sq`, `PlanHistory.sq`, `PlannerEngine.sq`,
`PlanVolumeExplanation.sq`, `PlanVolumeExplanationState.sq`, `RoutineTemplate.sq`,
`TrainingSchedule.sq`, `UserEquipment.sq`, `WorkoutLog.sq`, and `WorkoutSession.sq`.
`PlanHistory.sq`'s `updateEntryExerciseIdAtPosition` swaps one entry's
`exerciseId`/`exerciseName`/`suggestedWeightKg` in place (no schema change) for
`SubstituteExerciseUseCase`; the entry's `sets`/`reps` are untouched. Editing a
routine template upserts kept workouts/slots by id and deletes the removed ones,
so reordering preserves identity.

`N.sqm` migrates from version N to N+1. At authoring, migrations were `1.sqm`
through `26.sqm`, producing schema 27. Determine the next version from the
current directory/generated Schema rather than copying this snapshot.

`core/database/build.gradle.kts` declares:

```kotlin
sqldelight {
    databases {
        create("HydraFitDatabase") {
            packageName.set("com.hydrafit.app.core.database")
        }
    }
}
```

Named SQL statements become query methods:

```sql
selectAllSets:
SELECT * FROM workoutSet ORDER BY performedAt, id;
```

```kotlin
database.workoutLogQueries.selectAllSets().executeAsList()
```

Generated files land under
`core/database/build/generated/sqldelight/code/HydraFitDatabase/commonMain/`.
`AndroidDatabaseDriverFactory` and `NativeDatabaseDriverFactory` pass
`HydraFitDatabase.Schema` to their driver; the driver handles creation/upgrade.
The default database filename is `hydrafit.db`.

For a repository test, create `JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)`,
call `HydraFitDatabase.Schema.create(driver)`, and construct the repository.
For a migration test, build the old tables with raw SQL, insert representative
old rows, call `Schema.migrate(driver, from, to)`, and assert retained values.
See `WorkoutSetWeekDayMigrationTest.kt` and `InvolvementConversionMigrationTest.kt`
under `core/database/src/androidHostTest/kotlin/com/hydrafit/app/core/database/`.

Fixture trap: migrating to `Schema.version` runs all later migrations, so the
fixture needs every table they touch. Use an explicit end version for a scoped
test. Generated current queries can also expect columns absent from a scoped
old schema; inspect the particular column via raw SQL where appropriate rather
than disguising an old fixture as the latest schema.

`20.sqm` demonstrates a table rebuild for column removal: Android minSdk 24
predates SQLite 3.35's `DROP COLUMN` support.

## Koin binding and verification map

Composition files:

| Location | Export / responsibility |
| --- | --- |
| Shell `DomainModule.kt` | `domainModule`: use cases (incl. `SubstituteExerciseUseCase` / `PlanBuilderActions`), configs, deterministic/local engines, provider |
| Database `DatabaseModule.kt` | `databaseModule`: DB and repository implementations |
| Network `NetworkModule.kt` | `networkModule`: HTTP client and Gemini engine |
| Feature `EquipmentFeatureModule.kt` | `equipmentModule` |
| Feature `FatigueHeatmapModule.kt` | `fatigueHeatmapModule` |
| Feature `SplitBuilderModule.kt` | `splitBuilderModule` |
| Feature `WorkoutLoggerModule.kt` | `loggerModule` |
| Feature `SettingsModule.kt` | `settingsModule` |
| Platform `AndroidDatabaseModule.kt` / `IosDatabaseModule.kt` | Driver, clock, key store, local runtime adapters |

Shell `Koin.kt` aggregates the modules and invokes catalog/equipment seeding
after startup. `core/llm` has no separate Koin module.
Existing bindings use `single { ... }`, `single<Port> { Implementation(...) }`,
`singleOf(::UseCase)`, and `viewModel { FeatureViewModel(...) }`.

The verification files are:

- `core/database/src/androidHostTest/kotlin/com/hydrafit/app/core/database/DatabaseModuleVerificationTest.kt`
- `core/network/src/androidHostTest/kotlin/com/hydrafit/app/core/network/NetworkModuleVerificationTest.kt`
- `shared/src/androidHostTest/kotlin/com/hydrafit/app/KoinModulesVerificationTest.kt`

The shared test's `allModulesResolve` checks the aggregate graph.
`theDomainUseCaseGraphResolvesAtRuntime` actually resolves domain use cases with
fake ports, covering missing collaborators that static verification can miss.
Network verification supplies external types including `HttpClientEngine`.
Platform Context/Keystore and iOS wiring are not proved by these host tests.

## Feature and test implementation recipes

For logger work, the concrete files are `WorkoutLoggerModule.kt`,
`WorkoutLoggerScreen.kt`, `WorkoutLoggerViewModel.kt`, and
`WorkoutLoggerUiState.kt` under the feature package root.
`WorkoutLoggerScreen.kt` contains `loggerDestination`, `loggerGraph`,
`WorkoutLoggerRoute`, and the stateless screen. Equipment instead keeps
navigation in `EquipmentNavigation.kt`. The ViewModel depends on the domain
aggregate `WorkoutLoggingActions` (accepted-plan + schedule context + start/
finish/skip), which replaced its direct `ObserveAcceptedPlanUseCase` dependency
to stay within the constructor budget. When a block is active the Logger shows
the selected/oldest-unresolved occurrence's remaining sets as drafts, stamps
`occurrenceId`/`occurrenceEntryId` on logged sets, and exposes Finish/partial/
Skip (the only queue advances); with no block it keeps the accepted-plan path.
Planned-set draft editing is one-off: the in-memory editor changes only the values
written by that draft's confirmation, never the accepted plan, routine or frozen
activation/occurrence. Reps, capability-appropriate load, optional RIR and optional
performed time can be edited; a draft's set count remains prescription-owned. An
external draft with null load requires an explicit choice (use the last numeric
external set, enter a weight, or log without weight); zero remains a recorded
numeric load. Bodyweight drafts remain bodyweight unless a BODYWEIGHT_ADDABLE
exercise's added-load field is explicitly revealed; clearing an existing ADDED
amount preserves ADDED+null. LEGACY_UNSPECIFIED still uses the `legacyResolution` decision queue.
Repeated draft submissions are guarded by one in-flight write. The guard spans the "Use last
logged" lookup, and cancel/reopen or context replacement invalidates that delayed lookup.
Backdated session reuse is scoped to the timestamp. If a multi-set write fails partway through,
successful writes remain and the not-yet-recorded sets are kept as a retry that carries the same
confirmed one-off values (reps, load, RIR, explicit performed time including Use-now) and frozen
occurrence slot. Repeated failures retain the remaining count and cumulative saved-set feedback;
a resolved legacy retry does not reuse its stale decision dialog. The retry survives an occurrence
refresh and is retried through the normal confirm path. Retry state is in memory only, so a process
restart starts over. A Confirm-all batch is scoped to its plan/occurrence context and stops
continuing when that context changes, without undoing successful writes. A legacy resolution dialog
takes precedence over an open draft editor, which remains in state until resolution is dismissed.
Cancel leaves the draft pending; canceling the editor discards its in-memory edits. These actions
stay within the six existing Logger ViewModel dependencies; no draft-actions Koin aggregate is
registered.

Feature navigation: each feature exports a `FeatureDestination` whose `graph` is
`(NavController) -> NavGraphBuilder.() -> Unit`; the shell passes its `NavController`, so a feature
can register an internal sub-route without a shell change. Settings uses a nested `navigation(...)`
graph (`settings` → `settings/home` + `settings/acknowledgments`) so the bottom tab stays selected on
its sub-screen. The live app version comes from `AppVersionProvider` (`:core:userdata`), bound in
`appVersionModule` (Android, from `BuildConfig.VERSION_NAME`) and `IosDatabaseModule` (returns
`"dev"`).

For routine authoring, the module is `:feature:routines`:
`RoutinesModule.kt`, `RoutinesNavigation.kt` (`routinesRoute`/
`routinesDestination`), `RoutinesScreen.kt` (list, editor, exercise picker,
activation dialog), `RoutinesViewModel.kt` and `RoutinesUiState.kt`. The
ViewModel depends on the domain aggregates `RoutineTemplateActions` and
`WorkoutScheduleActions` plus `ExerciseCatalog`, `TimeProvider` and
`WeightUnitRepository`. SplitBuilder starts a generated plan as a block via
`PlanBuilderActions.scheduleAcceptedPlan`/`acceptAndSchedule` and the
`SplitScheduleDialogState` in `SplitBuilderUiState.kt`. SplitBuilder's accepted-plan swap uses
`SplitBuilderViewModel.onSwapRequested`/`onSwapCandidateSelected` (via
`PlanBuilderActions`) and the `SwapCandidateDialog` in `SplitBuilderScreen.kt`;
the dialog is only reachable while `SplitBuilderUiState.isPlanAccepted`.

The route collects `viewModel.state` with `collectAsStateWithLifecycle` and
forwards method references to the screen. Logger also uses
`LifecycleEventEffect(ON_RESUME)` to recompute today's focus. State updates use
`MutableStateFlow.update { it.copy(...) }`.

Each feature's resources are at
`feature/<name>/src/commonMain/composeResources/values/strings.xml`.
Generated imports for logger are:

```kotlin
import hydrafit.feature.logger.generated.resources.Res
import hydrafit.feature.logger.generated.resources.logger_weight_label

// XML: <string name="logger_weight_label">Weight (%1$s)</string>
stringResource(Res.string.logger_weight_label, state.weightUnit.label)
```

Test entry points:

- Domain math and feature state: `src/commonTest/kotlin/.../<Thing>Test.kt`.
- Database/migrations, Ktor MockEngine, Koin: `src/androidHostTest/kotlin/...`.
- Fakes are usually private `FakeXxx` classes inside the test file, with a
  private builder supplying defaults. No shared fake library exists.
- Mutable repository behavior needs a mutable flow in its fake if the test
  relies on subsequent emissions; `flowOf(snapshot)` is a one-shot read.
- Use-case shape: `class ActionUseCase(private val repository: Port)` with
  `suspend operator fun invoke(...)`. Ports use `Repository` suffixes, not `I`
  prefixes; capability ports include `ExerciseCatalog` and `TimeProvider`.

## Units, time, and visual tooling details

`WeightUnit` converts display/entry values; storage and planner calculations
remain kilograms. Unilateral rows use the logged per-hand value rather than
doubling the 1RM input.

`TimeProvider.utcOffsetMillis()` defaults to zero, preserving SAM fakes such as
`TimeProvider { fixedMillis }`. Domain `time/DayOfWeek.kt` provides local-day
helpers. Platform adapters supply Android TimeZone offsets or the iOS
NSDateFormatter `Z` offset. Stored timestamps remain UTC milliseconds.
Current bucketing uses the supplied offset; it is not a historical timezone or
DST lookup for each recorded timestamp.

Run UI helpers with `bash scripts/<name>.sh`; AGENTS.md Visual Verification owns
the workflow and emulator-only restrictions. `emulator-common.sh` verifies an
explicit `ANDROID_SERIAL=emulator-<port>` and bounds every ADB call to 120s.

| Helper | Behavior |
| --- | --- |
| `deploy.sh [build-log]` | 600s-bounded `assembleDebug`, then targeted `adb install -r`; no launch. Default log `/tmp/hydrafit-deploy.log`. |
| `launch.sh [--restart]` | Foreground HydraFit; force-stop first only with `--restart`. |
| `inspect.sh [output.xml]` | Dump current UI hierarchy to `/tmp/hydrafit-ui.xml` by default; no launch/restart. Reads emulator scratch `/data/local/tmp/hydrafit-ui.xml` only after a successful dump. |
| `snap.sh [output.png]` | Capture current screen only; default `/tmp/hydrafit-screen.png`. |
| `tap.sh x y [--screenshot [output.png]]` | Tap without delay/image by default; optional image after one second. |

Deployment is only needed after relevant app changes; capture/inspection must not
reset the state being tested. Prefer hierarchy text/states/bounds for navigation
and images for layout, colour and custom graphics. These scripts do not implement
semantic selectors or readiness assertions; verify expected transitions explicitly.

Gradle task lookup by subsystem:

These are task names, not standalone invocation examples. Use the timeout/log
wrapper in AGENTS.md Key Commands and inspect the captured result separately.

```text
:core:domain:testAndroidHostTest      pure planner/fatigue tests
:core:database:testAndroidHostTest    SQL repositories and migrations
:core:network:testAndroidHostTest     Gemini/HTTP and network Koin verification
:core:llm:testAndroidHostTest         local engine and fallback tests
:feature:<name>:testAndroidHostTest   feature state tests
:shared:testAndroidHostTest           application graph/provider tests
:shared:compileKotlinIosSimulatorArm64
:core:network:compileKotlinIosSimulatorArm64
```

Version aliases live in `gradle/libs.versions.toml`; module build scripts use
`alias(libs.plugins.*)`. There is no convention-plugin `buildSrc`/`build-logic`
layer at authoring; root Gradle applies ktlint across subprojects.
