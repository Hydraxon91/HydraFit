package com.hydrafit.app.feature.progress

import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.time.localEpochDay
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.workout.ExerciseHistoryPage
import com.hydrafit.app.core.domain.workout.ExerciseHistoryRequest
import com.hydrafit.app.core.domain.workout.ExerciseProgress
import com.hydrafit.app.core.domain.workout.ExerciseProgressRepository
import com.hydrafit.app.core.domain.workout.ExerciseProgressRequest
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.ProgressDay
import com.hydrafit.app.core.domain.workout.ProgressRecordKey
import com.hydrafit.app.core.domain.workout.ProgressRecordType
import com.hydrafit.app.core.domain.workout.WorkoutHistorySet
import com.hydrafit.app.core.domain.workout.WorkoutTimingProvenance
import com.hydrafit.app.core.userdata.settings.WeightUnitRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressViewModelTest {

    private val nowMillis = 1_000_000_000_000L
    private val offsetMillis = 3_600_000L

    private class FakeProgressRepository : ExerciseProgressRepository {
        val progressRequests = mutableListOf<ExerciseProgressRequest>()
        val historyRequests = mutableListOf<ExerciseHistoryRequest>()
        var progressFlow: (ExerciseProgressRequest) -> Flow<ExerciseProgress> =
            { flowOf(ExerciseProgress(emptyList(), emptyList(), emptyList())) }
        var historyFlow: (ExerciseHistoryRequest) -> Flow<ExerciseHistoryPage> =
            { flowOf(ExerciseHistoryPage(emptyList(), hasMore = false)) }
        var setFlow: (String, Long) -> Flow<WorkoutHistorySet?> = { _, _ -> flowOf(null) }

        override fun observeHistory(request: ExerciseHistoryRequest): Flow<ExerciseHistoryPage> {
            historyRequests += request
            return historyFlow(request)
        }

        override fun observeProgress(request: ExerciseProgressRequest): Flow<ExerciseProgress> {
            progressRequests += request
            return progressFlow(request)
        }

        override fun observeSet(exerciseId: String, setId: Long): Flow<WorkoutHistorySet?> =
            setFlow(exerciseId, setId)

        override fun observeOccurrenceExerciseIds(occurrenceId: Long): Flow<List<String>> =
            flowOf(emptyList())
    }

    private class FakeCatalog(private val exercises: List<Exercise>) : ExerciseCatalog {
        override suspend fun all(): List<Exercise> = exercises
    }

    private class FakeWeightUnitRepository(initial: WeightUnit = WeightUnit.KG) :
        WeightUnitRepository {
        val units = MutableStateFlow(initial)
        override suspend fun selectedUnit(): WeightUnit = units.value
        override fun unitFlow(): Flow<WeightUnit> = units
        override suspend fun setUnit(unit: WeightUnit) {
            units.value = unit
        }
    }

    /** Mutable clock, so a test can advance the instant between reads. */
    private class FakeTimeProvider(var now: Long, var offset: Long) : TimeProvider {
        override fun nowMillis(): Long = now

        override fun utcOffsetMillis(): Long = offset
    }

    private fun exercise(id: String, name: String) = Exercise(
        id = id,
        name = name,
        requiredEquipment = emptySet(),
        primaryMuscles = emptySet()
    )

    private fun viewModel(
        repository: FakeProgressRepository = FakeProgressRepository(),
        unitRepository: FakeWeightUnitRepository = FakeWeightUnitRepository(),
        catalog: ExerciseCatalog = FakeCatalog(listOf(exercise("squat", "Squat"))),
        timeProvider: TimeProvider = FakeTimeProvider(nowMillis, offsetMillis)
    ) = ProgressViewModel(
        progressRepository = repository,
        exerciseCatalog = catalog,
        weightUnitRepository = unitRepository,
        timeProvider = timeProvider
    )

    @Test
    fun selectingAnExerciseRequestsInclusiveTrailingRangeAtTheCurrentOffset() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val repository = FakeProgressRepository()
            val vm = viewModel(repository)
            advanceUntilIdle()
            vm.onExerciseSelected("squat")
            advanceUntilIdle()

            val request = repository.progressRequests.last()
            val expectedDayTo = localEpochDay(nowMillis, offsetMillis)
            assertEquals(expectedDayTo, request.dayTo)
            assertEquals(expectedDayTo - 27L, request.dayFrom)
            assertEquals(nowMillis, request.nowMillis)
            assertEquals(offsetMillis, request.utcOffsetMillis)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun rangeChangeOnlyWidensTheChartWindowAndKeepsHistoryAllTime() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val repository = FakeProgressRepository()
            val vm = viewModel(repository)
            advanceUntilIdle()
            vm.onExerciseSelected("squat")
            advanceUntilIdle()
            val historyCountBefore = repository.historyRequests.size

            vm.onRangeSelected(ProgressRange.DAYS_365)
            advanceUntilIdle()

            val request = repository.progressRequests.last()
            assertEquals(localEpochDay(nowMillis, offsetMillis) - 364L, request.dayFrom)
            // History is all-time: a range change does not re-issue the history read.
            assertEquals(historyCountBefore, repository.historyRequests.size)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun aNewExerciseCancelsThePreviousReadSoOnlyTheLatestPublishes() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val repository = FakeProgressRepository()
            repository.progressFlow = { request ->
                flow {
                    val day =
                        ProgressDay(request.dayFrom, 1, 1L, null, emptyList(), emptyList(), null)
                    emit(ExerciseProgress(listOf(day), emptyList(), emptyList()))
                    awaitCancellation()
                }
            }
            val vm = viewModel(
                repository,
                catalog = FakeCatalog(
                    listOf(exercise("squat", "Squat"), exercise("bench", "Bench"))
                )
            )
            advanceUntilIdle()
            vm.onExerciseSelected("squat")
            advanceUntilIdle()
            val squatFrom = repository.progressRequests.last().dayFrom
            assertEquals(squatFrom, vm.state.value.days.single().localEpochDay)

            vm.onExerciseSelected("bench")
            advanceUntilIdle()

            assertEquals("bench", vm.state.value.selectedExerciseId)
            // The new exercise starts a fresh read; the cancelled one publishes nothing after this.
            assertEquals(2, repository.progressRequests.size)
            assertEquals(
                repository.progressRequests.last().dayFrom,
                vm.state.value.days.single().localEpochDay
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun chartsEmptyIsIndependentOfRecordedHistoryPresence() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val repository = FakeProgressRepository()
            repository.progressFlow =
                { flowOf(ExerciseProgress(emptyList(), emptyList(), emptyList())) }
            repository.historyFlow = {
                flowOf(ExerciseHistoryPage(listOf(historySet(1L)), hasMore = false))
            }
            val vm = viewModel(repository)
            advanceUntilIdle()
            vm.onExerciseSelected("squat")
            advanceUntilIdle()

            assertTrue(vm.state.value.chartsHaveNoEligibleDays)
            assertFalse(vm.state.value.historyIsEmpty)
            assertEquals(1, vm.state.value.historySets.size)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun retryReissuesTheChartReadAndKeepsTheSelectedExercise() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val repository = FakeProgressRepository()
            val vm = viewModel(repository)
            advanceUntilIdle()
            vm.onExerciseSelected("squat")
            advanceUntilIdle()
            val before = repository.progressRequests.size

            vm.onRetryCharts()
            advanceUntilIdle()

            assertEquals(before + 1, repository.progressRequests.size)
            assertEquals("squat", repository.progressRequests.last().exerciseId)
            assertEquals("squat", vm.state.value.selectedExerciseId)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun aMissingSelectedExerciseKeepsItsIdentityAndShowsTheMissingNameState() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val repository = FakeProgressRepository()
            val catalog =
                FakeCatalog(listOf(exercise("squat", "Squat"), exercise("bench", "Bench")))
            val vm = viewModel(repository, catalog = catalog)
            advanceUntilIdle()
            vm.onExerciseSelected("squat")
            advanceUntilIdle()
            assertEquals("Squat", vm.state.value.selectedExerciseName)
            assertFalse(vm.state.value.selectedExerciseNameMissing)

            // The catalog drops the exercise; the stable id is preserved, not cleared.
            vm.onExerciseSelected("bench")
            advanceUntilIdle()
            assertEquals("bench", vm.state.value.selectedExerciseId)
            assertEquals("Bench", vm.state.value.selectedExerciseName)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun selectingASupportingSetReadsOnlyThatExerciseScopedId() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val repository = FakeProgressRepository()
            val requested = mutableListOf<Pair<String, Long>>()
            repository.setFlow = { exerciseId, setId ->
                requested += exerciseId to setId
                flowOf(historySet(setId))
            }
            val vm = viewModel(repository)
            advanceUntilIdle()
            vm.onExerciseSelected("squat")
            advanceUntilIdle()

            vm.onShowSupportingSet(loadKey(), 42L)
            advanceUntilIdle()

            assertEquals(listOf("squat" to 42L), requested)
            assertEquals(42L, vm.state.value.supportingSet?.id)
            assertEquals(loadKey(), vm.state.value.selectedRecordKey)
            assertFalse(vm.state.value.supportingSetMissing)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun reTappingTheSameSetUnderAnotherRecordKeyMovesPlacementWithoutReReading() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val repository = FakeProgressRepository()
            val requested = mutableListOf<Pair<String, Long>>()
            repository.setFlow = { exerciseId, setId ->
                requested += exerciseId to setId
                flowOf(historySet(setId))
            }
            val vm = viewModel(repository)
            advanceUntilIdle()
            vm.onExerciseSelected("squat")
            advanceUntilIdle()

            vm.onShowSupportingSet(loadKey(), 42L)
            advanceUntilIdle()
            assertEquals(1, requested.size)
            assertEquals(loadKey(), vm.state.value.selectedRecordKey)

            // The same set holds another record: only the placement moves, no second read.
            vm.onShowSupportingSet(estimateKey(), 42L)
            advanceUntilIdle()
            assertEquals(estimateKey(), vm.state.value.selectedRecordKey)
            assertEquals(42L, vm.state.value.selectedSetId)
            assertEquals(42L, vm.state.value.supportingSet?.id)
            assertEquals(1, requested.size)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun resumeReSamplesTheClockAndMovesTheWindowToTheNewDay() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val repository = FakeProgressRepository()
            val clock = FakeTimeProvider(nowMillis, offsetMillis)
            val vm = viewModel(repository, timeProvider = clock)
            advanceUntilIdle()
            vm.onExerciseSelected("squat")
            advanceUntilIdle()
            val firstDayTo = repository.progressRequests.last().dayTo
            assertEquals(1, repository.progressRequests.size)

            // The next local day: a resume must re-issue the read at the new instant, not the old.
            clock.now = nowMillis + 24L * 60L * 60L * 1000L
            vm.onResume()
            advanceUntilIdle()

            val request = repository.progressRequests.last()
            assertEquals(2, repository.progressRequests.size)
            assertEquals(clock.now, request.nowMillis)
            assertEquals(firstDayTo + 1L, request.dayTo)
            assertEquals(firstDayTo + 1L - 27L, request.dayFrom)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun aLiveFirstPageEmissionCancelsAndInvalidatesAnInFlightAppendedPage() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val firstPage = MutableStateFlow(
                ExerciseHistoryPage(listOf(historySet(1L)), hasMore = true)
            )
            val pendingPages = mutableListOf<CompletableDeferred<ExerciseHistoryPage>>()
            val repository = FakeProgressRepository()
            repository.historyFlow = { request ->
                if (request.cursor == null) {
                    firstPage
                } else {
                    flow {
                        val gate = CompletableDeferred<ExerciseHistoryPage>()
                        pendingPages += gate
                        emit(gate.await())
                    }
                }
            }
            val vm = viewModel(repository)
            advanceUntilIdle()
            vm.onExerciseSelected("squat")
            advanceUntilIdle()
            assertEquals(listOf(1L), vm.state.value.historySets.map { it.id })

            vm.onLoadMoreHistory()
            advanceUntilIdle()
            assertTrue(vm.state.value.loadingMoreHistory)
            assertEquals(1, pendingPages.size)

            // A live first-page correction arrives while the appended page is suspended. This must
            // cancel the in-flight read and reset the paging context and its flags.
            firstPage.value = ExerciseHistoryPage(listOf(historySet(2L)), hasMore = true)
            advanceUntilIdle()
            assertEquals(listOf(2L), vm.state.value.historySets.map { it.id })
            assertFalse(vm.state.value.loadingMoreHistory)
            assertFalse(vm.state.value.loadMoreHistoryFailed)

            // The superseded page now completes; it must not append and must not resurrect flags.
            pendingPages.last().complete(
                ExerciseHistoryPage(listOf(historySet(9L)), hasMore = false)
            )
            advanceUntilIdle()
            assertEquals(listOf(2L), vm.state.value.historySets.map { it.id })
            assertFalse(vm.state.value.loadingMoreHistory)
            assertFalse(vm.state.value.loadMoreHistoryFailed)

            // The corrected first page's cursor still works: a following page appends normally.
            vm.onLoadMoreHistory()
            advanceUntilIdle()
            pendingPages.last().complete(
                ExerciseHistoryPage(listOf(historySet(3L)), hasMore = false)
            )
            advanceUntilIdle()
            assertEquals(listOf(2L, 3L), vm.state.value.historySets.map { it.id })
            assertFalse(vm.state.value.historyHasMore)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun switchingExerciseCancelsAndInvalidatesAnInFlightAppendedPage() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val squatFirst = MutableStateFlow(
                ExerciseHistoryPage(listOf(historySet(1L, "squat")), hasMore = true)
            )
            val benchFirst = MutableStateFlow(
                ExerciseHistoryPage(listOf(historySet(4L, "bench")), hasMore = true)
            )
            val pendingPages = mutableListOf<CompletableDeferred<ExerciseHistoryPage>>()
            val repository = FakeProgressRepository()
            repository.historyFlow = { request ->
                if (request.cursor == null) {
                    if (request.exerciseId == "squat") squatFirst else benchFirst
                } else {
                    flow {
                        val gate = CompletableDeferred<ExerciseHistoryPage>()
                        pendingPages += gate
                        emit(gate.await())
                    }
                }
            }
            val vm = viewModel(
                repository,
                catalog = FakeCatalog(
                    listOf(exercise("squat", "Squat"), exercise("bench", "Bench"))
                )
            )
            advanceUntilIdle()
            vm.onExerciseSelected("squat")
            advanceUntilIdle()
            vm.onLoadMoreHistory()
            advanceUntilIdle()
            assertEquals(1, pendingPages.size)

            // Switching exercise cancels the whole subscription and the suspended continuation.
            vm.onExerciseSelected("bench")
            advanceUntilIdle()
            pendingPages.last().complete(
                ExerciseHistoryPage(listOf(historySet(9L, "squat")), false)
            )
            advanceUntilIdle()
            assertEquals(listOf(4L), vm.state.value.historySets.map { it.id })
            assertFalse(vm.state.value.loadingMoreHistory)
            assertFalse(vm.state.value.loadMoreHistoryFailed)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun weightUnitPresentationFollowsTheRepositoryFlow() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val unitRepository = FakeWeightUnitRepository(WeightUnit.KG)
            val vm = viewModel(unitRepository = unitRepository)
            advanceUntilIdle()
            assertEquals(WeightUnit.KG, vm.state.value.weightUnit)
            unitRepository.units.value = WeightUnit.LB
            advanceUntilIdle()
            assertEquals(WeightUnit.LB, vm.state.value.weightUnit)
        } finally {
            Dispatchers.resetMain()
        }
    }
}

private fun historySet(setId: Long, exerciseId: String = "squat") = WorkoutHistorySet(
    id = setId,
    exerciseId = exerciseId,
    exerciseName = if (exerciseId == "squat") "Squat" else "Bench",
    reps = 5,
    weightKg = 100.0,
    loadKind = LoadKind.EXTERNAL,
    isWarmup = false,
    rir = null,
    performedAtMillis = 0L,
    timingProvenance = WorkoutTimingProvenance.LIVE
)

private fun loadKey() = ProgressRecordKey(
    type = ProgressRecordType.LOAD_AT_REPS,
    loadKind = LoadKind.EXTERNAL,
    reps = 5
)

private fun estimateKey() = ProgressRecordKey(
    type = ProgressRecordType.ESTIMATED_ONE_REP_MAX,
    loadKind = LoadKind.EXTERNAL
)
