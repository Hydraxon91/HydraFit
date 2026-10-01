---
name: hydrafit-mechanics
description: Use when implementing or debugging HydraFit fatigue, workout planning, SQLDelight repositories or migrations, Koin wiring, or feature UI. Provides project-specific formulas, defaults, source locations, and implementation recipes complementary to AGENTS.md.
---

# HydraFit mechanics

This is a code-level reference. Behavioral rules, approvals, module boundaries,
and verification requirements remain in `AGENTS.md`; future work remains in
`PLANS.md`.

Defaults below describe the implementation when this skill was authored. The
named source files are authoritative when code changes. Schema versions are
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
| Platform adapters | `shared/src/androidMain/kotlin/com/hydrafit/app/AndroidDatabaseModule.kt` and corresponding `iosMain/IosDatabaseModule.kt` |

## Fatigue: exact calculation

Sources: domain `fatigue/FatigueCalculator.kt`, `FatigueConfig.kt`, `LoggedSet.kt`,
and `MuscleTarget.kt`; supporting explanation: `docs/fatigue-formula.md`.

For each muscle independently:

```text
volume(set, muscle) = sum of matching MuscleTarget.weight values
events = non-warmup sets with nonzero volume, sorted by timestampMillis
decay(elapsedMillis, H) = 2 ^ (-max(elapsedMillis, 0) / H)

raw = 0
previousMillis = first event's timestamp
for each event:
    raw = raw * decay(event.timestamp - previousMillis, H) + event.volume
    previousMillis = event.timestamp

recovered = raw * decay(nowMillis - last event.timestamp, H)
score = clamp(recovered / referenceVolume, 0, 1)
```

`H` is the muscle half-life in milliseconds. Empty events produce `0.0`.
The result includes every `MuscleGroup`. Reps and lifted kilograms do not enter
this calculation; accumulated involvement-weighted sets do.

Verbatim defaults:

```text
referenceVolume = 24.0
fallback half-life = 48.hours
CHEST, BACK, QUADS, HAMSTRINGS, GLUTES = 48.hours
SHOULDERS = 36.hours
BICEPS, TRICEPS, CALVES, CORE = 24.hours
```

`nowMillis` is supplied to `calculate`, making decay tests deterministic.
`CalculateMuscleFatigueUseCase` is the domain entry point.

## Muscle mapping and catalog persistence

The database encodes involvements as comma-separated `MUSCLE:weight` pairs,
for example `CHEST:1.0,CORE:0.2,TRICEPS:0.4`.
`ExerciseEncoding.kt` owns encoding and decoding; encoding sorts by muscle name.

There are three distinct locations for this map:

1. `exercise.involvements`: catalog defaults or custom exercise data.
2. `exerciseOverride.involvements`: an optional built-in exercise edit.
3. `workoutSet.involvements`: effective muscle mapping snapshotted when logged.

`SqlDelightExerciseCatalog` overlays nullable override fields onto catalog rows.
Display groups are derived from the resolved map: weight `>= 0.7` is primary,
and lower positive weights are secondary. The SQL legacy primary/secondary
columns were removed; these remain domain/display concepts.

Domain `equipment/Exercise.kt` provides `effectiveInvolvements`. Its fallback
for an empty explicit map is primary muscles at `1.0` plus secondary muscles at
`0.5`. Fatigue uses `MuscleTarget(muscle, weight)` values.

Editor tiers: None, Low `0.3`, Mid `0.5`, High `0.7`, Primary `1.0`.
Stored values are doubles, not a tier enum.

