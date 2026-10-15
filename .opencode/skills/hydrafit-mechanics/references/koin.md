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
