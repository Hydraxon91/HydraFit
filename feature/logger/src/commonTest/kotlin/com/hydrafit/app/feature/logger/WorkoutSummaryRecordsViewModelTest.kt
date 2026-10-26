package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.StationAttribution
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.workout.ExerciseHistoryPage
import com.hydrafit.app.core.domain.workout.ExerciseHistoryRequest
import com.hydrafit.app.core.domain.workout.ExerciseProgress
import com.hydrafit.app.core.domain.workout.ExerciseProgressRepository
import com.hydrafit.app.core.domain.workout.ExerciseProgressRequest
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.ObserveOccurrenceRecordsUseCase
import com.hydrafit.app.core.domain.workout.ProgressRecord
import com.hydrafit.app.core.domain.workout.ProgressRecordEvent
import com.hydrafit.app.core.domain.workout.ProgressRecordKey
import com.hydrafit.app.core.domain.workout.ProgressRecordType
import com.hydrafit.app.core.domain.workout.WorkoutHistorySet
import com.hydrafit.app.core.userdata.settings.WeightUnitRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * The summary records ViewModel observes improvements read-only: opening, switching and clearing an
 * occurrence resets or replaces state, retry/resume re-samples the clock, the display unit tracks the
 * weight-unit flow, and a read failure is isolated to the section without any write mechanism.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutSummaryRecordsViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun resolvesEffectiveNameAndTracksTheDisplayUnit() = runTest(dispatcher) {
        val repository = FakeProgressRepository()
        repository.progress.value = mapOf("squat" to progress(loadEvent(exerciseId = "squat")))
        val unitRepository = FakeWeightUnitRepository()
        val viewModel = viewModel(repository, unitRepository = unitRepository)
        advanceUntilIdle()

        viewModel.onOccurrenceChanged(1L)
        repository.ids.value = listOf("squat")
        advanceUntilIdle()

        val row = viewModel.state.value.rows.single()
        assertEquals("Barbell Squat", row.exerciseName)
        assertEquals(ProgressRecordType.LOAD_AT_REPS, row.type)
        assertEquals(100.0, row.value)
        assertEquals(WeightUnit.KG, viewModel.state.value.unit)

        unitRepository.unit.value = WeightUnit.LB
        advanceUntilIdle()
        assertEquals(WeightUnit.LB, viewModel.state.value.unit)
    }

    @Test
    fun switchingOccurrenceReplacesStaleResultsAndClearingResets() = runTest(dispatcher) {
        val repository = FakeProgressRepository()
        repository.progress.value = mapOf("squat" to progress(loadEvent(exerciseId = "squat")))
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        viewModel.onOccurrenceChanged(1L)
        repository.ids.value = listOf("squat")
        advanceUntilIdle()
        assertEquals(
            "squat",
            viewModel.state.value.rows.single().exerciseId
        )

        // A new occurrence with a different improvement must not retain the previous row.
        repository.progress.value =
            mapOf("press" to progress(loadEvent(exerciseId = "press", setId = 9)))
        viewModel.onOccurrenceChanged(2L)
        repository.ids.value = listOf("press")
        advanceUntilIdle()
        assertEquals(
            "press",
            viewModel.state.value.rows.single().exerciseId
        )

        viewModel.onOccurrenceChanged(null)
        advanceUntilIdle()
        assertEquals(WorkoutSummaryRecordsScreen.Hidden, viewModel.state.value.screen)
    }

    @Test
    fun emptyOccurrenceIsDistinctFromLoading() = runTest(dispatcher) {
        val repository = FakeProgressRepository()
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onOccurrenceChanged(1L)
        repository.ids.value = listOf("squat")
        advanceUntilIdle()

        assertEquals(WorkoutSummaryRecordsScreen.Empty, viewModel.state.value.screen)
    }

    @Test
    fun failureIsIsolatedAndRetryResamplesTheClock() = runTest(dispatcher) {
        val repository = FakeProgressRepository()
        repository.progress.value = mapOf("squat" to progress(loadEvent(exerciseId = "squat")))
        val clock = FakeTimeProvider(now = 1_000L)
        val viewModel = viewModel(repository, timeProvider = clock)
        advanceUntilIdle()

        repository.fail = true
        viewModel.onOccurrenceChanged(1L)
        repository.ids.value = listOf("squat")
        advanceUntilIdle()
        assertEquals(WorkoutSummaryRecordsScreen.Failed, viewModel.state.value.screen)

        repository.fail = false
        clock.now = 5_000L
        viewModel.onRetry()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.screen is WorkoutSummaryRecordsScreen.Ready)
        assertEquals(5_000L, repository.requests.last().nowMillis)

        clock.now = 9_000L
        viewModel.onResume()
        advanceUntilIdle()
        assertEquals(9_000L, repository.requests.last().nowMillis)
    }

    @Test
    fun neverReadsTheFullLog() = runTest(dispatcher) {
        val repository = FakeProgressRepository()
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        viewModel.onOccurrenceChanged(1L)
        repository.ids.value = listOf("squat")
        advanceUntilIdle()

        assertEquals(0, repository.historyCalls)
    }

    private fun viewModel(
        repository: FakeProgressRepository,
        unitRepository: WeightUnitRepository = FakeWeightUnitRepository(),
        timeProvider: TimeProvider = FakeTimeProvider()
    ) = WorkoutSummaryRecordsViewModel(
        observeOccurrenceRecords = ObserveOccurrenceRecordsUseCase(repository),
        exerciseCatalog = FakeExerciseCatalog(),
        weightUnitRepository = unitRepository,
        timeProvider = timeProvider
    )

    private fun loadEvent(exerciseId: String, setId: Long = 7L) = ProgressRecordEvent(
        setId = setId,
        occurrenceId = 1L,
        record = ProgressRecord(
            key = ProgressRecordKey(ProgressRecordType.LOAD_AT_REPS, LoadKind.EXTERNAL, reps = 5),
            value = 100.0,
            setIds = listOf(setId)
        )
    )

    private fun progress(vararg events: ProgressRecordEvent) =
        ExerciseProgress(emptyList(), emptyList(), events.toList())
}