`DefaultExercises.kt` is the seed catalog. `SeedExerciseCatalog` inserts missing
rows and backfills involvements only where null. A fresh installation can
therefore have corrected seed weights while an upgraded installation retains
legacy-equivalent weights. That difference is intentional data preservation.

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
recentExerciseIdsByPattern, suggestedWeightsKg, equipmentMaxWeights,
includeWorkoutData, recentWeights, weekNumber, cycleNumber, isDeload
```

Important distinction: `PlanRequest.suggestedWeightsKg` contains estimated 1RM
baselines adjusted by progression, despite the name. The output field
`PlannedExercise.suggestedWeightKg` is a working-set load. AI prompt wording
must not accidentally present a 1RM baseline as a ready-to-lift working weight.

Manual PRs enter the baseline through `WorkoutPlanSources.personalRecords`:
`baseline = max(logged-set estimated 1RM, manual-record estimated 1RM)` per
exercise. They are not workout sets and do not add fatigue.

AI history and weight suggestions are gated by `includeWorkoutData`.
Deterministic suggestions are local computations and do not require the AI
sharing toggle.

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

The deterministic engine computes:

```text
working = roundToIncrement(progressed1RM * intensityForReps(reps) * deloadScale)
suggestedWeightKg = EquipmentWeightLimit.clamp(working, ceilingFor(exercise))
```

Volume-aware reps:

```text
intendedVolume = goal's default sets * goal's default reps for the slot type
reps = roundToInt(intendedVolume / chosenSets).coerceIn(3, 20)
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

### Selection, schedule, and periodization

Deterministic candidate ordering: weighted fatigue, not recently used,
equipment rank, then exercise id. Weighted fatigue is the maximum of
`involvementWeight * muscleFatigue`; skip/reduce decisions instead use raw
fatigue of muscles with involvement `>= 0.7`.

```text
FATIGUE_REDUCE_THRESHOLD = 0.5
FATIGUE_SKIP_THRESHOLD = 0.85
TARGETED_THRESHOLD = 0.7
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

`PlanVarietyEnforcer` removes repeated foci, within-day duplicates, and repeated
compound exercise ids across days. It does not repair the focus schedule or
choose substitute exercises. Repeated focus can be valid for a cyclic split;
the current unique-focus rule can reject such model plans. Likewise, the local
schema's `items.anyOf` allows any listed focus on each item; it does not pin
focus by array position. Inspect these when diagnosing unexpected fallback.

Current AI schemas request 4–6 exercises/day; sanitizer and enforcer accept a
minimum of 2 after filtering. These are distinct current thresholds, with
alignment tracked in `PLANS.md` rather than assumed implemented.

- Gemini implementation: network `GeminiWorkoutPlannerEngine.kt` and
  `GeminiDtos.kt`. HTTP transient retries: `MAX_RETRIES = 2`, base delay
  `1_000` ms, maximum delay `8_000` ms. HTTP failures surface as
  `PlanGenerationException`; sanitizer rejection falls back to deterministic.
- Local implementation: `LocalLlmWorkoutPlannerEngine.kt`, with
  `MAX_ATTEMPTS = 2`. Prompt numbers are 1-based catalog indexes mapped back to
  ids before sanitization. Unavailability and OOM fall back; cancellation is
  rethrown rather than converted into fallback.
- Native runtime: `core/llm/src/androidMain/kotlin/com/hydrafit/app/core/llm/LiteRtLmTextGenerator.kt`.
  It caches the Engine but creates/closes a Conversation per call. Reusing a
  failed Conversation can cause "roles must alternate" errors.
- Imported models live at `filesDir/on_device_llm.litertlm`; filename target
  classification is persisted separately. Portable packs try GPU then CPU;
  NPU-tagged packs try NPU, GPU, then CPU. Installed-file presence is not proof
  that native inference works.

## SQLDelight recipe and migration fixtures

Schema directory:
`core/database/src/commonMain/sqldelight/com/hydrafit/app/core/database/`.

Query files: `Equipment.sq`, `Exercise.sq`, `ExerciseOverride.sq`,
`PersonalRecord.sq`, `PlanHistory.sq`, `PlannerEngine.sq`, `UserEquipment.sq`,
and `WorkoutLog.sq`.

`N.sqm` migrates from version N to N+1. At authoring, migrations were `1.sqm`
through `22.sqm`, producing schema 23. Determine the next version from the
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
| Shell `DomainModule.kt` | `domainModule`: use cases, configs, deterministic/local engines, provider |
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
navigation in `EquipmentNavigation.kt`.

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

`scripts/snap.sh [output]` installs debug, force-stops/relaunches HydraFit, waits,
then captures a screenshot. This restart matters when testing retained UI
state. `scripts/tap.sh x y [output]` taps, waits, and captures without relaunching.
Both default to `/tmp/hydrafit-screen.png`. Set `ANDROID_SERIAL` to the emulator
when more than one adb target is attached.

Gradle task lookup by subsystem:

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
