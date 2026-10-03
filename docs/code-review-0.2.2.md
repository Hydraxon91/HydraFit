# HydraFit 0.2.2 — code review

Findings are appended per slice. `file:line` evidence refers to the pinned commit for that slice.

## S1 — `:core:domain` (main sources)

- **Slice:** S1 — `:core:domain` `commonMain` only.
- **Pinned commit:** `7e04245` (tag `v0.2.1`).
- **Date:** 2026-10-03.
- **Scope:** main sources, ~2,707 Kotlin lines. Test tree read only via grep for coverage. No fixes.

**Coverage (files read):** all 69 `commonMain` `*.kt` files (`git ls-files core/domain | grep 'src/commonMain/.*\.kt$'`), grouped by package below.

- `time/`: `IsoDate.kt`, `LocalCivil.kt`, `DayOfWeek.kt`, `TimeProvider.kt`
- `unit/`: `WeightUnit.kt`
- `fatigue/`: `FatigueConfig.kt`, `FatigueCalculator.kt`, `LoggedSet.kt`, `MuscleGroup.kt`, `MuscleInvolvement.kt`, `MuscleTarget.kt`, `CalculateMuscleFatigueUseCase.kt`
- `equipment/`: `Equipment.kt`, `EquipmentTag.kt`, `Exercise.kt`, `MovementPattern.kt`, `MovementPatternGuardrail.kt`, `FilterExercisesByEquipmentUseCase.kt`
- `workout/`: `LogWorkoutSetUseCase.kt`, `WorkoutLogMutations.kt`, `WorkoutLogRepository.kt`, `WorkoutSet.kt`, `SessionConfig.kt`, `StartWorkoutSessionUseCase.kt`, `EndWorkoutSessionUseCase.kt`, `ObserveOpenWorkoutSessionUseCase.kt`, `CorrectWorkoutSetTimeUseCase.kt`, `DeleteWorkoutSetUseCase.kt`, `GetWorkoutLogUseCase.kt`, `WorkoutSession.kt`, `WorkoutSessionRepository.kt`
- `engine/` (all 38 files read): `AcceptWeeklyPlanUseCase.kt`, `AcceptedPlan.kt`, `BuildRecentWeightsUseCase.kt`, `DeterministicWorkoutPlannerEngine.kt`, `EngineAvailability.kt`, `EquipmentWeightLimit.kt`, `ExerciseCatalog.kt`, `GenerateWeeklySplitUseCase.kt`, `ObserveWorkoutPlanInputsUseCase.kt`, `OnDevicePlanProgress.kt`, `OneRepMax.kt`, `PeriodizationConfig.kt`, `PersonalRecord.kt`, `PlanGenerationException.kt`, `PlanHistoryRepository.kt`, `PlanRequest.kt`, `PlanVarietyEnforcer.kt`, `PlannedExercise.kt`, `PlannerEngineId.kt`, `PlannerExerciseCounts.kt`, `PlannerPromptFragments.kt`, `ProgressWeightsUseCase.kt`, `ProgressionConfig.kt`, `SplitFocus.kt`, `SplitResolver.kt`, `SplitType.kt`, `SuggestWeightsUseCase.kt`, `SuggestedWeightConfig.kt`, `TrainingGoal.kt`, `VolumeAwareReps.kt`, `WeeklyPlan.kt`, `WeeklyPlanJson.kt`, `WeeklyPlanSanitizer.kt`, `WeeklyVolumeTargets.kt`, `WorkoutDay.kt`, `WorkoutPlanSources.kt`, `WorkoutPlannerEngine.kt`, `WorkoutPlannerEngineProvider.kt`

**Files read with no findings:** `Equipment.kt`, `EquipmentTag.kt`, `MovementPattern.kt`, `MovementPatternGuardrail.kt`, `FilterExercisesByEquipmentUseCase.kt`, `MuscleGroup.kt`, `MuscleTarget.kt`, `CalculateMuscleFatigueUseCase.kt`, `TimeProvider.kt`, `OneRepMax.kt`, `PersonalRecord.kt`, `PlanHistoryRepository.kt`, `ExerciseCatalog.kt`, `EngineAvailability.kt`, `WorkoutPlannerEngine.kt`, `WorkoutPlannerEngineProvider.kt`, `PlanGenerationException.kt`, `OnDevicePlanProgress.kt`, `PeriodizationConfig.kt`, `ProgressionConfig.kt`, `SuggestedWeightConfig.kt`, `EquipmentWeightLimit.kt`, `WeeklyVolumeTargets.kt`, `SplitResolver.kt`, `SplitType.kt`, `SplitFocus.kt`, `TrainingGoal.kt`, `PlannerExerciseCounts.kt`, `PlannedExercise.kt`, `WorkoutDay.kt`, `WorkoutPlanSources.kt`, `GetWorkoutLogUseCase.kt`, `DeleteWorkoutSetUseCase.kt`, `EndWorkoutSessionUseCase.kt`, `ObserveOpenWorkoutSessionUseCase.kt`, `WorkoutSession.kt`, `WorkoutSessionRepository.kt`

### time + unit

**S1-001 — minor — duplication — file:line:** `core/domain/src/commonMain/kotlin/com/hydrafit/app/core/domain/time/IsoDate.kt:42`, `time/DayOfWeek.kt:30`, `time/DayOfWeek.kt:36`, `time/LocalCivil.kt:3`
- **Why it matters:** `floorDiv` is defined twice with an identical body, `floorMod` is private to `DayOfWeek`, and `MILLIS_PER_DAY` is declared privately in three files. The three copies of the same date math are easy to drift apart; a fix to one (e.g. overflow or sign handling) silently misses the others.
- **Recommendation:** Extract a single `internal` `time/TimeMath.kt` holding `MILLIS_PER_DAY`, `floorDiv`, and `floorMod`, and have `IsoDate`/`DayOfWeek`/`LocalCivil` use it.
- **Fix cost:** S

**S1-002 — nit — consistency — file:line:** `core/domain/src/commonMain/kotlin/com/hydrafit/app/core/domain/unit/WeightUnit.kt:39`
- **Why it matters:** `formatWeight` returns `rounded.toString()`, so a value whose rounding lands on a negative-zero or scientific magnitude (e.g. very small/large weights, or `-0.0`) would render as `"-0.0"`/`"1.0E-4"`. Reachable only through odd inputs, but weights flow straight from user entry.
- **Recommendation:** If weights can be negative/very small in practice, normalize `-0.0` and format explicitly; otherwise no change is needed. Low priority.
- **Fix cost:** S

### fatigue

**S1-003 — minor — dead code — file:line:** `core/domain/src/commonMain/kotlin/com/hydrafit/app/core/domain/fatigue/MuscleInvolvement.kt:3`
- **Why it matters:** `MuscleInvolvement` is never referenced by production code — the model uses free-double `involvements` and derives the primary/secondary tag split at the repository boundary (`primaryMuscles`/`secondaryMuscles` are computed in `core:database`, not from this enum). Every reference to the enum is in test code. Its `SECONDARY(0.5)` also no longer matches the documented involvement tiers (None 0.0 / Low 0.3 / Mid 0.5 / High 0.7 / Primary 1.0), so it is a stale fixture constant living in main.
- **Recommendation:** Delete the enum and replace test usages with literal weights or a shared test fixture constant. (Deletion is gated; RF decides.)
- **Fix cost:** S

**S1-004 — minor — design smell — SRP — file:line:** `core/domain/src/commonMain/kotlin/com/hydrafit/app/core/domain/workout/LogWorkoutSetUseCase.kt:18-143`
- **Why it matters:** Seed confirmed — `LogWorkoutSetUseCase` is no longer a single "log a set" action. It owns set persistence, session auto-start, day-rollover/idle expiry, backdated attach/reject logic, manual end/restart, and a `Mutex` policy (`resolveSession`/`backdatedAttachTarget`/`expireOpenSession`). Five collaborators and ~143 lines make any change to the session rules touch the logging path. `WorkoutLogMutations:10` already exists only to keep the Logger's constructor small, which is a symptom rather than a fix.
- **Recommendation:** If session rules grow further, extract the lifecycle rules into a `WorkoutSessionResolver`/`SessionPolicy` collaborator in `:core:domain` that `LogWorkoutSetUseCase` delegates to, keeping the lock at the boundary. Not urgent while rule count is stable; flag as the target seam.
- **Fix cost:** M

**S1-005 — minor — risk (performance) — file:line:** `core/domain/src/commonMain/kotlin/com/hydrafit/app/core/domain/workout/LogWorkoutSetUseCase.kt:139-142`
- **Why it matters:** Seed confirmed — `lastSetAt` calls `repository.all()` (full-table load) and filters in memory, and it runs on every logged set (`resolveSession:123`) and on expiry/restart. As the log grows, each write is O(total sets). The fatigue path has the same shape (`WorkoutLogRepository.loggedSets()`/`all()`), which is the 0.2.3 database concern.
- **Recommendation:** Add a bounded repository query (latest set by `sessionId`, `ORDER BY performedAtMillis DESC LIMIT 1`) and use it here. Coordinate with 0.2.3 rather than fixing now.
- **Fix cost:** M

**S1-006 — minor — design smell — ISP — file:line:** `core/domain/src/commonMain/kotlin/com/hydrafit/app/core/domain/workout/WorkoutLogRepository.kt:6-26`
- **Why it matters:** Seed partly confirmed from the domain side — the single interface carries 9 members spanning raw persistence (`add`, `delete`, `assignSession`, `updateSetPerformedAt`, `clear`), whole-log reads (`all`, `setsFlow`), and a derived cross-domain mapping (`loggedSets`, `loggedSetsFlow` returning fatigue `LoggedSet`s). Consumers use disjoint subsets, and every change forces all 7 test fakes to change (recorded as TS work). This is the interface-width cause of the fixture churn.
- **Recommendation:** Leave behavior as-is; when TS consolidates fixtures, consider splitting the read/derived surface (`WorkoutLogReadRepository`) from the command surface, or move `loggedSets` mapping to a mapper the fatigue path calls. Don't split speculatively.
- **Fix cost:** M

### workout (continued)

**S1-007 — major — risk (invariant) — file:line:** `core/domain/src/commonMain/kotlin/com/hydrafit/app/core/domain/workout/CorrectWorkoutSetTimeUseCase.kt:1-16`
- **Why it matters:** A time-only correction leaves `sessionId` untouched. If the new time lands between another session's sets, the timestamp-ordered session ids alternate, and `FatigueCalculator.sessionBoundary` (`FatigueCalculator.kt:181-187`) reads each alternation as a session reset — inflating fatigue resets and understating accumulated stimulus. A correction across a local-day boundary also leaves the session's `localEpochDay`/bounds stale. The KDoc already documents this, so it is a known, accepted limitation rather than an oversight; a review should still record the reachable wrong-result path.
- **Recommendation:** RF triage: either re-segment (recompute the affected session ids/`localEpochDay` on correction) or, minimally, reject/limit corrections that would reorder relative to other sessions' sets. Track as a named follow-up in PLANS.md if not fixed in 0.2.2.
- **Fix cost:** L

### engine

**S1-008 — major — bug — file:line:** `core/domain/src/commonMain/kotlin/com/hydrafit/app/core/domain/engine/DeterministicWorkoutPlannerEngine.kt:106-127` (skip at `108-109`)
- **Why it matters:** `pick(candidates)` selects the single best candidate, and if that candidate's `targetedFatigue >= skipThreshold` it returns `false` for the whole group instead of trying the next candidate. Because the comparator sorts by `weightedFatigue` (`97-104`) but the skip test uses the *raw* targeted fatigue (`241-246`), a lower-ranked candidate can be non-sore while the top-ranked one is sore. Concretely, a 0.7-weighted muscle at 0.8 fatigue (weighted 0.56) sorts ahead of a 1.0-weighted muscle at 0.57 fatigue (weighted 0.57); the first is skipped, the valid second is never tried. The compound loop then drops that movement group and the isolation fill can break, silently thinning the day. Test coverage exercises only the single-candidate case (`DeterministicWorkoutPlannerEngineTest.kt:344`), so this fall-through path is untested.
- **Recommendation:** Iterate candidates in comparator order and return the first one below `skipThreshold` (e.g. `candidates.sortedWith(comparator).firstOrNull { targetedFatigue(it, fatigue) < skipThreshold }`), rather than `minWithOrNull` + single-branch reject. Add a regression test with two same-pattern exercises where the top-ranked is sore and the other is fresh.
- **Fix cost:** S

