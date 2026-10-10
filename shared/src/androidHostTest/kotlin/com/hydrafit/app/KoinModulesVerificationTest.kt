package com.hydrafit.app

import com.hydrafit.app.core.database.DatabaseDriverFactory
import com.hydrafit.app.core.database.databaseModule
import com.hydrafit.app.core.domain.backup.BACKUP_FORMAT
import com.hydrafit.app.core.domain.backup.BACKUP_FORMAT_VERSION
import com.hydrafit.app.core.domain.backup.BackupApplyError
import com.hydrafit.app.core.domain.backup.BackupCatalog
import com.hydrafit.app.core.domain.backup.BackupCatalogManifest
import com.hydrafit.app.core.domain.backup.BackupFile
import com.hydrafit.app.core.domain.backup.BackupRepository
import com.hydrafit.app.core.domain.backup.BackupStagingRepository
import com.hydrafit.app.core.domain.backup.PendingBackup
import com.hydrafit.app.core.domain.engine.AcceptWeeklyPlanUseCase
import com.hydrafit.app.core.domain.engine.AcceptedPlan
import com.hydrafit.app.core.domain.engine.BuildPlannerLoadInputsUseCase
import com.hydrafit.app.core.domain.engine.DeterministicWorkoutPlannerEngine
import com.hydrafit.app.core.domain.engine.EngineAvailability
import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.ObserveAcceptedPlanUseCase
import com.hydrafit.app.core.domain.engine.ObserveWorkoutPlanInputsUseCase
import com.hydrafit.app.core.domain.engine.OnDevicePlanProgress
import com.hydrafit.app.core.domain.engine.PlanBuilderActions
import com.hydrafit.app.core.domain.engine.PlanHistoryRepository
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.SubstituteExerciseUseCase
import com.hydrafit.app.core.domain.engine.SuggestWeightsUseCase
import com.hydrafit.app.core.domain.engine.TrainingGoal
import com.hydrafit.app.core.domain.engine.WeeklyPlanSanitizer
import com.hydrafit.app.core.domain.engine.WorkoutPlanSources
import com.hydrafit.app.core.domain.engine.WorkoutPlanSourcesRepository
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.ExerciseExclusion
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.ExercisePreference
import com.hydrafit.app.core.domain.fatigue.LoggedSet
import com.hydrafit.app.core.domain.routine.ArchiveRoutineTemplateUseCase
import com.hydrafit.app.core.domain.routine.ConvertPlanToTemplateUseCase
import com.hydrafit.app.core.domain.routine.DeleteRoutineTemplateUseCase
import com.hydrafit.app.core.domain.routine.DuplicateRoutineTemplateUseCase
import com.hydrafit.app.core.domain.routine.ObserveRoutineTemplatesUseCase
import com.hydrafit.app.core.domain.routine.RoutineTemplate
import com.hydrafit.app.core.domain.routine.RoutineTemplateActions
import com.hydrafit.app.core.domain.routine.RoutineTemplateRepository
import com.hydrafit.app.core.domain.routine.SaveRoutineTemplateUseCase
import com.hydrafit.app.core.domain.schedule.ActivateRoutineUseCase
import com.hydrafit.app.core.domain.schedule.CancelTrainingActivationUseCase
import com.hydrafit.app.core.domain.schedule.CreateTrainingActivationUseCase
import com.hydrafit.app.core.domain.schedule.EditUnstartedOccurrenceUseCase
import com.hydrafit.app.core.domain.schedule.FinishTrainingBlockUseCase
import com.hydrafit.app.core.domain.schedule.FinishWorkoutOccurrenceUseCase
import com.hydrafit.app.core.domain.schedule.MoveWorkoutOccurrenceUseCase
import com.hydrafit.app.core.domain.schedule.PreviewWorkoutScheduleUseCase
import com.hydrafit.app.core.domain.schedule.RepeatTrainingBlockUseCase
import com.hydrafit.app.core.domain.schedule.SelectWorkoutOccurrenceUseCase
import com.hydrafit.app.core.domain.schedule.SkipWorkoutOccurrenceUseCase
import com.hydrafit.app.core.domain.schedule.StartWorkoutOccurrenceUseCase
import com.hydrafit.app.core.domain.schedule.SwitchScheduleModeUseCase
import com.hydrafit.app.core.domain.schedule.TrainingActivation
import com.hydrafit.app.core.domain.schedule.WorkoutLoggingActions
import com.hydrafit.app.core.domain.schedule.WorkoutOccurrence
import com.hydrafit.app.core.domain.schedule.WorkoutScheduleActions
import com.hydrafit.app.core.domain.schedule.WorkoutScheduleRepository
import com.hydrafit.app.core.domain.schedule.WorkoutScheduleState
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.workout.CorrectWorkoutSetTimeUseCase
import com.hydrafit.app.core.domain.workout.CorrectWorkoutSetUseCase
import com.hydrafit.app.core.domain.workout.DeleteWorkoutSetUseCase
import com.hydrafit.app.core.domain.workout.EndWorkoutSessionUseCase
import com.hydrafit.app.core.domain.workout.GetWorkoutLogUseCase
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.LogWorkoutSetUseCase
import com.hydrafit.app.core.domain.workout.ObserveOpenWorkoutSessionUseCase
import com.hydrafit.app.core.domain.workout.SessionResegmenter
import com.hydrafit.app.core.domain.workout.StartWorkoutSessionUseCase
import com.hydrafit.app.core.domain.workout.WorkoutLogRepository
import com.hydrafit.app.core.domain.workout.WorkoutSession
import com.hydrafit.app.core.domain.workout.WorkoutSessionRepository
import com.hydrafit.app.core.domain.workout.WorkoutSet
import com.hydrafit.app.core.domain.workout.WorkoutSetCorrection
import com.hydrafit.app.core.llm.NoopOnDevicePlannerLogger
import com.hydrafit.app.core.llm.OnDevicePlannerLogger
import com.hydrafit.app.core.llm.OnDeviceTextGenerator
import com.hydrafit.app.core.network.ApiKeyProvider
import com.hydrafit.app.core.network.GeminiWorkoutPlannerEngine
import com.hydrafit.app.core.userdata.backup.BackupFileStore
import com.hydrafit.app.core.userdata.backup.UnsupportedBackupFileStore
import com.hydrafit.app.core.userdata.equipment.ExerciseExclusionRepository
import com.hydrafit.app.core.userdata.equipment.ExercisePreferenceRepository
import com.hydrafit.app.core.userdata.settings.ApiKeyStore
import com.hydrafit.app.core.userdata.settings.AppVersionProvider
import com.hydrafit.app.core.userdata.settings.EnginePreferenceRepository
import com.hydrafit.app.core.userdata.settings.GuidedWorkoutPreferenceRepository
import com.hydrafit.app.core.userdata.settings.TrainingGoalRepository
import com.hydrafit.app.core.userdata.settings.WeightUnitRepository
import com.hydrafit.app.feature.equipment.ExercisePlanningSettingsViewModel
import com.hydrafit.app.feature.equipment.equipmentModule
import com.hydrafit.app.feature.fatigueheatmap.fatigueHeatmapModule
import com.hydrafit.app.feature.logger.WorkoutLoggerViewModel
import com.hydrafit.app.feature.logger.loggerModule
import com.hydrafit.app.feature.routines.RoutinesViewModel
import com.hydrafit.app.feature.routines.routinesModule
import com.hydrafit.app.feature.settings.AcknowledgmentsViewModel
import com.hydrafit.app.feature.settings.BackupViewModel
import com.hydrafit.app.feature.settings.SettingsViewModel
import com.hydrafit.app.feature.settings.settingsModule
import com.hydrafit.app.feature.splitbuilder.splitBuilderModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.verify.verify

