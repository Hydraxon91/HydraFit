package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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

    private fun viewModel(repository: WorkoutLogRepository = FakeWorkoutLogRepository()) =
        WorkoutLoggerViewModel(
            logWorkoutSet = LogWorkoutSetUseCase(repository),
            getWorkoutLog = GetWorkoutLogUseCase(repository),
            exerciseCatalog = FakeExerciseCatalog,
            timeProvider = TimeProvider { 1_000L }
        )

    private object FakeExerciseCatalog : ExerciseCatalog {
        override suspend fun all(): List<Exercise> = listOf(
            Exercise(
                id = "back-squat",
                name = "Back Squat",
                requiredEquipment = setOf(EquipmentTag.BARBELL),
                primaryMuscles = setOf(MuscleGroup.QUADS)
            ),
            Exercise(
                id = "bench-press",
                name = "Bench Press",
                requiredEquipment = setOf(EquipmentTag.BARBELL),
                primaryMuscles = setOf(MuscleGroup.CHEST)
            )
        )
    }

    private class FakeWorkoutLogRepository : WorkoutLogRepository {
        private val sets = mutableListOf<WorkoutSet>()

        override suspend fun add(set: WorkoutSet) {
            sets.add(set)
        }

        override suspend fun all(): List<WorkoutSet> = sets.toList()

        override suspend fun loggedSets(): List<LoggedSet> = emptyList()

        override suspend fun clear() {
            sets.clear()
        }
    }
}