**S1-009 — minor — design smell — SRP — file:line:** `core/domain/src/commonMain/kotlin/com/hydrafit/app/core/domain/engine/ObserveWorkoutPlanInputsUseCase.kt:12-21`
- **Why it matters:** Eight constructor dependencies, above the project's ~6-parameter guidance in AGENTS.md ("Oversized Constructors Are a Design Signal"). The class mixes source combination, fatigue computation, periodization advance, exercise-history derivation, and three separate weighting collaborators (`suggestWeights`, `buildRecentWeights`, `progressWeights`). This is the domain-side instance of the same smell as the 7-parameter Logger ViewModel seed, and it makes the plan-input pipeline harder to test and change.
- **Recommendation:** Group the weight collaborators behind one domain service (e.g. `PlanWeightSuggestions(suggestWeights, buildRecentWeights, progressWeights, periodization)` with a single `forPlan(...)` entry point), or extract a `BuildPlanRequestUseCase`. Not urgent; flag for RA/RG.
- **Fix cost:** M

**S1-010 — minor — consistency — file:line:** `core/domain/src/commonMain/kotlin/com/hydrafit/app/core/domain/engine/VolumeAwareReps.kt:10-13`
- **Why it matters:** `repsFor` ignores `sets` entirely — the parameter's only effect is the `require(sets >= 1)`. The class name, and PLANS.md line 306 ("holds per-slot volume roughly constant … `reps = roundToInt(intendedVolume / sets)`"), both still describe the superseded trade-off, while the code (and its KDoc) implements Option C/Q4c: reps are fixed per goal and sets are the volume knob. Tests confirm the current behavior (`VolumeAwareRepsTest.kt:27-42`). This is working code with a misleading signature and a stale recorded decision.
- **Recommendation:** Either drop the unused `sets` parameter (updating the three call sites and tests) or, if the volume-constant behavior is still intended, implement it. In either case align PLANS.md line 306 with the shipped Option C semantics.
- **Fix cost:** S

**S1-011 — minor — duplication — file:line:** `core/domain/src/commonMain/kotlin/com/hydrafit/app/core/domain/engine/WeeklyPlan.kt:11-19`, `AcceptedPlan.kt:19-27`
- **Why it matters:** `scheduledDay`/`dayFor` (the `dayIndex * 7 / days.size` weekday-spreading formula) are copied verbatim between `WeeklyPlan` and `AcceptedPlan`. A change to the scheduling rule must be made in both, and they are easy to drift.
- **Recommendation:** Extract one top-level/internal schedule helper (or have `AcceptedPlan.toWeeklyPlan()` delegate), and call it from both.
- **Fix cost:** S

**S1-012 — minor — consistency — file:line:** `core/domain/src/commonMain/kotlin/com/hydrafit/app/core/domain/engine/PlannerPromptFragments.kt:45-51`
- **Why it matters:** Seed confirmed. `volumeRepsGuidance` still tells the model "Scale reps to keep volume steady: fewer sets mean more reps per set", which is the pre-Q4c volume-aware trade-off. Under Option C reps are fixed per goal and the set count is the volume knob, and the sanitizer overwrites model reps regardless (`WeeklyPlanSanitizer.kt:32-36`). Behavior is correct; the prompt wording contradicts the enforced design and asks the model for something the app discards.
- **Recommendation:** Reword to match Option C — state the compound/isolation rep bands from `TrainingGoal` and say the set count is the volume control — and drop the "fewer sets mean more reps" sentence. Verify with the existing `PlannerPromptFragmentsTest`.
- **Fix cost:** S

**S1-013 — major — risk — file:line:** `core/domain/src/commonMain/kotlin/com/hydrafit/app/core/domain/engine/PlanVarietyEnforcer.kt:37-54`
- **Why it matters:** Seed confirmed. When a model repeats a compound across days, the later occurrence is stripped (`41-42`); if that leaves a day below `FLOOR_PER_DAY` the enforcer returns `null` (`44`) and the caller falls back to Deterministic. The on-device model reliably reuses compounds across days (PLANS 0.2.4), so a valid-but-repetitive week is discarded rather than repaired. This is the recorded 0.2.4 core question, but it is a code-level design choice in this slice and it is what makes the on-device engine unusable today.
- **Recommendation:** Decide in 0.2.4 (do not pick silently): give the enforcer a repair path that substitutes a different exercise of the same focus/pattern instead of dropping the slot, or make reject-vs-repair configurable and reported. Record the decision in PLANS.md.
- **Fix cost:** M

**S1-014 — nit — consistency — file:line:** `core/domain/src/commonMain/kotlin/com/hydrafit/app/core/domain/engine/PlannerPromptFragments.kt:78-79`
- **Why it matters:** `equipmentList` joins a `Set<EquipmentTag>` in iteration order, so the same equipment selection can render in different orders across prompt builds, adding avoidable nondeterminism to model input (and any prompt golden tests).
- **Recommendation:** Sort by `displayName` (as `equipmentCapsLine` already does) before joining.
- **Fix cost:** S

### Seed-observation status (S1-resident)

- Grown session-aware `LogWorkoutSetUseCase`: **confirmed** — S1-004.
- `WorkoutLogRepository` width / fakes churn: **confirmed from the domain side** — S1-006 (fixture consolidation itself is TS work).
- `LogWorkoutSetUseCase` and fatigue path load all sets via `all()`: **confirmed** — S1-005 (`lastSetAt`), implementation detail deferred to S2/0.2.3.
- Use-case/Koin wiring placement: **outside S1** for the Koin half (S6); the use-case concentration is S1-004/S1-009.
- Error handling and logging consistency: **no finding** in S1 — domain raises `PlanGenerationException` and otherwise returns nulls; logging lives behind ports outside this slice.
- Coroutine scope and dispatcher handling: **no finding** — domain use cases are `suspend`/`Flow`, no hardcoded scopes or dispatchers; the two `combine`/`onStart` usages are correct.
- expect/actual boundaries: **refuted for S1** — `:core:domain` is `commonMain`-only; no expect/actual declarations exist here.
- Stale `PlannerPromptFragments.volumeRepsGuidance`: **confirmed** — S1-012.
- `PlanVarietyEnforcer` versus on-device output: **confirmed** — S1-013.
- Logger ViewModel 7 params / testFixtures sharing / heatmap ticker flakiness: **outside S1** (features S4/S5, tests TS2–TS4).

### S1 finding summary

- blocker: 0
- major: 3 (S1-007, S1-008, S1-013)
- minor: 9 (S1-001, S1-003, S1-004, S1-005, S1-006, S1-009, S1-010, S1-011, S1-012)
- nit: 2 (S1-002, S1-014)
- total: 14

## S2 — `:core:database` + `:core:userdata` + `:core:navigation` (main sources)

- **Slice:** S2 — main sources of `:core:database` (1599), `:core:userdata` (298), `:core:navigation` (10); 483 lines of `.sq`/`.sqm`.
- **Pinned commit:** `7e04245` (tag `v0.2.1`).
- **Date:** 2026-10-03.
- **Scope:** main sources only. Test tree read only via grep for coverage. No fixes.

**Coverage (files read):** all 37 main `.kt` files across every non-test source set, plus all 9 `.sq` and 24 `.sqm` files.

- `:core:navigation`: `FeatureDestination.kt`
- `:core:userdata` commonMain: `EquipmentRepository.kt`, `ExerciseOverrideRepository.kt`, `PersonalRecordRepository.kt`, `EquipmentSelectionRepository.kt`, `OnDeviceModelManager.kt`, `NpuDeviceDetector.kt`, `OnDeviceModelTarget.kt`, `ModelUpdateResult.kt`, `ApiKeyStore.kt`, `EnginePreferenceRepository.kt`, `TrainingGoalRepository.kt`, `WeightUnitRepository.kt`
- `:core:userdata` platform: `AndroidKeystoreApiKeyStore.kt`, `NoopApiKeyStore.kt`
- `:core:database` commonMain: `DatabaseDriverFactory.kt`, `DatabaseModule.kt`, `ExerciseEncoding.kt`, `SeedEquipmentCatalog.kt`, `SeedExerciseCatalog.kt`, `DefaultExercises.kt`, `SqlDelightEquipmentSelectionRepository.kt`, `SqlDelightExerciseOverrideRepository.kt`, `SqlDelightTrainingGoalRepository.kt`, `SqlDelightWeightUnitRepository.kt`, `SqlDelightPersonalRecordRepository.kt`, `SqlDelightWorkoutSessionRepository.kt`, `SqlDelightEquipmentRepository.kt`, `SqlDelightExerciseCatalog.kt`, `SqlDelightEnginePreferenceRepository.kt`, `SqlDelightWorkoutPlanSourcesRepository.kt`, `SqlDelightCustomExerciseRepository.kt`, `SqlDelightPlanHistoryRepository.kt`, `SqlDelightWorkoutLogRepository.kt`, `WorkoutSessionBackfill.kt`
- `:core:database` platform: `AndroidDatabaseDriverFactory.kt`, `NativeDatabaseDriverFactory.kt`
- SQL: `WorkoutLog.sq`, `Exercise.sq`, `PlanHistory.sq`, `PlannerEngine.sq`, `ExerciseOverride.sq`, `Equipment.sq`, `WorkoutSession.sq`, `UserEquipment.sq`, `PersonalRecord.sq`; `1.sqm`..`24.sqm`

**Files read with no findings:** `FeatureDestination.kt`, `NpuDeviceDetector.kt`, `OnDeviceModelTarget.kt`, `ModelUpdateResult.kt`, `EnginePreferenceRepository.kt`, `TrainingGoalRepository.kt`, `WeightUnitRepository.kt`, `PersonalRecordRepository.kt`, `EquipmentSelectionRepository.kt`, `EquipmentRepository.kt`, `DatabaseDriverFactory.kt`, `AndroidDatabaseDriverFactory.kt`, `NativeDatabaseDriverFactory.kt`, `SeedEquipmentCatalog.kt`, `SqlDelightEquipmentSelectionRepository.kt`, `SqlDelightTrainingGoalRepository.kt`, `SqlDelightWeightUnitRepository.kt`, `SqlDelightPersonalRecordRepository.kt`, `SqlDelightWorkoutSessionRepository.kt`, `SqlDelightEnginePreferenceRepository.kt`, `SqlDelightExerciseOverrideRepository.kt`, all `.sq`, all `.sqm`, `DefaultExercises.kt` (no duplicate ids; explicit involvements in (0,1]).

### `:core:database` — SQL, seeds, repositories

**S2-001 — major — bug — file:line:** `core/database/.../ExerciseEncoding.kt:19-23`, `SqlDelightExerciseCatalog.kt:34`, `SqlDelightWorkoutLogRepository.kt:31`
- **Why it matters:** `encodeInvolvements` maps an **empty** map to `null`, and the read paths treat a `null` override as "no override": `decodeInvolvements(override?.involvements ?: involvements)`. So an override that legitimately clears every muscle is stored as `NULL` and decoded as the *seed* weights. The equipment editor sends exactly that empty map for an existing built-in when the user removes all muscles (`EquipmentProfilerViewModel.kt:347` → `writeBuiltInOverrides`; the empty guard at `EquipmentProfilerViewModel.kt:295` only applies to new custom exercises), so the edit is silently discarded and the original muscles remain. `null` currently conflates "not overridden" with "overridden to empty".
- **Recommendation:** Distinguish the two states — e.g. encode the empty map as `""` (non-null) and decode `""` to an empty map, or add an explicit `involvementsOverridden` flag/column. Apply consistently to `exercise`, `exerciseOverride`, and the `workoutSet` snapshot. Add a test that clearing all muscles on a built-in round-trips.
- **Fix cost:** M

**S2-002 — minor — duplication/dead code — file:line:** `core/database/.../ExerciseEncoding.kt:13-16`
- **Why it matters:** `encodeMuscles` and `decodeMuscles` have no callers anywhere in the repo (the muscle tag split is now derived from `involvements`). `decodeMuscles` also uses an unguarded `MuscleGroup::valueOf`, which would throw on an unknown token if it were ever used. Dead code that looks load-bearing.
- **Recommendation:** Delete both functions.
- **Fix cost:** S