/** The ready rows, for a test that has already asserted the ready state. */
private val WorkoutSummaryRecordsUiState.rows: List<SummaryRecordRow>
    get() = (screen as WorkoutSummaryRecordsScreen.Ready).records

private class FakeProgressRepository : ExerciseProgressRepository {
    val ids = MutableStateFlow<List<String>>(emptyList())
    val progress = MutableStateFlow<Map<String, ExerciseProgress>>(emptyMap())
    val requests = mutableListOf<ExerciseProgressRequest>()
    var fail = false
    var historyCalls = 0

    override fun observeOccurrenceExerciseIds(occurrenceId: Long): Flow<List<String>> = ids

    override fun observeStationOptions(exerciseId: String): Flow<List<StationAttribution>> =
        flowOf(emptyList())

    override fun observeProgress(request: ExerciseProgressRequest): Flow<ExerciseProgress> {
        requests += request
        return if (fail) {
            flow { throw IllegalStateException("read failed") }
        } else {
            progress.map {
                it[request.exerciseId]
                    ?: ExerciseProgress(emptyList(), emptyList(), emptyList())
            }
        }
    }

    override fun observeHistory(request: ExerciseHistoryRequest): Flow<ExerciseHistoryPage> {
        historyCalls++
        return flowOf(ExerciseHistoryPage(emptyList(), hasMore = false))
    }

    override fun observeSet(exerciseId: String, setId: Long): Flow<WorkoutHistorySet?> =
        flowOf<WorkoutHistorySet?>(null)
}

private class FakeExerciseCatalog : ExerciseCatalog {
    override suspend fun all(): List<Exercise> = CATALOG

    override fun observeAll(): Flow<List<Exercise>> = flowOf(CATALOG)

    private companion object {
        val CATALOG =
            listOf(
                Exercise(
                    id = "squat",
                    name = "Barbell Squat",
                    requiredEquipment = emptySet(),
                    primaryMuscles = emptySet()
                )
            )
    }
}

private class FakeWeightUnitRepository : WeightUnitRepository {
    val unit = MutableStateFlow(WeightUnit.KG)

    override suspend fun selectedUnit(): WeightUnit = unit.value

    override fun unitFlow(): Flow<WeightUnit> = unit

    override suspend fun setUnit(unit: WeightUnit) {
        this.unit.value = unit
    }
}

private class FakeTimeProvider(var now: Long = 0L, private val offset: Long = 0L) : TimeProvider {
    override fun nowMillis(): Long = now

    override fun utcOffsetMillis(): Long = offset
}
