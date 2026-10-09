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

`domainModule` also registers `CorrectWorkoutSetUseCase`, using the existing `SessionResegmenter`
port. `WorkoutLogMutations` delegates both full performed-value and time-only corrections; its
four use cases do not add another Logger ViewModel constructor dependency. Runtime graph tests
resolve the new correction use case explicitly.