**S2-003 — minor — bug/consistency — file:line:** `core/database/.../SqlDelightWorkoutLogRepository.kt:77-79` and `103-105`
- **Why it matters:** `loggedSets()`/`loggedSetsFlow()` build `targetsByExercise` from the seed `exercise.involvements` only, while the sibling `compoundByExercise` (`:129-138`) resolves overrides first. For a legacy set whose snapshot is absent, fatigue targets therefore ignore the user's exercise overrides even though the exercise type does not. The two resolutions should agree.
- **Recommendation:** Resolve targets through the same override-aware path as the catalog/`compoundByExercise` (e.g. `override.involvements ?: row.involvements`).
- **Fix cost:** S

**S2-004 — minor — risk (migration) — file:line:** `core/database/build.gradle.kts:50-55`
- **Why it matters:** The SQLDelight block sets neither `verifyMigrations` nor `schemaOutputDirectory`, so the build never checks that the `.sq` schema equals the result of applying `1.sqm..24.sqm`. A future `.sq` edit (or a mis-ordered migration) can pass `ktlintCheck`/tests while breaking upgrades for existing installs; the hand-written `*MigrationTest` files only assert the expectations they encode. A read of the chain this session found the current schema and migrations consistent, but nothing enforces it.
- **Recommendation:** Enable `verifyMigrations = true` with a checked-in schema snapshot directory (or add a CI step that generates and diffs the schema). This is a build change and must be approved separately.
- **Fix cost:** M

**S2-005 — minor — bug — file:line:** `core/database/.../SqlDelightEquipmentRepository.kt:23-29` and `46-47`
- **Why it matters:** `add` slugifies the display name to a tag id and then uses plain `insert`, so two distinct names that normalize to the same id (e.g. "Lat Pulldown" / "Lat-Pulldown") collide and the second throws a constraint exception out of a routine "add equipment" action. `CustomExerciseRepository.uniqueId` already solves exactly this for exercises.
- **Recommendation:** Reuse the uniqueness approach (suffix on collision) or surface a typed error; at minimum validate before insert.
- **Fix cost:** S

**S2-006 — minor — consistency — file:line:** `core/database/.../SqlDelightExerciseCatalog.kt:50`, `SqlDelightCustomExerciseRepository.kt:120`
- **Why it matters:** The `0.7` primary/secondary threshold is redeclared as a private constant in both database classes (and again as `MovementPatternGuardrail.PRIMARY_THRESHOLD` in `:core:domain`, which the feature layer imports). Three local copies of one domain rule invite drift.
- **Recommendation:** Reference one shared constant (the domain `MovementPatternGuardrail.PRIMARY_THRESHOLD`, or a domain-owned constant) from both repositories.
- **Fix cost:** S

### `:core:userdata` — ports

**S2-007 — minor — design smell / risk (concurrency) — file:line:** `core/userdata/.../ApiKeyStore.kt:3-9`, `OnDeviceModelManager.kt:9-20`, `AndroidKeystoreApiKeyStore.kt:20-35`
- **Why it matters:** Both ports are synchronous, so their implementations must do Keystore crypto / file copy on the caller's thread. `SettingsViewModel.saveApiKey` calls `apiKeyStore.save(key)` directly (not in `viewModelScope`) and `refresh()` calls `load()` from a `viewModelScope` (Main) — both run AES/Keystore work on the main thread. `save()` also lets Keystore exceptions escape uncaught (`AndroidKeystoreApiKeyStore.kt:27-35`), unlike `load()` which swallows them (`:20-24`), so a Keystore failure during save can crash the settings screen while a failure during load silently reports "no key". The on-device model port has the same shape; PLANS records that `:shared` only keeps it off-main by convention.
- **Recommendation:** Make these ports `suspend` (or add suspend variants) so implementations own their dispatcher and callers cannot block main; give load and save the same explicit failure contract.
- **Fix cost:** M

### Nits

**S2-008 — nit — consistency — file:line:** `core/database/.../SqlDelightPlanHistoryRepository.kt:33-34,53,56`
- **Why it matters:** `lastInsertedPlanId` (`SELECT last_insert_rowid()`) is reused to fetch the inserted *day* id, so the query name is misleading at the second call site.
- **Recommendation:** Rename to `lastInsertedRowId` or add a separate `lastInsertedDayId` alias.
- **Fix cost:** S

**S2-009 — nit — consistency — file:line:** `core/database/.../WorkoutSessionBackfill.kt:26`
- **Why it matters:** The KDoc says "the Logger does not stamp session ids until S4", but `LogWorkoutSetUseCase` already stamps `sessionId` on every logged/backdated/draft set, so later launches no longer create null rows by design.
- **Recommendation:** Update the comment to describe the actual condition (only pre-session-feature rows can be null).
- **Fix cost:** S

### Seed-observation status (S2-resident)

- `all()` / full-table loads on hot paths: **confirmed** — `SqlDelightWorkoutLogRepository.all()/loggedSets()/loggedSetsFlow()` (`:52,74,98`), `setsFlow` (`:55`), and `SqlDelightPlanHistoryRepository.observeHistory()` (`:28-35`) read whole tables and re-join the catalog; the write-path `lastSetAt` was S1-005. Deferred to 0.2.3.
- Use-case/Koin wiring placement (`DatabaseModule`): **confirmed as a DI-only module** — 19 `single` bindings, no logic; the `get()` chains are the intended exception to the oversized-constructor rule.
- Catalog seeding + `movementPattern`/session backfills at startup: **confirmed** — `Koin.kt:31-33` runs `SeedExerciseCatalog.seed()`, `SeedEquipmentCatalog.seed()`, `WorkoutSessionBackfill.backfill()` on every launch; both seeders are idempotent (`insertIgnore` + conditional `updateInvolvements WHERE involvements IS NULL`) and the backfill only touches `sessionId IS NULL`. No finding.
- Immutable logged snapshots: **confirmed with the S2-001 caveat** — `add` snapshots `involvements` at log time (`:31`); the null-vs-empty ambiguity affects zero-muscle overrides only.
- Migration discipline / schema version: **migrations are internally consistent** (manual cross-check of `1.sqm..24.sqm` against the `.sq` files this session); the gap is that the build never verifies it (S2-004).
- 7 `WorkoutLogRepository` fakes / `testFixtures` sharing: **outside S2** — test-side, TS3/TS4.

### S2 finding summary

- blocker: 0
- major: 1 (S2-001)
- minor: 6 (S2-002, S2-003, S2-004, S2-005, S2-006, S2-007)
- nit: 2 (S2-008, S2-009)
- total: 9

## S3 — `:core:network` + `:core:llm` (main sources)

- **Slice:** S3 — main sources of `:core:network` (439) and `:core:llm` (870).
- **Pinned commit:** `7e04245` (tag `v0.2.1`).
- **Date:** 2026-10-03.
- **Scope:** main sources only. Test tree read only via grep for coverage. No fixes.

**Coverage (files read):**

- `:core:network` commonMain: `GeminiConfig.kt`, `NetworkModule.kt`, `GeminiHttpClient.kt`, `GeminiDtos.kt`, `GeminiWorkoutPlannerEngine.kt`
- `:core:llm` commonMain: `OnDeviceTextGenerator.kt`, `OnDevicePlannerLogger.kt`, `OnDeviceModelTargetClassifier.kt`, `OnDeviceSampler.kt`, `LocalLlmWorkoutPlannerEngine.kt`
- `:core:llm` androidMain: `AndroidOnDeviceModelManager.kt`, `AndroidOnDevicePlannerLogger.kt`, `LiteRtLmTextGenerator.kt`
- `:core:llm` iosMain: `UnsupportedOnDeviceTextGenerator.kt`

**Files read with no findings:** `GeminiConfig.kt`, `NetworkModule.kt`, `GeminiHttpClient.kt`, `GeminiDtos.kt`, `OnDeviceTextGenerator.kt`, `OnDevicePlannerLogger.kt`, `OnDeviceModelTargetClassifier.kt`, `OnDeviceSampler.kt`, `UnsupportedOnDeviceTextGenerator.kt`, `AndroidOnDevicePlannerLogger.kt`.

### `:core:network`

**S3-001 — major — bug/consistency — file:line:** `core/network/.../GeminiWorkoutPlannerEngine.kt:102`
- **Why it matters:** On a successful HTTP response whose content is unusable, the engine does `sanitizer.sanitize(plan, request) ?: fallback.generatePlan(request)` — it silently returns the Deterministic plan. `parseWeeklyPlan` is lenient and returns an empty plan for a no-content/garbled reply, and the sanitizer then rejects it, so the user who selected Gemini gets a built-in plan with no error and no log. This contradicts the recorded decision (PLANS.md line 291: Gemini "never silently falls back to Deterministic") and defeats the `INVALID_RESPONSE` reason that `SplitBuilder` is built to surface. The injected `fallback` is only reachable here.
- **Recommendation:** Throw `PlanGenerationException(transient = false, reason = PlanFailureReason.INVALID_RESPONSE, …)` when `sanitize` returns null (include the compact rejection summary, as the local engine does), so the ViewModel can show the reason and Retry. Remove the `fallback` dependency from this engine, matching the recorded decision.
- **Fix cost:** S

**S3-002 — minor — risk (performance) — file:line:** `core/network/.../GeminiWorkoutPlannerEngine.kt:178-183`
- **Why it matters:** `retryDelayMillis` takes the server hint (via `Retry-After` or `RetryInfo.retryDelay`) and then `coerceIn(0L, MAX_BACKOFF_MILLIS)` (8 s). A server asking the client to wait longer is retried ~8 s later, which can keep hitting the rate limit and waste the single retry budget.
- **Recommendation:** Honor the hint up to a larger ceiling (or, if the hint exceeds the cap, stop retrying and throw `RATE_LIMITED`), so the client respects the server's pacing.
- **Fix cost:** S

**S3-003 — minor — duplication/performance — file:line:** `core/network/.../GeminiWorkoutPlannerEngine.kt:57` and `:147`
- **Why it matters:** `generatePlan` loads `catalog.all()` to compute `availableIds`, then `normalizeExerciseIds` loads `catalog.all()` again to build the name index — two full catalog reads per generation, and the second ignores the already-filtered list.
- **Recommendation:** Load the catalog once and pass the resulting exercises (or id/name maps) into `normalizeExerciseIds`.
- **Fix cost:** S

### `:core:llm`

**S3-004 — major — bug (hang) — file:line:** `core/llm/src/androidMain/.../LiteRtLmTextGenerator.kt:157-166`
- **Why it matters:** `stream` waits on `done.await()` with no timeout. If the native callback never fires `onDone`/`onError` (the known LiteRT-LM failure mode behind the earlier hung runs), `generate` blocks forever on a `Dispatchers.Default` thread while holding the `@Synchronized` monitor, so every later generation queues behind it; the heartbeat executor is only shut down in the `finally`, which never runs until `await` returns. `LocalLlmWorkoutPlannerEngine` cannot recover because its `catch` never sees an exception.
- **Recommendation:** Bound the wait (`done.await(timeoutMillis, MILLISECONDS)`) and treat a timeout as a failure — set a cause, close the conversation, shut down the heartbeat, and let the engine fall back. Size the timeout from `MAX_OUTPUT_TOKENS` at the slowest expected throughput.
- **Fix cost:** S

**S3-005 — minor — consistency (privacy) — file:line:** `core/llm/src/androidMain/.../LiteRtLmTextGenerator.kt:50,54`
- **Why it matters:** The full prompt and output are logged at debug, and the prompt includes the shared recent/progressed weight history when the "Share workout data" toggle is on. On-device data is user training history, and the Gemini API key is never in it, but logging whole payloads is inconsistent with the "no payload logging" stance and unnecessary in release.
- **Recommendation:** Log lengths and progress counters only, or gate the payload behind a debug-only flag.
- **Fix cost:** S

**S3-006 — minor — consistency — file:line:** `core/llm/src/androidMain/.../LiteRtLmTextGenerator.kt:205`
- **Why it matters:** `Engine.setNativeMinLogSeverity(LogSeverity.VERBOSE)` globally raises native logging on every engine creation, including release builds; the native runtime can then spill verbose model/tensor logs at runtime.
- **Recommendation:** Set `VERBOSE` only under a debug build flag; keep the release default.
- **Fix cost:** S

