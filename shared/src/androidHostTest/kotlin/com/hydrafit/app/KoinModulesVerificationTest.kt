package com.hydrafit.app

import com.hydrafit.app.core.database.DatabaseDriverFactory
import com.hydrafit.app.core.database.databaseModule
import com.hydrafit.app.core.domain.engine.AcceptWeeklyPlanUseCase
import com.hydrafit.app.core.domain.engine.AcceptedPlan
import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.ObserveAcceptedPlanUseCase
import com.hydrafit.app.core.domain.engine.ObserveWorkoutPlanInputsUseCase
import com.hydrafit.app.core.domain.engine.OnDevicePlanProgress
import com.hydrafit.app.core.domain.engine.PlanBuilderActions
import com.hydrafit.app.core.domain.engine.PlanHistoryRepository
import com.hydrafit.app.core.domain.engine.SubstituteExerciseUseCase
import com.hydrafit.app.core.domain.engine.SuggestWeightsUseCase
import com.hydrafit.app.core.domain.engine.WorkoutPlanSources
import com.hydrafit.app.core.domain.engine.WorkoutPlanSourcesRepository
import com.hydrafit.app.core.domain.equipment.Exercise
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
import com.hydrafit.app.core.domain.workout.CorrectWorkoutSetTimeUseCase
import com.hydrafit.app.core.domain.workout.DeleteWorkoutSetUseCase
import com.hydrafit.app.core.domain.workout.EndWorkoutSessionUseCase
import com.hydrafit.app.core.domain.workout.GetWorkoutLogUseCase
import com.hydrafit.app.core.domain.workout.LogWorkoutSetUseCase
import com.hydrafit.app.core.domain.workout.ObserveOpenWorkoutSessionUseCase
import com.hydrafit.app.core.domain.workout.SessionResegmenter
import com.hydrafit.app.core.domain.workout.StartWorkoutSessionUseCase
import com.hydrafit.app.core.domain.workout.WorkoutLogRepository
import com.hydrafit.app.core.domain.workout.WorkoutSession
import com.hydrafit.app.core.domain.workout.WorkoutSessionRepository
import com.hydrafit.app.core.domain.workout.WorkoutSet
import com.hydrafit.app.core.llm.NoopOnDevicePlannerLogger
import com.hydrafit.app.core.llm.OnDevicePlannerLogger
import com.hydrafit.app.core.llm.OnDeviceTextGenerator
import com.hydrafit.app.core.network.ApiKeyProvider
import com.hydrafit.app.core.network.GeminiWorkoutPlannerEngine
import com.hydrafit.app.core.userdata.settings.ApiKeyStore
import com.hydrafit.app.feature.equipment.equipmentModule
import com.hydrafit.app.feature.fatigueheatmap.fatigueHeatmapModule
import com.hydrafit.app.feature.logger.loggerModule
import com.hydrafit.app.feature.routines.routinesModule
import com.hydrafit.app.feature.settings.settingsModule
import com.hydrafit.app.feature.splitbuilder.splitBuilderModule
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.verify.verify

@OptIn(KoinExperimentalAPI::class)
class KoinModulesVerificationTest {

    private val testPlatformModule = module {
        single<TimeProvider> { TimeProvider { 0L } }
        single<ApiKeyStore> { FakeApiKeyStore }
        single<ApiKeyProvider> { ApiKeyProvider { "test-key" } }
        single<OnDeviceTextGenerator> { FakeOnDeviceTextGenerator }
        single<OnDevicePlannerLogger> { NoopOnDevicePlannerLogger }
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
            assertNotNull(koin.get<ObserveWorkoutPlanInputsUseCase>())
            assertNotNull(koin.get<AcceptWeeklyPlanUseCase>())
            assertNotNull(koin.get<SubstituteExerciseUseCase>())
            assertNotNull(koin.get<PlanBuilderActions>())
            assertNotNull(koin.get<ObserveAcceptedPlanUseCase>())
            assertNotNull(koin.get<LogWorkoutSetUseCase>())
            assertNotNull(koin.get<GetWorkoutLogUseCase>())
            assertNotNull(koin.get<DeleteWorkoutSetUseCase>())
            assertNotNull(koin.get<CorrectWorkoutSetTimeUseCase>())
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

    private object FakeApiKeyStore : ApiKeyStore {
        override fun load(): String? = "test-key"

        override fun save(apiKey: String) = Unit

        override fun clear() = Unit
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
            newWeightKg: Double?
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
