# Koin binding and verification map

Paths are relative to the repository root; package shorthand is defined in
`../SKILL.md`. Read current source and relevant tests before using this reference.

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
| Feature `OnboardingModule.kt` | `onboardingModule` (exported, aggregated by the shell; `OnboardingViewModel` + `OnboardingEntryViewModel`) |
| Feature `progress/ProgressModule.kt` | `progressModule` (exported; one `ProgressViewModel`). Registered in the shell's aggregation separately from the six tab destinations; progress is a pushed, non-tab route. |
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
Onboarding's `OnboardingModuleVerificationTest` verifies its external ports and resolves its
ViewModels/save actions at runtime, including an entry-ViewModel init read that must not write.
The module owns the two setup use-case factories; databaseModule binds `SetupWriteRepository`,
`SetupRepository` and `PreferredWeekdaysRepository`. The shared graph test now aggregates
`onboardingModule` in both its static and runtime checks.
`theDomainUseCaseGraphResolvesAtRuntime` actually resolves domain use cases with
fake ports, covering missing collaborators that static verification can miss.
Network verification supplies external types including `HttpClientEngine`.
Platform Context/Keystore and iOS wiring are not proved by these host tests.

`domainModule` also registers `CorrectWorkoutSetUseCase`, using the existing `SessionResegmenter`
port. `WorkoutLogMutations` delegates both full performed-value and time-only corrections; its
four use cases do not add another Logger ViewModel constructor dependency. Runtime graph tests
resolve the new correction use case explicitly.

`loggerModule` also registers `WorkoutHistoryViewModel` (history repository, exercise-note
repository, weight-unit repository and `TimeProvider`), the Logger's second presentation ViewModel.
It holds no new port: every collaborator is already bound elsewhere in the graph, and a history
correction reuses the Logger ViewModel's existing editor rather than a third one.

`databaseModule` exposes the same `SqlDelightWorkoutScheduleRepository` singleton through schedule
and occurrence-replacement ports, and binds `TrainingGuidancePreferenceRepository`. `domainModule`
registers `ReplaceOccurrenceExerciseUseCase`; `loggerModule` registers its separate presentation
ViewModel plus an eager, closeable `RestAlertCoordinator`. Its collaborators all serve app-lifetime
alarm reconciliation, not unrelated actions. Android binds the alarm/notification adapter; iOS binds
`UnsupportedRestAlertScheduler`. Settings registers `TrainingGuidanceSettingsViewModel`. Runtime
graph tests resolve these additions; Android Context/permission/receiver delivery remains platform
verification, not a host-test claim.

`databaseModule` also binds `ReminderDeliveryRepository` and the process-local
`WorkoutReminderDeliveryGate`, one shared instance injected into `SqlDelightWorkoutScheduleRepository`,
`SqlDelightWorkoutReminderPreferenceRepository`, `SqlDelightBackupRepository` and the coordinator so
their writes and the posting path serialize on the same gate. `routinesModule` registers the eager,
closeable `WorkoutReminderCoordinator` for app-lifetime reminder reconciliation; Android binds
`AndroidWorkoutReminderScheduler` (alarm plus notification adapter) and iOS binds
`UnsupportedWorkoutReminderScheduler`, alongside the Android `TimeProvider` override for zone-aware
local-minute resolution. The shared runtime graph test resolves the coordinator with fake reminder
ports and a not-ready startup gate, so construction is proved without delivery.

Settings also registers `WorkoutReminderSettingsViewModel` over the reminder preference repository
and scheduler; a runtime graph test resolves it. `loggerModule` owns the singleton
`RestCompletionPresentation`, injected into the eager `RestAlertCoordinator` and resolved by the
Android receiver and root-mounted Logger presenter. The shared runtime graph test resolves both the
coordinator and presentation singleton. No new platform binding or dependency is needed for this
presentation state; lifecycle and Android notification behavior still require runtime acceptance.

`databaseModule` binds `ExerciseProgressRepository` (`SqlDelightExerciseProgressRepository`), the
read-only seam behind `:feature:progress`. It has no write path: `observeHistory` pages a bounded
keyset, `observeProgress` replays bounded pages through `ExerciseProgressCalculator`,
`observeOccurrenceExerciseIds` returns one occurrence's distinct recorded exercise ids live, and
`observeSet` resolves one exercise-scoped set id. `progressModule` registers `ProgressViewModel`
over that repository, the `ExerciseCatalog` and `WeightUnitRepository` ports plus `TimeProvider`;
the shared graph test aggregates `progressModule` and resolves the ViewModel with fake ports and a
test Main dispatcher, and `ProgressModuleVerificationTest` verifies it at the feature boundary.

`domainModule` also registers `ObserveOccurrenceRecordsUseCase` (composing
`observeOccurrenceExerciseIds`, `observeProgress` and `observeSet`), and `loggerModule` registers the
route-owned `WorkoutSummaryRecordsViewModel` over it, the `ExerciseCatalog`, `WeightUnitRepository`
and `TimeProvider`. The shared graph test verifies both: `allModulesResolve` statically covers the new
use-case single and ViewModel, and `theWorkoutLoggerViewModelResolvesAtRuntime` resolves the use case
and the records ViewModel with fake ports and a test Main dispatcher. There is no
`WorkoutLoggerModuleVerificationTest`; the logger module has no host-test seam of its own, so its
runtime coverage stays in the shared graph test.

0.8.2 `databaseModule` binds `TrainingCoverageRepository` to
`SqlDelightTrainingCoverageRepository`; the database graph verification resolves
the binding at runtime. The pure `TrainingCoverageCalculator` has no DI collaborators.

0.8.3 `databaseModule` binds `BodyWeightRepository` to `SqlDelightBodyWeightRepository`
(database plus the shared `TimeProvider`, used for the future-day rule); the database
graph verification resolves it at runtime.