**S3-007 — minor — risk (data) — file:line:** `core/llm/src/androidMain/.../AndroidOnDeviceModelManager.kt:104-108`
- **Why it matters:** `replaceModelWith` first tries `renameTo`, and on failure deletes the existing `modelFile` before the second `renameTo`. If that second rename also fails, the working model is gone and the import throws — the exact loss the temp-file copy was meant to avoid. Renames within the same directory rarely fail, but the fallback path is the one designed to be safe.
- **Recommendation:** Rename the old model aside (or copy the temp over) so a failed replace can be rolled back; only delete the old file after the new one is confirmed.
- **Fix cost:** M

### Seed-observation status (S3-resident)

- Error handling and logging consistency (fallback classification): **confirmed** — `OnDevicePlannerFallback` is split correctly and the Android logger uses warn/error accordingly (`AndroidOnDevicePlannerLogger.kt:6-19`); the Gemini silent fallback is S3-001 and the payload logging is S3-005/S3-006.
- Coroutine scope and dispatcher handling: **mostly confirmed** — the local engine correctly runs blocking generation on `Dispatchers.Default` and rethrows cancellation (`LocalLlmWorkoutPlannerEngine.kt:62,70-71`); the gap is the unbounded native wait (S3-004).
- expect/actual boundaries: **refuted for S3** — neither module uses `expect`/`actual`; platform selection is source-set classes (`UnsupportedOnDeviceTextGenerator`) plus Koin bindings.
- Test quality/flakiness (on-device path): **outside S3** — test slice TS3; S3-004 is the code-side explanation for the reported hang.
- Stale `PlannerPromptFragments.volumeRepsGuidance`: **confirmed as consumed here** — `GeminiWorkoutPlannerEngine.kt:250` and `LocalLlmWorkoutPlannerEngine.kt:173`; root cause is S1-012.
- `PlanVarietyEnforcer` vs on-device output: **confirmed and handled as designed** — a rejected local week is re-prompted once with a correction and then fell back with a compact rejection summary (`LocalLlmWorkoutPlannerEngine.kt:94,108-119`); the S1-013 repair-vs-reject question stands.
- 0.2.4 async `sendMessageAsync` + `ResponseFormat.json` count enforcement: **not decided here** — the schema uses per-day `anyOf` (`LocalLlmWorkoutPlannerEngine.kt:266-283`); whether `minItems`/`maxItems` survive that on-device is the recorded 0.2.4 device check, not a finding.

### S3 finding summary

- blocker: 0
- major: 2 (S3-001, S3-004)
- minor: 5 (S3-002, S3-003, S3-005, S3-006, S3-007)
- nit: 0
- total: 7

## S4 — `:feature:logger` + `:feature:equipment` (main sources)

- **Slice:** S4 — main sources of `:feature:logger` (1268) and `:feature:equipment` (1258).
- **Pinned commit:** `7e04245` (tag `v0.2.1`).
- **Date:** 2026-10-03.
- **Scope:** main sources only. Test tree read only via grep for coverage. No fixes.

**Coverage (files read):**

- `:feature:logger`: `WorkoutLoggerModule.kt`, `BackdatedTime.kt`, `WorkoutLoggerUiState.kt`, `WorkoutLoggerViewModel.kt`, `WorkoutLoggerScreen.kt`
- `:feature:equipment`: `EquipmentFeatureModule.kt`, `EquipmentNavigation.kt`, `EquipmentProfilerUiState.kt`, `EquipmentProfilerViewModel.kt`, `EquipmentProfilerScreen.kt`

**Files read with no findings:** `WorkoutLoggerModule.kt`, `BackdatedTime.kt`, `WorkoutLoggerUiState.kt`, `EquipmentFeatureModule.kt`, `EquipmentNavigation.kt`, `EquipmentProfilerUiState.kt`.

### `:feature:logger`

**S4-001 — major — bug — file:line:** `feature/logger/.../WorkoutLoggerViewModel.kt:331-361` (rebuild at `:346`), reached from `:319-321`
- **Why it matters:** `updateTodayPlan` unconditionally re-derives `draftSets` from the accepted plan, and `onResume()` calls it on every `ON_RESUME` (`WorkoutLoggerScreen.kt:115`). So after the user confirms (or dismisses) a draft, backgrounding and returning rebuilds the full draft list from the plan: the confirmed draft reappears and can be confirmed again, logging duplicate sets. There is no persistence of "already handled today", and the existing tests only assert the list right after `confirmDraft`/`dismissDraft` (`WorkoutLoggerViewModelTest.kt:329-362`), so the resurrection is untested.
- **Recommendation:** Only rebuild drafts when the plan/day actually changes (compare plan id + local day before resetting), or track handled drafts for the day. Add a test: `confirmDraft(...)` then `onResume()` leaves `draftSets` empty.
- **Fix cost:** M

**S4-002 — minor — bug/consistency — file:line:** `feature/logger/.../WorkoutLoggerScreen.kt:386` and `:430`
- **Why it matters:** The draft and recent-set rows build user-visible strings with hardcoded separators/digits (`"${draft.name}  ${draft.sets} x ${draft.reps} · ~$weight"`, `"... ${row.reps} x $weight$warmupSuffix$weekDay"`). The localized pieces (`weight`, `warmupSuffix`, `weekDay`) are used, but `"x"`, `"·"`, `"~"`, and the spacing are literals in a Composable, which the project's localization rule forbids.
- **Recommendation:** Put the row formats in resource strings with positional placeholders (the weight unit is already a parameter) so translators control the layout.
- **Fix cost:** S

### `:feature:equipment`

**S4-003 — minor — bug/consistency — file:line:** `feature/equipment/.../EquipmentProfilerScreen.kt:249`
- **Why it matters:** Personal records are rendered as `"${formatWeight(record.weightKg)} kg × ${record.reps}"` — always kg, with a hardcoded unit — even though the user's `WeightUnit` preference (used by the Logger) can be LB. A user who set pounds sees their PRs in kilograms on this screen. `EquipmentProfilerViewModel` has no `WeightUnitRepository` dependency, so it cannot format per preference.
- **Recommendation:** Inject `WeightUnitRepository` (or expose the unit in `EquipmentProfilerUiState`) and format via `kilogramsToDisplay` + the unit label, matching the Logger. Note this raises the ViewModel to 7 constructor dependencies — group the read-only preference repositories (or add a small `UnitPreference` collaborator) rather than just appending another `get()`.
- **Fix cost:** M

**S4-004 — minor — bug (data loss) — file:line:** `feature/equipment/.../EquipmentProfilerViewModel.kt:190-202` (rename), `:145-152` (add), `:95-105` (PR save)
- **Why it matters:** Renaming a custom equipment item is implemented as `remove(tag)` then `add(name)` and `setMaxWeight(...)`, with no transaction. If `add` throws (id collision, per S2-005) or the process dies between the two calls, the equipment — and its selection — is lost. More generally, `onAddEquipment`, `onSaveEquipmentRenamed`, and `onSavePersonalRecord` run repository calls in `viewModelScope` with no `try/catch`, so a persistence failure crashes the app, unlike the custom-exercise paths that catch `CustomExerciseException`.
- **Recommendation:** Add an `update`/rename operation to `EquipmentRepository` (or wrap remove+add in a transaction) and surface DB failures into `equipmentEditor.error` instead of leaving them uncaught.
- **Fix cost:** M

### Nits

**S4-005 — nit — dead parameter — file:line:** `feature/equipment/.../EquipmentProfilerViewModel.kt:379-384`
- **Why it matters:** `closeEditorAndRefresh(highlightId)` accepts `highlightId` and never uses it; `persistNew` passes the new id (`:332`) expecting the list to highlight it. The intent is unimplemented.
- **Recommendation:** Implement the highlight or drop the parameter.
- **Fix cost:** S

**S4-006 — nit — consistency — file:line:** `feature/logger/.../WorkoutLoggerScreen.kt:374` and `:404`
- **Why it matters:** `items(state.draftSets)` and `items(state.recentSets)` supply no `key`, so Compose reuses item state positionally when drafts/sets are removed. `LoggedSetRow` has a stable `id` for a key.
- **Recommendation:** Pass `key = { it.id }` for `recentSets` (and a stable key for drafts if one is added).
- **Fix cost:** S

### Seed-observation status (S4-resident)

- Logger ViewModel constructor size: **refuted** — the seed's "7 constructor params" is now **6** (`WorkoutLoggerViewModel.kt:27-34`), at the project's guideline boundary, after `WorkoutLogMutations` grouped the mutating use cases. No finding on size; the class is large (458 lines) but the logic is state mapping plus delegation.
- Coroutine scope and dispatcher handling: **mostly confirmed** — all work runs in `viewModelScope` on the Main dispatcher through `suspend` use cases; the gaps are the uncaught persistence calls (S4-004) and the resume-driven draft reset (S4-001).
- Error handling and logging consistency: **confirmed as inconsistent** — custom-exercise paths catch `CustomExerciseException`, but equipment/PR persistence paths do not (S4-004).
- Feature registration and cross-feature isolation: **confirmed** — each module exports its Koin module + `FeatureDestination`, and neither feature imports the other or `core:database`/`core:network`; both depend only on `core:domain`/`core:userdata`.
- Empty-involvements override path (S2-001): **confirmed reachable from here** — `onEditorMuscleInvolvementChanged(..., null)` can clear every muscle, and `onSaveExercise` sends the resulting empty map to `writeBuiltInOverrides` (`:347`); root cause is S2-001.

### S4 finding summary

- blocker: 0
- major: 1 (S4-001)
- minor: 3 (S4-002, S4-003, S4-004)
- nit: 2 (S4-005, S4-006)
- total: 6

## S5 — `:feature:splitbuilder` + `:feature:settings` + `:feature:fatigueheatmap` (main sources)

- **Slice:** S5 — main sources of `:feature:splitbuilder` (641), `:feature:settings` (592, incl. platform sets), `:feature:fatigueheatmap` (210).
- **Pinned commit:** `7e04245` (tag `v0.2.1`).
- **Date:** 2026-10-03.
- **Scope:** main sources only. Test tree read only via grep for coverage. No fixes.

**Coverage (files read):**

- `:feature:fatigueheatmap`: `FatigueHeatmapModule.kt`, `FatigueHeatmapUiState.kt`, `FatigueHeatmapViewModel.kt`, `FatigueHeatmapScreen.kt`
- `:feature:splitbuilder`: `SplitBuilderModule.kt`, `SplitBuilderUiState.kt`, `SplitBuilderViewModel.kt`, `SplitBuilderScreen.kt`
- `:feature:settings`: `SettingsModule.kt`, `OnDeviceModelSection.kt` (expect), `SettingsUiState.kt`, `SettingsViewModel.kt`, `SettingsScreen.kt`, `OnDeviceModelSection.android.kt`, `OnDeviceModelSection.ios.kt`

**Files read with no findings:** `FatigueHeatmapModule.kt`, `FatigueHeatmapUiState.kt`, `FatigueHeatmapScreen.kt`, `SplitBuilderModule.kt`, `SplitBuilderUiState.kt`, `SettingsModule.kt`, `OnDeviceModelSection.kt`, `SettingsUiState.kt`, `SettingsScreen.kt`, `OnDeviceModelSection.android.kt`, `OnDeviceModelSection.ios.kt`.

### `:feature:splitbuilder`

**S5-001 — minor — bug (invariant) — file:line:** `feature/splitbuilder/.../SplitBuilderViewModel.kt:118-127` (merge at `:127`)
- **Why it matters:** `showAccepted` resolves display names as `snapshotNames + catalogNames`. Kotlin's `Map.plus` lets the right operand overwrite on key collision, so the **live catalog name wins** over the name snapshotted into the accepted plan. That inverts the recorded invariant (PLANS.md line 302: an accepted entry snapshots the exercise name "so later catalog edits cannot rewrite history") — renaming an exercise after accepting a plan changes how the accepted plan and history render.
- **Recommendation:** Make the snapshot authoritative (`catalogNames + snapshotNames`, or only fall back to the catalog for ids absent from the snapshot) so acceptance-time names are stable.
- **Fix cost:** S

