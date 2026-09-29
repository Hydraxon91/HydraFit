package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.engine.AcceptedDay
import com.hydrafit.app.core.domain.engine.AcceptedExercise
import com.hydrafit.app.core.domain.engine.AcceptedPlan
import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.ObserveAcceptedPlanUseCase
import com.hydrafit.app.core.domain.engine.PlanHistoryRepository
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.LoggedSet
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.workout.GetWorkoutLogUseCase
import com.hydrafit.app.core.domain.workout.LogWorkoutSetUseCase
import com.hydrafit.app.core.domain.workout.WorkoutLogRepository
import com.hydrafit.app.core.domain.workout.WorkoutSet
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutLoggerViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun cannotLogUntilAnExerciseAndValidRepsAreProvided() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.canLog)

        viewModel.onExerciseSelected("back-squat")
        assertFalse(viewModel.state.value.canLog)

        viewModel.onRepsChanged("5")
        assertTrue(viewModel.state.value.canLog)
    }

    @Test
    fun loggingASetPersistsItAndShowsItInHistory() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        viewModel.onRepsChanged("5")
        viewModel.onWeightChanged("100")
        viewModel.onWarmupToggled(false)
        viewModel.log()
        advanceUntilIdle()

        assertEquals(1, repository.all().size)
        val row = viewModel.state.value.recentSets.single()
        assertEquals("Back Squat", row.exerciseName)
        assertEquals(5, row.reps)
        assertEquals(100.0, row.weightKg)
        assertFalse(row.isWarmup)
        assertEquals("", viewModel.state.value.reps)
        assertEquals("", viewModel.state.value.weightKg)
    }

    @Test
    fun marksWarmupSets() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onExerciseSelected("back-squat")
        viewModel.onRepsChanged("10")
        viewModel.onWarmupToggled(true)
        viewModel.log()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.recentSets.single().isWarmup)
    }

    @Test
    fun ignoresInvalidInput() = runTest(dispatcher) {
        val repository = FakeWorkoutLogRepository()
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onRepsChanged("0")
        viewModel.log()
        advanceUntilIdle()

        assertTrue(repository.all().isEmpty())
    }

    @Test
    fun showsNoTodayFocusWithoutAnAcceptedPlan() = runTest(dispatcher) {
        val viewModel = viewModel(timeMillis = MONDAY)
        advanceUntilIdle()

        assertEquals(null, viewModel.state.value.todayFocus)
        assertEquals(
            listOf("Back Squat", "Bench Press", "Dumbbell Curl", "Plank"),
            viewModel.state.value.exercises.map { it.name }
        )
    }

    @Test
    fun prioritizesTodaysExercisesFromTheAcceptedPlan() = runTest(dispatcher) {
        val viewModel = viewModel(
            timeMillis = MONDAY,
            acceptedPlan = acceptedPlan(dayZeroExerciseIds = listOf("plank", "back-squat"))
        )
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(SplitFocus.FULL_BODY, state.todayFocus)
        assertEquals(
            listOf("Plank", "Back Squat", "Bench Press", "Dumbbell Curl"),
            state.exercises.map { it.name }
        )
    }

    @Test
    fun appliesAcceptedPlanPriorityWhenCatalogLoadsAfterThePlan() = runTest(dispatcher) {
        val catalogReady = CompletableDeferred<Unit>()
        val catalog = object : ExerciseCatalog {
            override suspend fun all(): List<Exercise> {
                catalogReady.await()
                return FakeExerciseCatalog.all()
            }
        }
        val viewModel = viewModel(
            timeMillis = MONDAY,
            acceptedPlan = acceptedPlan(listOf("plank", "back-squat")),
            catalog = catalog
        )
        runCurrent()

        assertEquals(SplitFocus.FULL_BODY, viewModel.state.value.todayFocus)

        catalogReady.complete(Unit)
        advanceUntilIdle()

        assertEquals(
            listOf("Plank", "Back Squat", "Bench Press", "Dumbbell Curl"),
            viewModel.state.value.exercises.map { it.name }
        )
    }

    @Test
    fun reprioritizesWhenTheAcceptedPlanChanges() = runTest(dispatcher) {
        val history = FakePlanHistoryRepository(acceptedPlan(listOf("plank")))
        val viewModel = viewModel(timeMillis = MONDAY, history = history)
        advanceUntilIdle()

        assertEquals("Plank", viewModel.state.value.exercises.first().name)

        history.accepted = acceptedPlan(listOf("bench-press"))
        advanceUntilIdle()

        assertEquals("Bench Press", viewModel.state.value.exercises.first().name)
    }

    private fun viewModel(
        repository: WorkoutLogRepository = FakeWorkoutLogRepository(),
        timeMillis: Long = 1_000L,
        acceptedPlan: AcceptedPlan? = null,
        history: FakePlanHistoryRepository = FakePlanHistoryRepository(acceptedPlan),
        catalog: ExerciseCatalog = FakeExerciseCatalog
    ): WorkoutLoggerViewModel = WorkoutLoggerViewModel(
        logWorkoutSet = LogWorkoutSetUseCase(repository),
        getWorkoutLog = GetWorkoutLogUseCase(repository),
        observeAcceptedPlan = ObserveAcceptedPlanUseCase(history),
        exerciseCatalog = catalog,
        timeProvider = TimeProvider { timeMillis }
    )

    private fun acceptedPlan(dayZeroExerciseIds: List<String>) = AcceptedPlan(
        engine = PlannerEngineId.DETERMINISTIC,
        acceptedAtMillis = 0L,
        days = listOf(
            AcceptedDay(
                dayIndex = 0,
                focus = SplitFocus.FULL_BODY,
                exercises = dayZeroExerciseIds.map { id ->
                    AcceptedExercise(
                        exerciseId = id,
                        sets = 3,
                        reps = 8,
                        name = id,
                        movementPattern = MovementPattern.CORE
                    )
                }
            )
        )
    )

    private class FakePlanHistoryRepository(accepted: AcceptedPlan?) : PlanHistoryRepository {
        private val state = MutableStateFlow(accepted)

        var accepted: AcceptedPlan?
            get() = state.value
            set(value) {
                state.value = value
            }

        override fun observeLatest(): Flow<AcceptedPlan?> = state

        override suspend fun latest(): AcceptedPlan? = state.value

        override suspend fun accept(plan: AcceptedPlan) {
            state.value = plan
        }

        override suspend fun clear() {
            state.value = null
        }
    }

    private object FakeExerciseCatalog : ExerciseCatalog {
        override suspend fun all(): List<Exercise> = listOf(
            Exercise(
                id = "back-squat",
                name = "Back Squat",
                requiredEquipment = setOf(EquipmentTag.BARBELL),
                primaryMuscles = setOf(MuscleGroup.QUADS),
                movementPattern = MovementPattern.SQUAT
            ),
            Exercise(
                id = "bench-press",
                name = "Bench Press",
                requiredEquipment = setOf(EquipmentTag.BARBELL),
                primaryMuscles = setOf(MuscleGroup.CHEST),
                movementPattern = MovementPattern.HORIZONTAL_PUSH
            ),
            Exercise(
                id = "plank",
                name = "Plank",
                requiredEquipment = emptySet(),
                primaryMuscles = setOf(MuscleGroup.CORE),
                movementPattern = MovementPattern.CORE
            ),
            Exercise(
                id = "dumbbell-curl",
                name = "Dumbbell Curl",
                requiredEquipment = setOf(EquipmentTag.DUMBBELL),
                primaryMuscles = setOf(MuscleGroup.BICEPS),
                movementPattern = MovementPattern.BICEPS_ISOLATION
            )
        )
    }

    private class FakeWorkoutLogRepository : WorkoutLogRepository {
        private val sets = mutableListOf<WorkoutSet>()

        override suspend fun add(set: WorkoutSet) {
            sets.add(set)
        }

        override suspend fun all(): List<WorkoutSet> = sets.toList()

        override fun setsFlow(): Flow<List<WorkoutSet>> = flowOf(sets.toList())

        override suspend fun loggedSets(): List<LoggedSet> = emptyList()

        override fun loggedSetsFlow(): Flow<List<LoggedSet>> = flowOf(emptyList())

        override suspend fun clear() {
            sets.clear()
        }
    }

    private companion object {
        /** Epoch millis whose `dayOfWeek` is MONDAY, matching a plan's first day. */
        const val MONDAY = 4L * 24L * 60L * 60L * 1000L
    }
}