@OptIn(KoinExperimentalAPI::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class KoinModulesVerificationTest {

    private val testPlatformModule = module {
        single<TimeProvider> { TimeProvider { 0L } }
        single<ApiKeyStore> { FakeApiKeyStore }
        single<AppVersionProvider> { FakeAppVersionProvider }
        single<ApiKeyProvider> { ApiKeyProvider { "test-key" } }
        single<OnDeviceTextGenerator> { FakeOnDeviceTextGenerator }
        single<OnDevicePlannerLogger> { NoopOnDevicePlannerLogger }
        single<BackupFileStore> { UnsupportedBackupFileStore() }
    }

    private val allModules = module {
        includes(
            domainModule,
            databaseModule,
            equipmentModule,
            fatigueHeatmapModule,
            splitBuilderModule,
            loggerModule,
            settingsModule,
            routinesModule,
            testPlatformModule
        )
    }

    @Test
    fun allModulesResolve() {
        allModules.verify(
            extraTypes = listOf(
                DatabaseDriverFactory::class,
                GeminiWorkoutPlannerEngine::class
            )
        )
    }

    /**
     * `verify()` does not reflect the constructor of lambda/`singleOf` definitions, so a missing
     * collaborator (e.g. `ProgressWeightsUseCase`) can slip through and only crash on device. Boot a
     * real container over the domain graph (with fakes for the database-backed repositories) and
     * resolve the `singleOf` use cases, so a missing binding fails here instead.
     *
     * `GenerateWeeklySplitUseCase` is omitted because its provider resolves the Gemini engine, which
     * lives in `networkModule` and is not on `:shared`'s test classpath.
     */
    @Test
    fun theDomainUseCaseGraphResolvesAtRuntime() {
        val koin = koinApplication {
            modules(
                module {
                    single<WorkoutPlanSourcesRepository> { FakeWorkoutPlanSourcesRepository }
                    single<PlanHistoryRepository> { FakePlanHistoryRepository }
                    single<ExerciseCatalog> { FakeExerciseCatalog }
                    single<WorkoutLogRepository> { FakeWorkoutLogRepository }
                    single<WorkoutSessionRepository> { FakeWorkoutSessionRepository }
                    single<SessionResegmenter> { FakeSessionResegmenter }
                    single<RoutineTemplateRepository> { FakeRoutineTemplateRepository }
                    single<WorkoutScheduleRepository> { FakeWorkoutScheduleRepository }
                },
                domainModule,
                testPlatformModule
            )
        }.koin

        try {
            assertNotNull(koin.get<BuildPlannerLoadInputsUseCase>())
            assertNotNull(koin.get<DeterministicWorkoutPlannerEngine>())
            assertNotNull(koin.get<WeeklyPlanSanitizer>())
            assertNotNull(koin.get<ObserveWorkoutPlanInputsUseCase>())
            assertNotNull(koin.get<AcceptWeeklyPlanUseCase>())
            assertNotNull(koin.get<SubstituteExerciseUseCase>())
            assertNotNull(koin.get<PlanBuilderActions>())
            assertNotNull(koin.get<ObserveAcceptedPlanUseCase>())
            assertNotNull(koin.get<LogWorkoutSetUseCase>())
            assertNotNull(koin.get<GetWorkoutLogUseCase>())
            assertNotNull(koin.get<DeleteWorkoutSetUseCase>())
            assertNotNull(koin.get<CorrectWorkoutSetTimeUseCase>())
            assertNotNull(koin.get<CorrectWorkoutSetUseCase>())
            assertNotNull(koin.get<StartWorkoutSessionUseCase>())
            assertNotNull(koin.get<EndWorkoutSessionUseCase>())
            assertNotNull(koin.get<ObserveOpenWorkoutSessionUseCase>())
            assertNotNull(koin.get<SuggestWeightsUseCase>())
            assertNotNull(koin.get<ConvertPlanToTemplateUseCase>())
            assertNotNull(koin.get<SaveRoutineTemplateUseCase>())
            assertNotNull(koin.get<DuplicateRoutineTemplateUseCase>())
            assertNotNull(koin.get<ArchiveRoutineTemplateUseCase>())
            assertNotNull(koin.get<DeleteRoutineTemplateUseCase>())
            assertNotNull(koin.get<ObserveRoutineTemplatesUseCase>())
            assertNotNull(koin.get<RoutineTemplateActions>())
            assertNotNull(koin.get<WorkoutScheduleActions>())
            assertNotNull(koin.get<WorkoutLoggingActions>())
            assertNotNull(koin.get<PreviewWorkoutScheduleUseCase>())
            assertNotNull(koin.get<CreateTrainingActivationUseCase>())
            assertNotNull(koin.get<ActivateRoutineUseCase>())
            assertNotNull(koin.get<RepeatTrainingBlockUseCase>())
            assertNotNull(koin.get<SelectWorkoutOccurrenceUseCase>())
            assertNotNull(koin.get<StartWorkoutOccurrenceUseCase>())
            assertNotNull(koin.get<FinishWorkoutOccurrenceUseCase>())
            assertNotNull(koin.get<SkipWorkoutOccurrenceUseCase>())
            assertNotNull(koin.get<FinishTrainingBlockUseCase>())
            assertNotNull(koin.get<CancelTrainingActivationUseCase>())
            assertNotNull(koin.get<MoveWorkoutOccurrenceUseCase>())
            assertNotNull(koin.get<SwitchScheduleModeUseCase>())
            assertNotNull(koin.get<EditUnstartedOccurrenceUseCase>())
        } finally {
            koin.close()
        }
    }

    /**
     * `verify()` cannot reflect a lambda definition's `get()` chain, so boot the real
     * `settingsModule` plus the production Android version module and resolve the new VM, proving
     * the Acknowledgments binding and `AppVersionProvider` are wired at runtime.
     */
    @Test
    fun theAcknowledgmentsViewModelResolvesFromTheProductionVersionModule() {
        val koin = koinApplication {
            modules(settingsModule, appVersionModule("1.2.3"))
        }.koin

        try {
            assertEquals("1.2.3", koin.get<AppVersionProvider>().versionName)
            assertNotNull(koin.get<AcknowledgmentsViewModel>())
        } finally {
            koin.close()
        }
    }

    @Test
    fun guidedWorkoutSettingsViewModelResolvesFromKoin() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val koin = koinApplication {
            modules(
                module {
                    single<EngineAvailability> {
                        object : EngineAvailability {
                            override fun availableEngines() = listOf(PlannerEngineId.DETERMINISTIC)
                        }
                    }
                    single<EnginePreferenceRepository> { FakeEnginePreferenceRepository() }
                    single<TrainingGoalRepository> {
                        object : TrainingGoalRepository {
                            override suspend fun selectedGoal() = TrainingGoal.BALANCED
                            override fun goalFlow() = flowOf(TrainingGoal.BALANCED)
                            override suspend fun setGoal(goal: TrainingGoal) = Unit
                        }
                    }
                    single<WeightUnitRepository> {
                        object : WeightUnitRepository {
                            override suspend fun selectedUnit() = WeightUnit.KG
                            override fun unitFlow() = flowOf(WeightUnit.KG)
                            override suspend fun setUnit(unit: WeightUnit) = Unit
                        }
                    }
                    single<GuidedWorkoutPreferenceRepository> {
                        object : GuidedWorkoutPreferenceRepository {
                            override suspend fun isGuidedWorkoutEnabled() = false
                            override fun guidedWorkoutFlow() = flowOf(false)
                            override suspend fun setGuidedWorkoutEnabled(enabled: Boolean) = Unit
                        }
                    }
                },
                testPlatformModule,
                settingsModule
            )
        }.koin
        try {
            koin.get<SettingsViewModel>()
            advanceUntilIdle()
        } finally {
            koin.close()
            Dispatchers.resetMain()
        }
    }

    /**
     * The preference ViewModel is registered with a lambda `viewModel { }`, which `verify()` cannot
     * reflect, so resolve it to prove its constructor `get()` chain. The repositories it depends on
     * are proved against the real databaseModule in `DatabaseModuleVerificationTest`.
     */
    @Test
    fun theExercisePlanningSettingsViewModelResolvesAtRuntime() {
        val koin = koinApplication {
            modules(
                module {
                    single<ExercisePreferenceRepository> { FakeExercisePreferenceRepository }
                    single<ExerciseExclusionRepository> { FakeExerciseExclusionRepository }
                    single<TimeProvider> { TimeProvider { 0L } }
                },
                equipmentModule
            )
        }.koin

        try {
            assertNotNull(koin.get<ExercisePlanningSettingsViewModel>())
        } finally {
            koin.close()
        }
    }

    /**
     * The backup ViewModel is a lambda `viewModel { }`, so resolve it over `settingsModule` +
     * `domainModule` with a fake repository/catalog and the test platform module.
     */
    @Test
    fun theBackupViewModelResolvesAtRuntime() {
        val koin = koinApplication {
            modules(
                module {
                    single<BackupRepository> { FakeBackupRepository }
                    single<BackupStagingRepository> { FakeBackupStagingRepository }
                    single<BackupCatalog> { FakeBackupCatalog }
                },
                domainModule,
                settingsModule,
                testPlatformModule
            )
        }.koin

        try {
            assertNotNull(koin.get<BackupViewModel>())
        } finally {
            koin.close()
        }
    }

    private object FakeBackupRepository : BackupRepository {
        override suspend fun export(appVersion: String, exportedAtMillis: Long): BackupFile =
            emptyBackupFile(appVersion, exportedAtMillis)

        override suspend fun restore(file: BackupFile) = Unit
    }

    private object FakeBackupCatalog : BackupCatalog {
        override fun seedExerciseIds(): Set<String> = emptySet()

        override fun builtInEquipmentIds(): Set<String> = emptySet()
    }

    private object FakeBackupStagingRepository : BackupStagingRepository {
        override suspend fun stage(payload: String, appVersion: String, stagedAtMillis: Long) = Unit

        override suspend fun staged(): PendingBackup? = null

        override suspend fun clearStaged() = Unit

        override suspend fun recordApplyError(
            failure: com.hydrafit.app.core.domain.backup.BackupFailure,
            message: String?,
            occurredAtMillis: Long
        ) = Unit

        override suspend fun applyError(): BackupApplyError? = null

        override suspend fun clearApplyError() = Unit
    }

    /**
     * The routines ViewModel is a lambda `viewModel { }` with a real constructor `get()` chain, so
     * resolve it over `routinesModule` + `domainModule` with fake repositories.
     */
    @Test
    fun theRoutinesViewModelResolvesAtRuntime() {
        val koin = koinApplication {
            modules(
                module {
                    single<WorkoutPlanSourcesRepository> { FakeWorkoutPlanSourcesRepository }
                    single<PlanHistoryRepository> { FakePlanHistoryRepository }
                    single<ExerciseCatalog> { FakeExerciseCatalog }
                    single<WorkoutLogRepository> { FakeWorkoutLogRepository }
                    single<WorkoutSessionRepository> { FakeWorkoutSessionRepository }
                    single<SessionResegmenter> { FakeSessionResegmenter }
                    single<RoutineTemplateRepository> { FakeRoutineTemplateRepository }
                    single<WorkoutScheduleRepository> { FakeWorkoutScheduleRepository }
                    single<WeightUnitRepository> { FakeWeightUnitRepository }
                    single<ExerciseExclusionRepository> { FakeExerciseExclusionRepository }
                },
                domainModule,
                routinesModule,
                testPlatformModule
            )
        }.koin

        try {
            assertNotNull(koin.get<RoutinesViewModel>())
        } finally {
            koin.close()
        }
    }

    @Test
    fun theWorkoutLoggerViewModelResolvesAtRuntime() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val koin = koinApplication {
            modules(
                module {
                    single<WorkoutPlanSourcesRepository> { FakeWorkoutPlanSourcesRepository }
                    single<PlanHistoryRepository> { FakePlanHistoryRepository }
                    single<ExerciseCatalog> { FakeExerciseCatalog }
                    single<WorkoutLogRepository> { FakeWorkoutLogRepository }
                    single<WorkoutSessionRepository> { FakeWorkoutSessionRepository }
                    single<SessionResegmenter> { FakeSessionResegmenter }
                    single<RoutineTemplateRepository> { FakeRoutineTemplateRepository }
                    single<WorkoutScheduleRepository> { FakeWorkoutScheduleRepository }
                    single<WeightUnitRepository> { FakeWeightUnitRepository }
                    single<GuidedWorkoutPreferenceRepository> {
                        object : GuidedWorkoutPreferenceRepository {
                            override suspend fun isGuidedWorkoutEnabled() = false

                            override fun guidedWorkoutFlow() = flowOf(false)

                            override suspend fun setGuidedWorkoutEnabled(enabled: Boolean) = Unit
                        }
                    }
                },
                domainModule,
                loggerModule,
                testPlatformModule
            )
        }.koin

        try {
            assertNotNull(koin.get<WorkoutLoggerViewModel>())
            advanceUntilIdle()
        } finally {
            koin.close()
            Dispatchers.resetMain()
        }
    }

    private object FakeApiKeyStore : ApiKeyStore {
        override fun load(): String? = "test-key"

        override fun save(apiKey: String) = Unit

        override fun clear() = Unit
    }

    private class FakeEnginePreferenceRepository : EnginePreferenceRepository {
        private var shareWorkoutData = false

        override suspend fun selectedEngine() = PlannerEngineId.DETERMINISTIC

        override fun engineFlow(): Flow<PlannerEngineId> = flowOf(PlannerEngineId.DETERMINISTIC)

        override suspend fun setEngine(engine: PlannerEngineId) = Unit

        override suspend fun selectedDaysPerWeek() = 4

        override fun daysPerWeekFlow(): Flow<Int> = flowOf(4)

        override suspend fun setDaysPerWeek(daysPerWeek: Int) = Unit

        override suspend fun isWorkoutDataSharingEnabled() = shareWorkoutData

        override fun workoutDataSharingFlow(): Flow<Boolean> = flowOf(shareWorkoutData)

        override suspend fun setWorkoutDataSharingEnabled(enabled: Boolean) {
            shareWorkoutData = enabled
        }
    }

    private object FakeAppVersionProvider : AppVersionProvider {
        override val versionName: String = "test"
    }

    private object FakeExercisePreferenceRepository : ExercisePreferenceRepository {
        override fun observe(): Flow<Map<String, ExercisePreference>> = flowOf(emptyMap())

        override suspend fun preference(exerciseId: String): ExercisePreference =
            ExercisePreference.NEUTRAL

        override suspend fun set(exerciseId: String, preference: ExercisePreference) = Unit
    }

    private object FakeExerciseExclusionRepository : ExerciseExclusionRepository {
        override fun observe(): Flow<List<ExerciseExclusion>> = flowOf(emptyList())

        override suspend fun exclusion(exerciseId: String): ExerciseExclusion? = null

        override suspend fun set(exclusion: ExerciseExclusion) = Unit

        override suspend fun clear(exerciseId: String) = Unit
    }

    private object FakeWeightUnitRepository : WeightUnitRepository {
        override suspend fun selectedUnit(): WeightUnit = WeightUnit.KG

        override fun unitFlow(): Flow<WeightUnit> = flowOf(WeightUnit.KG)

        override suspend fun setUnit(unit: WeightUnit) = Unit
    }

    private object FakeOnDeviceTextGenerator : OnDeviceTextGenerator {
        override fun isAvailable(): Boolean = false

        override fun generate(
            prompt: String,
            jsonSchema: String?,
            onProgress: (OnDevicePlanProgress) -> Unit
        ): String = error("Not used by graph verification")
    }

    private object FakeWorkoutPlanSourcesRepository : WorkoutPlanSourcesRepository {
        override fun observe(): Flow<WorkoutPlanSources> = emptyFlow()
    }

    private object FakePlanHistoryRepository : PlanHistoryRepository {
        override fun observeLatest(): Flow<AcceptedPlan?> = flowOf(null)

        override fun observeHistory(): Flow<List<AcceptedPlan>> = flowOf(emptyList())

        override suspend fun latest(): AcceptedPlan? = null

        override suspend fun accept(plan: AcceptedPlan) = Unit

        override suspend fun substitute(
            planId: Long,
            dayIndex: Int,
            position: Int,
            newExerciseId: String,
            newExerciseName: String,
            newWeightKg: Double?,
            newLoadCapability: ExerciseLoadCapability,
            newLoadKind: LoadKind
        ) = Unit

        override suspend fun delete(planId: Long) = Unit

        override suspend fun clear() = Unit
    }

    private object FakeExerciseCatalog : ExerciseCatalog {
        override suspend fun all(): List<Exercise> = emptyList()
    }

    private object FakeWorkoutLogRepository : WorkoutLogRepository {
        override suspend fun add(set: WorkoutSet) = Unit

        override suspend fun assignSession(setId: Long, sessionId: String) = Unit

        override suspend fun delete(id: Long) = Unit

        override suspend fun updateSetPerformedAt(setId: Long, performedAtMillis: Long) = Unit

        override suspend fun all(): List<WorkoutSet> = emptyList()

        override suspend fun lastSetBySession(sessionId: String): WorkoutSet? = null

        override suspend fun setsForOccurrence(occurrenceId: Long): List<WorkoutSet> = emptyList()

        override fun setsFlow(): Flow<List<WorkoutSet>> = emptyFlow()

        override suspend fun loggedSets(): List<LoggedSet> = emptyList()

        override fun loggedSetsFlow(): Flow<List<LoggedSet>> = emptyFlow()

        override suspend fun clear() = Unit
    }

    private object FakeWorkoutSessionRepository : WorkoutSessionRepository {
        override suspend fun create(session: WorkoutSession) = Unit

        override suspend fun end(id: String, endedAtMillis: Long) = Unit

        override suspend fun open(): WorkoutSession? = null

        override fun openFlow(): Flow<WorkoutSession?> = emptyFlow()

        override suspend fun all(): List<WorkoutSession> = emptyList()
    }

    private object FakeSessionResegmenter : SessionResegmenter {
        override suspend fun resegmentAfterSetCorrection(
            setId: Long,
            correction: WorkoutSetCorrection,
            utcOffsetMillis: Long
        ) = Unit

        override suspend fun resegmentAfterTimeCorrection(
            setId: Long,
            performedAtMillis: Long,
            utcOffsetMillis: Long
        ) = Unit
    }

    private object FakeRoutineTemplateRepository : RoutineTemplateRepository {
        override fun observeAll(): Flow<List<RoutineTemplate>> = flowOf(emptyList())

        override suspend fun get(id: Long): RoutineTemplate? = null

        override suspend fun save(template: RoutineTemplate): Long = 0L

        override suspend fun setArchived(id: Long, archivedAtMillis: Long?) = Unit

        override suspend fun isReferencedByActivation(id: Long): Boolean = false

        override suspend fun delete(id: Long) = Unit
    }

    private object FakeWorkoutScheduleRepository : WorkoutScheduleRepository {
        override fun observeScheduleState(): Flow<WorkoutScheduleState> =
            flowOf(WorkoutScheduleState())

        override fun observeActiveActivation(): Flow<TrainingActivation?> = flowOf(null)

        override fun observeOccurrences(activationId: Long): Flow<List<WorkoutOccurrence>> =
            flowOf(emptyList())

        override suspend fun scheduleState(): WorkoutScheduleState = WorkoutScheduleState()

        override suspend fun setScheduleState(state: WorkoutScheduleState) = Unit

        override suspend fun activeActivation(): TrainingActivation? = null

        override suspend fun getActivation(id: Long): TrainingActivation? = null

        override suspend fun occurrences(activationId: Long): List<WorkoutOccurrence> = emptyList()

        override suspend fun getOccurrence(id: Long): WorkoutOccurrence? = null

        override suspend fun acceptAndActivate(
            acceptedPlan: AcceptedPlan?,
            activation: TrainingActivation,
            scheduledEpochDays: List<Long?>,
            replaceActive: Boolean
        ): Long = 0L

        override suspend fun resolveOccurrence(
            occurrenceId: Long,
            expectedRevision: Int,
            status: com.hydrafit.app.core.domain.schedule.OccurrenceStatus,
            resolvedAtMillis: Long,
            entries: List<com.hydrafit.app.core.domain.schedule.OccurrenceEntry>
        ): WorkoutScheduleState = WorkoutScheduleState()

        override suspend fun updateActivationHeader(activation: TrainingActivation) = Unit

        override suspend fun updateOccurrence(occurrence: WorkoutOccurrence) = Unit

        override suspend fun replaceOccurrenceEntries(
            occurrenceId: Long,
            entries: List<com.hydrafit.app.core.domain.schedule.OccurrenceEntry>
        ) = Unit

        override suspend fun deleteOccurrencesForActivation(activationId: Long) = Unit

        override suspend fun isTemplateReferenced(templateId: Long): Boolean = false
    }
}

private fun emptyBackupFile(appVersion: String, exportedAtMillis: Long): BackupFile = BackupFile(
    format = BACKUP_FORMAT,
    formatVersion = BACKUP_FORMAT_VERSION,
    appVersion = appVersion,
    exportedAtMillis = exportedAtMillis,
    catalog = BackupCatalogManifest(emptyList(), seedProfiles = emptyList()),
    settings = null,
    customExercises = emptyList(),
    exerciseOverrides = emptyList(),
    equipment = emptyList(),
    selectedEquipment = emptyList(),
    workoutSets = emptyList(),
    workoutSessions = emptyList(),
    plans = emptyList(),
    planDays = emptyList(),
    planEntries = emptyList(),
    volumeExplanations = emptyList(),
    volumeExplanationStates = emptyList(),
    routines = emptyList(),
    routineWorkouts = emptyList(),
    routineEntries = emptyList(),
    activations = emptyList(),
    activationWorkouts = emptyList(),
    activationEntries = emptyList(),
    occurrences = emptyList(),
    occurrenceEntries = emptyList(),
    scheduleState = null,
    personalRecords = emptyList(),
    preferences = emptyList(),
    exclusions = emptyList()
)