**S5-002 — minor — bug/consistency — file:line:** `feature/splitbuilder/.../SplitBuilderViewModel.kt:215-244` (`catalogSignature` at `:219-229`)
- **Why it matters:** The regenerate fingerprint folds in the catalog via `primaryMuscles`/`secondaryMuscles` — the ≥0.7 tags — but not the underlying involvement **weights** or `isUnilateral`. The deterministic engine selects on `effectiveInvolvements` (the weights), so an edit that changes a weight without crossing 0.7 (e.g. 0.4 → 0.5, or 1.0 → 0.7) produces an identical fingerprint: `canRegenerate` stays `false` and Regenerate is disabled even though the plan would change. The comment claims catalog edits re-enable Regenerate.
- **Recommendation:** Include each exercise's sorted `involvements` entries (and `isUnilateral`) in `catalogSignature`.
- **Fix cost:** S

**S5-003 — minor — i18n — file:line:** `feature/splitbuilder/.../SplitBuilderScreen.kt:274`, `:280`, `:286`
- **Why it matters:** The plan rows build user-visible strings with hardcoded literals in a Composable: `" - "` joined to the day heading, `"  ·  "`, and `"$name  ${exercise.sets} x ${exercise.reps}$weight"`. The project's localization rule forbids user-facing strings outside `commonMain` resources (same class as S4-002).
- **Recommendation:** Move the day-heading and exercise-row formats into string resources with placeholders (weight already uses a resource).
- **Fix cost:** S

### `:feature:fatigueheatmap`

**S5-004 — minor — risk (concurrency/perf) — file:line:** `feature/fatigueheatmap/.../FatigueHeatmapViewModel.kt:57-64`
- **Why it matters:** `refresh()` calls `calculateMuscleFatigue(sets, now)` directly from `viewModelScope` (Main) on every `loggedSetsFlow()` emission and every 60 s ticker fire (`:46-47`). The computation is O(muscles × sets) with decaying math; with a large log it runs on the UI thread and can jank, and the ticker guarantees it keeps happening while the tab is visible. This is the code-side counterpart to the recorded heatmap-ticker test flakiness.
- **Recommendation:** Run the calculation on `Dispatchers.Default` (e.g. `withContext`) before updating state; keep the ticker as is.
- **Fix cost:** S

### Nits

**S5-005 — nit — dead parameters — file:line:** `feature/settings/.../OnDeviceModelSection.ios.kt:15-19`
- **Why it matters:** The iOS `actual` never reads `installed` or calls `onModelChanged`; they are required by the `expect`, so every other platform signature change ripples into a stub that cannot honor it.
- **Recommendation:** Acceptable as a documented stub; add a brief comment that the parameters are intentionally unused until iOS model management ships.
- **Fix cost:** S

### Seed-observation status (S5-resident)

- expect/actual boundaries (settings on-device section): **confirmed** — `OnDeviceModelSection` is an `expect` Composable with Android (`...android.kt`) and iOS (`...ios.kt`) actuals; this is the only `expect`/`actual` in the app, and it is used correctly (Android does import/remove on `Dispatchers.IO`, iOS is a note).
- Coroutine scope and dispatcher handling: **mixed** — model IO is correctly off-main (`OnDeviceModelSection.android.kt:65,91`), while fatigue recomputation and API-key Keystore work run on Main (S5-004, and S2-007 reached here).
- Error handling and logging consistency: **minor** — `SettingsViewModel` and `SplitBuilderViewModel` update state on the known `PlanGenerationException`/`CustomExerciseException` paths, but persistence calls (`setEngine`, `setGoal`, `apiKeyStore.save`) have no `try/catch` (same shape as S4-004).
- Test quality and flakiness (heatmap ticker): **outside S5** — test slice TS4; S5-004 is the production-side explanation for the recorded ticker hang/fragility.
- Sync `ApiKeyStore`/`OnDeviceModelManager` reached from Settings (S2-007): **confirmed** — `SettingsViewModel.saveApiKey`/`clearApiKey` call the store directly on the caller thread (`:69,75`), outside `viewModelScope`, and `refresh()` calls `load()` from a Main `viewModelScope` (`:93`); root cause is S2-007.

### S5 finding summary

- blocker: 0
- major: 0
- minor: 4 (S5-001, S5-002, S5-003, S5-004)
- nit: 1 (S5-005)
- total: 5

## S6 — `:shared` + `:androidApp` + iOS Swift + build/Gradle/CI config (main sources)

- **Slice:** S6 — `:shared` (388), `:androidApp` (Kotlin 35 + manifest/resources + gradle), iOS Swift (26), build/Gradle config (`*.kts` + version catalog), and `.github/workflows` (297).
- **Pinned commit:** `7e04245` (tag `v0.2.1`).
- **Date:** 2026-10-03.
- **Scope:** read-only. Test tree not read. Findings are documentation only; any fix here would be a Major Infrastructure Change requiring separate approval.

**Coverage (files read):**

- `:shared`: `Koin.kt`, `App.kt`, `DomainModule.kt`, `DefaultWorkoutPlannerEngineProvider.kt`, `DefaultEngineAvailability.kt`, `AndroidDatabaseModule.kt`, `DelegatingOnDeviceModelManager.kt`, `IosDatabaseModule.kt`, `MainViewController.kt`
- `:androidApp`: `HydraFitApplication.kt`, `MainActivity.kt`, `AndroidManifest.xml`, `build.gradle.kts`, `proguard-rules.pro`
- iOS Swift: `iOSApp.swift`, `ContentView.swift`
- Build/Gradle: `settings.gradle.kts`, root `build.gradle.kts`, `gradle/libs.versions.toml`, `androidApp/build.gradle.kts`, `shared/build.gradle.kts`, and every `core/*` + `feature/*` `build.gradle.kts`; `.gitignore`; `local.properties.template`
- CI: `build-and-test.yml`, `nightly.yml`, `release.yml`

**Files read with no findings:** `App.kt`, `DefaultWorkoutPlannerEngineProvider.kt`, `DefaultEngineAvailability.kt`, `AndroidDatabaseModule.kt`, `DelegatingOnDeviceModelManager.kt`, `IosDatabaseModule.kt`, `MainViewController.kt`, `HydraFitApplication.kt`, `MainActivity.kt`, `iOSApp.swift`, `ContentView.swift`, `settings.gradle.kts`, root `build.gradle.kts`, all module `build.gradle.kts`, `.gitignore`, `local.properties.template`, `release.yml`, `nightly.yml`.

### `:shared` (composition root)

**S6-001 — minor — design smell / DI — file:line:** `shared/.../DomainModule.kt:37`, `:40-49`, `:61-62`, `:66`
- **Why it matters:** `SuggestedWeightConfig()` and `PeriodizationConfig()` are registered as singletons, and `ObserveWorkoutPlanInputsUseCase`/`SuggestWeightsUseCase` receive them. But `DeterministicWorkoutPlannerEngine(get())` and `WeeklyPlanSanitizer(get())` inject only the catalog and fall back to their own constructor-default configs (`DeterministicWorkoutPlannerEngine.kt:12-14`, `WeeklyPlanSanitizer.kt:13-15`). There are therefore two live instances of each config, and a DI override of the bound single would change the plan inputs but not the engine/sanitizer that consume them — a silent divergence waiting for the first non-default config.
- **Recommendation:** Pass the bound configs into the engine and sanitizer (e.g. `DeterministicWorkoutPlannerEngine(get(), get(), get(), get())`) or drop the unused singles; keep exactly one definition per config.
- **Fix cost:** S

**S6-002 — minor — risk (startup/perf) — file:line:** `shared/.../Koin.kt:31-33`, reached from `androidApp/.../HydraFitApplication.kt:8`
- **Why it matters:** `initKoin` synchronously runs `SeedExerciseCatalog.seed()`, `SeedEquipmentCatalog.seed()`, and `WorkoutSessionBackfill.backfill()`; `HydraFitApplication.onCreate` calls it on the main thread, so the first frame waits on several DB transactions (and, on legacy data, the whole session backfill). PLANS 0.2.3 names this as a cold-start target.
- **Recommendation:** Move seeding/backfill off the main thread (or defer behind a startup `Dispatchers.Default` job) and measure in 0.2.3; keep the calls idempotent as they are now.
- **Fix cost:** S

### `:androidApp`

**S6-003 — minor — risk (security/privacy) — file:line:** `androidApp/src/main/AndroidManifest.xml:8`
- **Why it matters:** `android:allowBackup="true"` with no `fullBackupContent`/`dataExtractionRules` means the workout database (and `hydrafit_secure` prefs holding the encrypted API-key ciphertext) are eligible for cloud/device backup. The Keystore key is non-exportable, so a restored ciphertext simply fails to decrypt, but the workout history is user data being copied off-device without an explicit decision.
- **Recommendation:** Decide deliberately — set `allowBackup="false"`, or add `dataExtractionRules` excluding the secure prefs (and optionally the DB). Small manifest change, but call it out as a change to shipped behavior.
- **Fix cost:** S

**S6-004 — minor — risk (release build) — file:line:** `androidApp/build.gradle.kts:95`
- **Why it matters:** `release { isMinifyEnabled = false }` ships unshrunk, unobfuscated code, and `proguard-rules.pro` is inert. PLANS 0.2.3 lists APK size and R8 as a measurement area, so this is the expected state now, but it should not be forgotten: enabling R8 later needs keep rules for LiteRT-LM, kotlinx-serialization, and Koin.
- **Recommendation:** Leave for 0.2.3; add the R8/keep-rule evaluation to that phase's task list.
- **Fix cost:** M (when done)

### Build config / CI

**S6-005 — minor — consistency — file:line:** `.github/workflows/build-and-test.yml:87-91` (vs `.github/workflows/nightly.yml:87-92`)
- **Why it matters:** AGENTS.md documents the debug APK artifact with **14-day retention**, and `nightly.yml` sets `retention-days: 14`, but the primary `build-and-test.yml` upload omits it, so the default (90 days) applies. The two workflows now differ on a documented property.
- **Recommendation:** Add `retention-days: 14` to the `assemble-debug-apk` upload in `build-and-test.yml` (or update AGENTS.md if 90 days is intended).
- **Fix cost:** S

**S6-006 — minor — dependency hygiene — file:line:** `gradle/libs.versions.toml:28-33`
- **Why it matters:** `junit`, `kotlin-testJunit`, `androidx-core-ktx`, `androidx-testExt-junit`, `androidx-espresso-core`, and `androidx-appcompat` have **zero** references in any `*.kts` (the app uses the framework `Theme.Material.Light.NoActionBar`, not AppCompat). They are KMP-wizard leftovers. (`mockk` is also unused but is intentionally staged — PLANS.md line 251 — and should stay.)
- **Recommendation:** Remove the six unused catalog entries; remove `mockk` only when the decision to not use it lands.
- **Fix cost:** S

**S6-007 — minor — risk (dependency) — file:line:** `gradle/libs.versions.toml:22`
- **Why it matters:** `material3 = "1.12.0-alpha03"` pins an alpha for a core, app-wide UI dependency. The dependency-hygiene rule asks that alpha/pre-release choices be deliberate and revisited.
- **Recommendation:** Track a move to a stable `material3` release; if the alpha is required, note why (e.g. a component only in alpha) so the next dependency review does not re-litigate it.
- **Fix cost:** S

### Nits

**S6-008 — nit — duplication — file:line:** `.github/workflows/*.yml` (lint/unit-tests jobs repeated in all three)
- **Why it matters:** The same `lint` and `unit-tests` job bodies are copy-pasted into `build-and-test.yml`, `nightly.yml`, and `release.yml`; a change (e.g. Java version, Gradle action version) must be applied three times. PLANS intentionally mirrors the pipeline, but the drift risk is real.
- **Recommendation:** Extract the shared jobs into a reusable workflow (`workflow_call`) and call it from all three, keeping the stage graph in each caller.
- **Fix cost:** M

### Seed-observation status (S6-resident)

