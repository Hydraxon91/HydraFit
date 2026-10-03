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