- Use-case/Koin wiring placement (composition root): **confirmed** — all use cases and both `WorkoutPlannerEngine` implementations are bound in `:shared`'s `domainModule`/`networkModule`; `:core:domain` exposes no Koin module. The one wart is the duplicate config instances (S6-001).
- expect/actual boundaries: **confirmed** — the only `expect`/`actual` is `OnDeviceModelSection` in `:feature:settings`; platform selection elsewhere is via Koin platform modules (`AndroidDatabaseModule`/`IosDatabaseModule`), and `MainViewController` is the iOS entry point.
- Feature registration as explicit static aggregation: **confirmed** — `App.kt:34-40` and `Koin.kt:18-28` list every feature export explicitly; no feature imports another (module graph is `feature -> core`), and `:shared` holds only the composition root.
- Dependency pins: **confirmed** — `libs.versions.toml` matches the PLANS.md "Decisions Made" pins (SQLDelight 2.4.0, Koin 4.2.2, Ktor 3.6.0, serialization 1.11.0, coroutines 1.11.0, MockK 1.14.11, navigation-compose 2.9.2); the gaps are the unused entries (S6-006) and the alpha material3 pin (S6-007).
- CI pipeline correctness: **confirmed with S6-005** — the stage graph matches AGENTS.md (lint/unit-tests/ios-compile immediately; assemble after lint+unit-tests; release gated on lint+unit-tests with the four secrets verified and the keystore deleted `always()`); no committed secrets. The missing retention setting is the only mismatch found.

### S6 finding summary

- blocker: 0
- major: 0
- minor: 7 (S6-001, S6-002, S6-003, S6-004, S6-005, S6-006, S6-007)
- nit: 1 (S6-008)
- total: 8

## TS2 — `:core:domain` tests (rule-branch gaps), sampled

- **Slice:** TS2 — `:core:domain` `commonTest` (5,049 Kotlin lines, 34 files). Sampled, not line-by-line.
- **Pinned commit:** `7e04245` (tag `v0.2.1`).
- **Date:** 2026-10-03.
- **Scope:** read-only. Findings only with evidence; test gaps are in scope, style is not.

**Coverage:** `DeterministicWorkoutPlannerEngineTest.kt` (942) read in full. For the other 33 files I enumerated every `@Test`/function name and read the regions relevant to each rule branch (session boundaries, thresholds, truncation, config validation). Test-name inventory used for the coverage judgement. **Read with no rule-branch gap found:** `FatigueCalculatorTest.kt` (603), `FatigueReplayTest.kt`/`FatigueReplayFixture.kt` (214), `ObserveWorkoutPlanInputsUseCaseTest.kt` (601), `LogWorkoutSetUseCaseTest.kt` (399), `WeeklyPlanSanitizerTest.kt` (238), `PlanVarietyEnforcerTest.kt` (124), `WeeklyPlanJsonTest.kt` (128), `ProgressWeightsUseCaseTest.kt` (178), `BuildRecentWeightsUseCaseTest.kt` (128), `AcceptWeeklyPlanUseCaseTest.kt` (134), `SuggestWeightsUseCaseTest.kt`, `SuggestedWeightConfigTest.kt`, `WeeklyVolumeTargetsTest.kt`, `PeriodizationConfigTest.kt`, `GenerateWeeklySplitUseCaseTest.kt`, `EquipmentWeightLimitTest.kt`, `SplitResolverTest.kt`, the `equipment/time/unit` tests, and the simple session/log use-case tests.

### Findings

**TS2-001 — major — test gap — file:line:** `core/domain/src/commonTest/.../engine/DeterministicWorkoutPlannerEngineTest.kt:343-356` and `:398-421`
- **Why it matters:** The fatigue-skip tests only ever pass **one** candidate per movement group. The engine's actual skip path (`DeterministicWorkoutPlannerEngine.pick`, S1-008) returns `false` for the whole group when the single best-ranked candidate is over `skipThreshold`, so a fresh second candidate in the same group is silently dropped — exactly the bug S1-008 describes. No test exercises two same-pattern candidates where the top-ranked is sore and the other is fresh, so the regression is invisible.
- **Recommendation:** Add a test with two exercises in one `compoundGroups` family (e.g. two HORIZONTAL_PUSH) where the comparator-leading one has a targeted muscle above 0.8 and the other is fresh; assert the fresh one is picked. This is the regression test S1-008 needs.
- **Fix cost:** S

**TS2-002 — minor — test gap — file:line:** (no test file) `core/domain/.../engine/WeeklyPlan.kt:11-19`, `AcceptedPlan.kt:19-27`
- **Why it matters:** `WeeklyPlan.scheduledDay`/`dayFor` and the identical `AcceptedPlan` methods have **zero** tests anywhere (`rg scheduledDay|dayFor` finds only main sources and the Logger consumer). That formula decides which accepted day is "today" for the Logger and progression day labeling, and it is the duplicated logic flagged in S1-011. Its boundaries (`days.size` 2..6, an out-of-range `dayIndex`, a `dayIndex` sequence that is not 0-based) are unverified.
- **Recommendation:** Add a `WeeklyPlanTest`/`AcceptedPlanTest` covering day counts 2..6, each `dayIndex`, and the invalid-`dayIndex` null case; this also pins the shared formula.
- **Fix cost:** S

**TS2-003 — minor — test gap — file:line:** `core/domain/src/commonTest/.../unit/WeightUnitTest.kt:1-32`
- **Why it matters:** `WeightUnitTest` covers kg/lb round-trips, the plate step, and whole-number formatting, but not the `formatWeight` edge flagged in S1-002 (`-0.0`, very small/large values that render in scientific notation). The boundary is unguarded by a test.
- **Recommendation:** Add cases for `-0.0`, a value that rounds to a whole number, and (if deemed in-scope) a tiny/large magnitude; assert the intended string.
- **Fix cost:** S

**TS2-004 — minor — test gap — file:line:** `core/domain/src/commonMain/.../equipment/Exercise.kt:26-29`
- **Why it matters:** `Exercise.effectiveInvolvements` (explicit map, else legacy primary 1.0 + secondary 0.5) is used by the deterministic engine, sanitizer, and volume targets, but no `:core:domain` test reads it directly (`rg effectiveInvolvements core/domain/src/commonTest` → none). The legacy fallback and the empty-both case are only exercised indirectly through catalog tests in other modules.
- **Recommendation:** Add a small `ExerciseTest` asserting explicit-with-non-empty wins, the primary/secondary fallback, and empty→empty.
- **Fix cost:** S

**TS2-005 — minor — test gap/consistency — file:line:** `core/domain/src/commonTest/.../engine/PlannerPromptFragmentsTest.kt:76-82`
- **Why it matters:** `derivesVolumeGuidanceFromTheGoal` asserts the prompt text verbatim, including the stale "fewer sets mean more reps per set" wording (S1-012). The test therefore pins the misleading behavior: any fix to S1-012 must change this assertion, and until then the test actively protects the stale text.
- **Recommendation:** When S1-012 is triaged, update this assertion to the Option C wording in the same change; note the coupling so the fix is not blocked or forgotten.
- **Fix cost:** S

**TS2-006 — nit — duplication — file:line:** `core/domain/src/commonTest/.../workout/GetWorkoutLogUseCaseTest.kt:27`, `LogWorkoutSetUseCaseTest.kt:347`, `CorrectWorkoutSetTimeUseCaseTest.kt:21`, `DeleteWorkoutSetUseCaseTest.kt:21`, `engine/ObserveWorkoutPlanInputsUseCaseTest.kt:576,587`, `AcceptWeeklyPlanUseCaseTest.kt:95`
- **Why it matters:** Within `:core:domain` alone there are two `FakeWorkoutLogRepository` classes, two `RecordingWorkoutLogRepository` classes, and two `FakePlanHistoryRepository` classes. Each re-implements the same interface, so any interface change ripples through all of them.
- **Recommendation:** Consolidate the domain-internal fakes into one shared test helper — coordinate with the TS4 `testFixtures` decision rather than fixing it here.
- **Fix cost:** M

### Seed-observation status (TS2-resident)

- Fakes/fixtures and shared-fixture opportunities: **noted, deferred to TS4** — the domain tests reuse `FatigueReplayFixture` (good) but duplicate repository fakes among themselves (TS2-006); `WorkoutLogRepository` fakes appear in `:core:domain`, `:core:database`, and the feature tests, and TS4 owns the consolidation count.
- Flaky patterns: **none found in TS2** — no real-clock/`System.*`/`sleep`/`delay` usage in `:core:domain` tests; every test drives explicit millisecond values and `runTest` virtual time.
- Test gaps against rule branches: **found** — TS2-001 (skip fall-through), TS2-002 (schedule formula), TS2-003 (`formatWeight` edge), TS2-004 (`effectiveInvolvements` fallback), TS2-005 (stale prompt wording pinned). The fatigue calculator, sanitizer/enforcer, JSON truncation recovery, session use cases, and config validation are well covered.

### TS2 finding summary

- blocker: 0
- major: 1 (TS2-001)
- minor: 4 (TS2-002, TS2-003, TS2-004, TS2-005)
- nit: 1 (TS2-006)
- total: 6

## TS3 — `:core:database` + `:core:userdata` + `:core:network` + `:core:llm` tests, sampled

- **Slice:** TS3 — `:core:database` `androidHostTest` (2,623), `:core:userdata` `commonTest` (25), `:core:network` `androidHostTest` (581), `:core:llm` `commonTest` (723). Sampled.
- **Pinned commit:** `7e04245` (tag `v0.2.1`).
- **Date:** 2026-10-03.
- **Scope:** read-only. Findings only with evidence; test gaps are in scope.

**Coverage:** read in full — all 10 migration tests (`SessionIdMigrationTest`, `InvolvementsMigrationTest`, `InvolvementConversionMigrationTest`, `ExerciseOverrideMigrationTest`, `WorkoutSetWeekDayMigrationTest`, `EquipmentMaxWeightMigrationTest`, `PlanHistoryMigrationTest`, `RirMigrationTest`, `ExerciseUnilateralMigrationTest`, `PersonalRecordMigrationTest`) and `SqlDelightWorkoutLogRepositoryTest.kt` (469); targeted reads in `GeminiWorkoutPlannerEngineTest.kt` (fallback and retry regions) and `SqlDelightExerciseOverrideRepositoryTest.kt`/`SqlDelightExerciseCatalogTest.kt` (override paths). All 25 TS3 test files inventoried by test name. **Read/inventoried with no rule-branch gap found:** `WorkoutSessionBackfillTest.kt` (17 tests incl. rollback and idempotency), `LocalLlmWorkoutPlannerEngineTest.kt` (30 tests incl. OOM fallback, retry, progress clear), `SqlDelightCustomExerciseRepositoryTest.kt`, `SqlDelightPlanHistoryRepositoryTest.kt`, `SeedExerciseCatalogTest.kt`, `SqlDelightEnginePreferenceRepositoryTest.kt`, `SqlDelightWorkoutPlanSourcesRepositoryTest.kt`, `SqlDelightTrainingGoalRepositoryTest.kt`, `SqlDelightWeightUnitRepositoryTest.kt`, `SqlDelightWorkoutSessionRepositoryTest.kt`, `SqlDelightEquipmentSelectionRepositoryTest.kt`, `SqlDelightPersonalRecordRepositoryTest.kt`, `DatabaseModuleVerificationTest.kt`, `NetworkModuleVerificationTest.kt`, `OnDeviceSamplerTest.kt`, `OnDeviceModelTargetClassifierTest.kt`, `NpuDeviceDetectorTest.kt`. **Skipped (not read):** the retry-backoff internals of `GeminiWorkoutPlannerEngineTest` beyond the region read and the `LocalLlmWorkoutPlannerEngineTest` prompt assertions (sampled by name only).

### Migrations

**TS3-001 — minor — test gap — file:line:** `core/database/src/androidHostTest/.../*MigrationTest.kt` (earliest fixture `v13Database()` in `ExerciseOverrideMigrationTest.kt:75`)
- **Why it matters:** Every migration test builds a hand-written partial "vN shape" and starts at **v13 or later** (`v13`, `v15`, `v16`, `v17`, `v18`, `v19`, `v21`, `v22`, `v23`, `v24`). Migrations `1.sqm..12.sqm` (initial `workoutSet`, `selected_equipment`, `plannerEngine`, `movementPattern`/`daysPerWeek`, `trainingGoal`, `planHistory`, `suggestedWeightKg`, `equipment`, `exerciseEdit`, `shareWorkoutData`, `exerciseMuscleEdit`, `isCustom`) have **no test**, and no single test migrates from the earliest supported version to the current one. Combined with S2-004 (no `verifyMigrations`), a broken early-chain migration or a missing table would not be caught; the recent tests each deliberately scope a partial schema, so the chain is never exercised end-to-end.
- **Recommendation:** Add one test that builds a v1 database and runs `Schema.migrate(driver, 1, Schema.version)`, plus a catalog/`workoutSet` round-trip afterwards; or (preferred, S2-004) enable SQLDelight `verifyMigrations` with a checked-in schema snapshot. Add the missing v1..v12 fixtures incrementally.
- **Fix cost:** M

### Repositories

**TS3-002 — minor — test gap — file:line:** `core/database/src/androidHostTest/.../SqlDelightWorkoutLogRepositoryTest.kt:244-263`
- **Why it matters:** `fallsBackToTheCatalogForLegacyRowsWithoutASnapshot` inserts a null-snapshot legacy row but sets **no** exercise override, so the behavior flagged in S2-003 — that the legacy fallback resolves targets from the seed `exercise.involvements` and ignores `exerciseOverride` — is not exercised. A regression test for S2-003 would set an override first, then assert the fallback uses it.
- **Recommendation:** Extend the fallback test to write an `exerciseOverride` and assert it (once S2-003 is triaged) or explicitly document that legacy rows intentionally use the seed weights.
- **Fix cost:** S

**TS3-003 — minor — test gap — file:line:** `core/database/src/androidHostTest/.../SqlDelightExerciseOverrideRepositoryTest.kt:62-81`, `SqlDelightExerciseCatalogTest.kt:110-134`
- **Why it matters:** The override tests only ever pass a **non-empty** involvements map, so S2-001 (clearing every muscle stores `NULL`, which the catalog then decodes back to the seed weights) is untested. There is no test that an override which clears all muscles produces an empty `effectiveInvolvements`.
- **Recommendation:** Add a catalog test that upserts an override with an empty involvement map and asserts the resolved `involvements` is empty (currently it would fail, pinning S2-001).
- **Fix cost:** S

### `:core:network`

**TS3-004 — major — test gap — file:line:** `core/network/src/androidHostTest/.../GeminiWorkoutPlannerEngineTest.kt:340-351` and `:353-366`
- **Why it matters:** `fallsBackWhenThePlanHasTooFewDays` and `dropsExercisesThatNeedUnavailableEquipmentAndFallsBack` **assert** `PlannerEngineId.DETERMINISTIC` for a successful HTTP response whose body fails sanitization. That is exactly the silent fallback S3-001 flags as contradicting PLANS.md line 291 ("never silently falls back to Deterministic"). The suite does not merely miss the bug — it codifies it, so any fix to S3-001 must rewrite these two tests, and until then CI protects the wrong behavior.
- **Recommendation:** When S3-001 is triaged, change both tests to expect `PlanGenerationException(INVALID_RESPONSE, transient = false)` (or the agreed contract) and add a positive test that a valid-but-rejected model week surfaces the reason instead of a Deterministic plan.
- **Fix cost:** S

**TS3-005 — minor — test gap — file:line:** `core/network/src/androidHostTest/.../GeminiWorkoutPlannerEngineTest.kt:210-281` (RetryInfo only via `geminiError()` at `:469-471`)
- **Why it matters:** Retry behavior is covered only at the call-count/reason level (`retriesTransientFailuresThenSucceeds`, `givesUpAfterMaxRetries…`, `mapsRateLimitToRateLimited…`). `retryDelayMillis` — the `Retry-After` header, the `RetryInfo.retryDelay` body hint, and the `MAX_BACKOFF_MILLIS` clamp behind S3-002 — has no assertion, so a regression in delay parsing/clamping would pass.
- **Recommendation:** Add a test with a `Retry-After: 30` header and with a `RetryInfo.retryDelay` body and assert the observed delay/attempt timing (using `runTest` virtual time), covering the S3-002 clamp.
- **Fix cost:** S

### `:core:llm`

**TS3-006 — minor — test gap — file:line:** `core/llm/src/androidMain/.../LiteRtLmTextGenerator.kt:157-166` (no test file exists)
- **Why it matters:** `LiteRtLmTextGenerator` is `androidMain` over a native runtime and has no test, so the unbounded `done.await()` from S3-004 (native callback never completes → the generation thread blocks while holding `@Synchronized`) has no regression coverage. `LocalLlmWorkoutPlannerEngineTest` covers the engine-level fallbacks but cannot reach the native wait.
- **Recommendation:** Since the wait cannot be unit-tested, add a bounded `await` and cover it with an instrumented/manual check, and record the S3-004 fix as needing a device re-check (like the other on-device paths). Do not leave it as "no test, no plan".
- **Fix cost:** S (test/device), tied to the S3-004 fix

### Nits

**TS3-007 — nit — duplication/coupling — file:line:** `core/database/src/androidHostTest/.../SqlDelightWorkoutLogRepositoryTest.kt:133-135`, `:152`, `:239-240`, `:262`, `:279`, `:298`
- **Why it matters:** The database tests use the production-dead `MuscleInvolvement.PRIMARY/SECONDARY.volumeWeight` enum (S1-003) as their expected constants, so deleting that enum requires editing this test (and the feature/llm tests that use it). Tests should not depend on dead production types.
- **Recommendation:** Replace the enum references with literal weights (or a shared test constant) when S1-003 is actioned.
- **Fix cost:** S

### Seed-observation status (TS3-resident)

- Fakes/fixtures and shared-fixture opportunities: **noted, deferred to TS4** — the database/network tests construct the real SQLDelight repositories against `JdbcSqliteDriver` rather than faking `WorkoutLogRepository`, so the feature-level repository fakes (TS4) are the consolidation target.
- Flaky patterns: **none found** — no real clocks (`historicalPerformedAt…` uses a fixed `now`), no `Thread.sleep`; network retries run on `runTest` virtual time; the native generator is untested (TS3-006) rather than flaky.
- Test gaps against rule branches: **found** — migration chain v1..v12 (TS3-001), legacy-fallback override resolution (TS3-002), empty-involvements override (TS3-003), Gemini fallback contract (TS3-004), retry-delay parsing (TS3-005), native wait (TS3-006). Repository round-trips, backfill, plan history, custom exercises, seed idempotency, and the local-LLM fallbacks are otherwise thorough.

### TS3 finding summary

- blocker: 0
- major: 1 (TS3-004)
- minor: 5 (TS3-001, TS3-002, TS3-003, TS3-005, TS3-006)
- nit: 1 (TS3-007)
- total: 7

## TS4 — feature + `:shared` tests, fixtures/fakes consolidation (sampled)

- **Slice:** TS4 — `:feature:logger` (1,443), `:feature:equipment` (479), `:feature:splitbuilder` (735), `:feature:settings` (278), `:feature:fatigueheatmap` (312), `:shared` (366). Sampled.
- **Pinned commit:** `7e04245` (tag `v0.2.1`).
- **Date:** 2026-10-03.
- **Scope:** read-only. Findings only with evidence; fixtures, flakiness, and VM/state gaps in scope.

**Coverage:** read in full — `FatigueHeatmapViewModelTest.kt` (312), `BackdatedTimeTest.kt` (53), `KoinModulesVerificationTest.kt` (197), `DefaultWorkoutPlannerEngineProviderTest.kt` (83), `DelegatingOnDeviceModelManagerTest.kt` (86). Inventoried all `@Test` names and read the relevant regions of `WorkoutLoggerViewModelTest.kt` (1,390), `SplitBuilderViewModelTest.kt` (735), `EquipmentProfilerViewModelTest.kt` (479), `SettingsViewModelTest.kt` (278); grep-verified the coverage of the specific main-pass findings below. **Read/inventoried with no rule-branch gap found:** the session/backdate/correction/unit/RIR paths in `WorkoutLoggerViewModelTest`, the error/fallback/regenerate-lock paths in `SplitBuilderViewModelTest`, the equipment/PR/override editor paths in `EquipmentProfilerViewModelTest`, and the engine/unit/goal paths in `SettingsViewModelTest`.

### Fixtures and fakes

**TS4-001 — major — duplication/design — file:line:** `feature/fatigueheatmap/.../FatigueHeatmapViewModelTest.kt:271,292`; `feature/logger/.../WorkoutLoggerViewModelTest.kt:1322`; `feature/splitbuilder/.../SplitBuilderViewModelTest.kt:710`; `core/domain/.../LogWorkoutSetUseCaseTest.kt:347`; `core/domain/.../GetWorkoutLogUseCaseTest.kt:27`; `core/domain/.../DeleteWorkoutSetUseCaseTest.kt:21`; `core/domain/.../CorrectWorkoutSetTimeUseCaseTest.kt:21`
- **Why it matters:** Seed confirmed. There are **8** `WorkoutLogRepository` test doubles across **7** files — 5 `FakeWorkoutLogRepository`, 2 `RecordingWorkoutLogRepository`, and 1 `FlowingWorkoutLogRepository` — not 7 across 6 as the seed estimated (the extra is the flow-backed heatmap double). `PlanHistoryRepository` has 4 doubles, `WorkoutSessionRepository` 5, plus repeated `ExerciseCatalog`, `EnginePreferenceRepository`, and `WeightUnitRepository` doubles. Every method added to a port edits all of them by hand, and a subtle behavioral divergence between copies is easy to introduce. This matches the AGENTS seed that a fixture "couldn't be shared between `:core:domain` and `:core:database`".
- **Recommendation:** Introduce one shared test-fixtures artifact for the domain ports — a small KMP `:test:fixtures` (or `:core:testing`) module with `commonMain` fakes (`InMemoryWorkoutLogRepository`, `InMemoryWorkoutSessionRepository`, `InMemoryPlanHistoryRepository`, `InMemoryExerciseCatalog`, …) that keep state and expose the flows, consumed by `:core:domain`, `:core:database`, `:core:network`, `:core:llm`, and every feature test. Start with `WorkoutLogRepository` since it is the most duplicated and most churn-prone. If a KMP `testFixtures` source set is preferred over a module, validate it can be consumed across modules before committing to it (that is the recorded open question).
- **Fix cost:** L

### VM/state gaps (anchored on main-pass findings)

**TS4-002 — major — test gap — file:line:** `feature/logger/.../WorkoutLoggerViewModelTest.kt` (draft tests `:329-387`; resume test `:735-750`)
- **Why it matters:** S4-001 (drafts rebuilt from the accepted plan on every `onResume`, resurrecting confirmed/dismissed drafts) has no regression test: the draft tests only assert immediately after `confirmDraft`/`dismissDraft`, and `reDerivesTodaysFocusOnResume` never combines resume with drafts. So the duplicate-logging path is invisible to CI.
- **Recommendation:** Add a test that confirms (and one that dismisses) a draft, calls `onResume()`, and asserts `draftSets` stays empty; this is the regression S4-001 needs.
- **Fix cost:** S

**TS4-003 — minor — test gap — file:line:** `feature/splitbuilder/.../SplitBuilderViewModelTest.kt:85` (no snapshot-precedence test)
- **Why it matters:** S5-001 (`showAccepted` lets live catalog names override the plan's snapshotted names) is unverified — the only `exerciseNames` assertion is for a catalog name. Nothing renames an exercise after acceptance and asserts the accepted plan keeps its snapshot.
- **Recommendation:** Add a test that accepts a plan, changes the exercise name in the catalog fake, and asserts the displayed name is the snapshotted one (once S5-001 is triaged).
- **Fix cost:** S

**TS4-004 — minor — test gap — file:line:** `feature/splitbuilder/.../SplitBuilderViewModelTest.kt` (no `involvements` reference in the file)
- **Why it matters:** S5-002 (the regenerate fingerprint folds in the ≥0.7 tags but not the involvement weights) is unverified: no splitbuilder test edits `involvements`, so an edit inside a band that should re-enable Regenerate is never exercised.
- **Recommendation:** Add a test that edits an exercise's involvement weight without changing its primary/secondary tags and asserts `canRegenerate` flips to true.
- **Fix cost:** S

**TS4-005 — minor — test gap — file:line:** `feature/equipment/.../EquipmentProfilerViewModelTest.kt:210-221` (removes one muscle; no clear-all save)
- **Why it matters:** S2-001 (clearing every muscle on a built-in stores `NULL` and silently reverts to the seed weights) is unverified. The editor test removes a single muscle from the in-progress map but never saves a built-in with an empty `involvements` map and reads it back.
- **Recommendation:** Add a test that clears all muscles, saves, and asserts the resolved `effectiveInvolvements` is empty (currently it would fail, pinning S2-001).
- **Fix cost:** S

### Flakiness

**TS4-006 — refuted (no finding) — flakiness — file:line:** `feature/fatigueheatmap/.../FatigueHeatmapViewModelTest.kt:35-45,129-203`
- **Why it matters:** The recorded "heatmap ticker tests once hung" is no longer reproducing in the code: the test installs a `StandardTestDispatcher`, drives the ticker with `advanceTimeBy(59_999)`/`advanceTimeBy(1)` + `runCurrent()`, uses a `FakeClock` that counts reads, and always cancels via `onPause()` in `finally`. `rg` finds no `System.*`, `Thread.sleep`, or real `delay()` anywhere in the feature/`shared` tests. I attempted to falsify this and could not.
- **Recommendation:** None. Keep the virtual-time pattern; if the ticker is ever reworked, preserve the boundary split around the 60 s interval.
- **Fix cost:** —

### Nits

**TS4-007 — nit — duplication — file:line:** feature tests' `setUp`/`tearDown` (`Dispatchers.setMain(StandardTestDispatcher())` / `resetMain()`)
- **Why it matters:** The Main-dispatcher install/reset pair is copy-pasted into every feature ViewModel test; it is easy to forget the reset and leak a dispatcher into a later test in the same JVM.
- **Recommendation:** Extract a small shared test rule/helper in the same fixtures module as TS4-001.
- **Fix cost:** S

### Seed-observation status (TS4-resident)

- The 7 `WorkoutLogRepository` fakes / shared `testFixtures`: **confirmed and corrected** — 8 doubles across 7 files; TS4-001 proposes the shared fixtures module (and flags that KMP `testFixtures` cross-module consumption must be validated first).
- Flaky patterns (heatmap ticker): **refuted** — the ticker tests are deterministic (virtual time + `FakeClock` + `finally { onPause() }`); no real clocks/sleeps anywhere in feature/`shared` tests.
- VM/state gaps: **found** — S4-001 (TS4-002), S5-001 (TS4-003), S5-002 (TS4-004), S2-001 (TS4-005) are all untested.
- Koin verification coverage: **partly confirmed** — `KoinModulesVerificationTest` covers `verify()` plus a runtime-resolution test that works around `verify()` not reflecting `singleOf` constructors; PLANS.md already records that the real Android Keystore/`Context` bindings, the Settings Composable's direct model-manager injection, and the iOS graph remain unverified. No new finding.

### TS4 finding summary

- blocker: 0
- major: 2 (TS4-001, TS4-002)
- minor: 3 (TS4-003, TS4-004, TS4-005)
- nit: 1 (TS4-007)
- total: 6
(TS4-006 recorded as refuted, not a finding.)

## TR — test redundancy (cross-cutting; signal, not quantity)

- **Slice:** all test sources, cross-cutting. Pinned `7e04245`. Date 2026-10-03.
- **Question:** not the test:code ratio, but whether distinct tests assert the **same behavior through different entry points** (so they all fail together for one reason), or assert implementation details. No test was modified; each recommendation states why removing/merging it would not reduce branch coverage.

### Redundancy findings

**TR-001 — major — duplication — file:line:** `core/domain/src/commonTest/.../engine/VolumeAwareRepsTest.kt`, `WeeklyPlanSanitizerTest.kt:18-33`, `core/network/.../GeminiWorkoutPlannerEngineTest.kt:368-376`, `core/llm/.../LocalLlmWorkoutPlannerEngineTest.kt:42-58`
- **Why it matters:** The same behavior — "a set override carries volume; reps stay in the goal's compound/isolation band" — is asserted at four levels with the same values (5 sets → 6 compound reps, 2 sets → 12 isolation reps). `VolumeAwareRepsTest` is the owner; the sanitizer test verifies the sanitizer applies it; the two engine tests repeat the identical numeric assertions. A change to `VolumeAwareReps.repsFor` fails all four for one reason, and the two engine copies add no branch the others lack.
- **Would removal reduce coverage?** No, if one canonical test stays. The engines' delegation to the shared sanitizer is already proven by `mapsStructuredResponseToWeeklyPlan` (Gemini) and `usesOnDeviceOutputWhenAvailable` (local), and the sanitizer itself is covered by `WeeklyPlanSanitizerTest`. Merge value: keep the `VolumeAwareRepsTest` band cases + one sanitizer test; reduce the engine copies to an assertion that the plan was sanitized.
- **Recommendation:** Keep `VolumeAwareRepsTest` and `WeeklyPlanSanitizerTest`; in each engine test assert only that a model plan's reps/sets were normalized (or drop the numeric copy). Do not delete until the caller confirms.
- **Fix cost:** S

**TR-002 — minor — duplication (implementation detail) — file:line:** `core/network/.../GeminiWorkoutPlannerEngineTest.kt:109-160,379-437` and `core/llm/.../LocalLlmWorkoutPlannerEngineTest.kt:60-189` vs `core/domain/.../PlannerPromptFragmentsTest.kt`
- **Why it matters:** `PlannerPromptFragmentsTest` owns the exact formatting and the sharing-gating of every shared fragment (equipment, caps, periodization, deload, volume guidance, recent/progressed weights). Both engine test files then re-assert the same strings via `prompt.contains(...)` — 28 `contains`/prompt assertions in the Gemini test and 32 in the local test — including the identical `sendsRecentWeights…` / `omitsWorkoutData…` / `sendsProgressedWeights…` trio duplicated between the two engines. A wording change fails all three files for one reason.
- **Would removal reduce coverage?** No for the fragment text: it lives in `PlannerPromptFragmentsTest`. The engines' unique signal is "the engine calls the fragment with the request's values", which one `contains` per engine proves. Removing the exact-format re-assertions keeps wiring coverage.
- **Recommendation:** In each engine test keep one assertion that the fragment appears/disappears with `includeWorkoutData`; move any exact-format expectation back to `PlannerPromptFragmentsTest`. (This is the main reason `LocalLlmWorkoutPlannerEngineTest` is 2× and `GeminiWorkoutPlannerEngineTest` is verbose.)
- **Fix cost:** M

**TR-003 — minor — duplication — file:line:** `core/domain/.../DeterministicWorkoutPlannerEngineTest.kt:343-356` (`skipsExercisesAboveTheFatigueSkipThreshold`) and `:358-372` (`reducesSetsWhenAPrimaryMuscleIsFatigued`)
- **Why it matters:** Both are single-candidate special cases of `reductionAndSkipBoundariesUseUnroundedScores` (`:374-396`), which loops the same thresholds with `nextDown/nextUp` boundaries (0.65 → 3 sets, 0.80 → skipped). 0.9 and 0.7 land inside the ranges the loop already covers, so all three fail together for any threshold change.
- **Would removal reduce coverage?** No. The loop covers both the reduce and skip branches and their exact boundaries; the two named tests only restate an in-range instance. (They do read as documentation of intent; if kept, mark them as examples, but they carry no extra branch.)
- **Recommendation:** Fold the two into the boundary loop (or keep one) — the boundary test is the stronger superset.
- **Fix cost:** S

**TR-004 — minor — duplication (implementation detail) — file:line:** `core/database/.../SqlDelightWorkoutLogRepositoryTest.kt:156-177`, `feature/fatigueheatmap/.../FatigueHeatmapViewModelTest.kt:232-240`
- **Why it matters:** `storedRepsReachTheCalculatorThroughBothMappingPaths` asserts the calculator's exact formula (`1/13`) inside a repository mapping test, and the heatmap test asserts the calculator's exact outputs (`0.8`, `0.2`, `0.05`). Both re-derive math owned by `FatigueCalculatorTest`; a formula change fails these too. The unique signals are "reps survive mapping" and "the VM maps sets→entries and refreshes" respectively.
- **Would removal reduce coverage?** No for the formula (owned by `FatigueCalculatorTest`). The mapping/refresh behavior is still asserted if the tests assert structure (reps==2; entries present, ordered, and nonzero after a change) instead of the derived constant.
- **Recommendation:** Keep the mapping/refresh assertions but assert the invariant, not the calculator constant; or explicitly label them as intended integration checks.
- **Fix cost:** S

**TR-005 — minor — low-signal (tautology) — file:line:** `core/domain/.../CalculateMuscleFatigueUseCaseTest.kt:21-35`
- **Why it matters:** `matchesTheCalculatorResult` builds the expected value with `FatigueCalculator().calculate(...)` and asserts the use case returns the same map — the oracle is the implementation under delegation. It can only fail if the one-line delegate stops delegating, which is already visible in the class. `returnsZeroForUntrainedMuscles` is the meaningful case.
- **Would removal reduce coverage?** No — `FatigueCalculatorTest` covers the math and the use case has no other logic. If a delegation guarantee is wanted, assert a hand-computed value instead of calling the collaborator in the oracle.
- **Recommendation:** Drop the tautology or replace the oracle with a fixed expected map.
- **Fix cost:** S

**TR-006 — nit — copy-paste — file:line:** `core/domain/.../DeterministicWorkoutPlannerEngineTest.kt:123`, `:147`, `:176`; and `:272`, `:293`
- **Why it matters:** `prefersBarbellBenchWhenAvailable` / `prefersDumbbellBenchWhenBarbellIsUnavailable` / `fallsBackToBodyweightWhenNothingElseIsAvailable` are one body with three equipment-set/expected-id pairs; the rotate/repeat pair (`:272`, `:293`) is a two-case parameterization. Only the inputs and one assertion differ.
- **Would removal reduce coverage?** No — a table-driven test enumerating the same `(equipment → expected ids)` cases covers exactly the same branches with less duplication.
- **Recommendation:** Collapse each family into a loop over cases (the file already does this for the fatigue boundaries).
- **Fix cost:** S

**TR-007 — nit — implementation detail — file:line:** `feature/fatigueheatmap/.../FatigueHeatmapViewModelTest.kt:166-199` (`clock.reads`), `core/network/.../GeminiWorkoutPlannerEngineTest.kt:224,240,256,281,303` (`calls`)
- **Why it matters:** These assert call counts rather than observable output. The retry counts encode real behavior (attempts) and are defensible; the heatmap `assertEquals(readsBeforeTick + 1, clock.reads)` is brittle — any extra `nowMillis()` read in `refresh()` breaks it without a behavior change.
- **Would removal reduce coverage?** The ticker's "does not run while paused" can be asserted by the unchanged score alone (already present at `:170`); the exact count adds no behavioral branch.
- **Recommendation:** Keep the retry counts; for the ticker assert the score/behavior, or allow a range.
- **Fix cost:** S

### Oversized test files (request #4)

Flagged specifically, **not** judged wasteful:

| Test file | lines | source | ratio |
| --- | ---: | ---: | ---: |
| `SqlDelightWorkoutLogRepositoryTest.kt` | 469 | 150 | 3.1× |
| `FatigueCalculatorTest.kt` | 603 | 194 | 3.1× |
| `WorkoutLoggerViewModelTest.kt` | 1390 | 458 | 3.0× |
| `SplitBuilderViewModelTest.kt` | 735 | 246 | 3.0× |
| `DeterministicWorkoutPlannerEngineTest.kt` | 942 | 334 | 2.8× |
| `LocalLlmWorkoutPlannerEngineTest.kt` | 626 | 308 | 2.0× |

- **TR-008 — nit — file-size flag — reasoning:** `FatigueCalculatorTest` (3.1×) is justified — the calculator has many independent rule branches (session boundaries, relative-load window, RIR, compound/isolation decay) and each needs its own case. `WorkoutLoggerViewModelTest` (3.0×) mirrors a 458-line ViewModel with many user actions, session/backdate states, and units — mostly distinct behavior. `SqlDelightWorkoutLogRepositoryTest` (3.1×) is many small field round-trips; some could be table-driven, but each covers a different column. The one that leans most on **low-signal repetition** is `LocalLlmWorkoutPlannerEngineTest` (2.0×): its bulk is prompt-string `contains` assertions that overlap `PlannerPromptFragmentsTest` and its Gemini twin (TR-002). No file here looks padded for its own sake; TR-002 is the only size driver that is genuinely redundant.
- **Recommendation:** Act on TR-002 first, then re-measure; do not cut the domain/VM tests for size alone.
- **Fix cost:** —

### TR summary

- blocker: 0
- major: 1 (TR-001)
- minor: 4 (TR-002, TR-003, TR-004, TR-005)
- nit: 3 (TR-006, TR-007, TR-008)
- total: 8 (all report-only; no test changed)

