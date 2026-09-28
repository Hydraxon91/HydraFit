package com.hydrafit.app.feature.splitbuilder

import com.hydrafit.app.core.domain.engine.DeterministicWorkoutPlannerEngine
import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.GenerateWeeklySplitUseCase
import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.engine.WorkoutPlannerEngineProvider
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.fatigue.CalculateMuscleFatigueUseCase
import com.hydrafit.app.core.domain.fatigue.LoggedSet
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.workout.WorkoutLogRepository
import com.hydrafit.app.core.domain.workout.WorkoutSet
import com.hydrafit.app.core.userdata.equipment.EquipmentSelectionRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class SplitBuilderViewModelTest {

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
    fun buildsPlanOnlyFromSelectedEquipment() = runTest(dispatcher) {
        val viewModel = viewModel(availableEquipment = setOf(EquipmentTag.DUMBBELL))
        advanceUntilIdle()

        val plan = viewModel.state.value.plan
        assertEquals(4, plan?.days?.size)
        val ids = plan!!.days.flatMap { day -> day.exercises.map { it.exerciseId } }.toSet()
        assertEquals(setOf("goblet-squat"), ids)
        assertEquals("Goblet Squat", viewModel.state.value.exerciseNames["goblet-squat"])
    }

    @Test
    fun changingDaysPerWeekRegeneratesThePlan() = runTest(dispatcher) {
        val viewModel = viewModel(availableEquipment = setOf(EquipmentTag.DUMBBELL))
        advanceUntilIdle()

        viewModel.onDaysPerWeekSelected(5)
        advanceUntilIdle()

        val focuses = viewModel.state.value.plan!!.days.map { it.focus }
        assertEquals(5, focuses.size)
        assertEquals(SplitFocus.PUSH, focuses.first())
        assertEquals(SplitFocus.PULL, focuses[1])
        assertEquals(SplitFocus.LEGS, focuses[2])
    }

    @Test
    fun producesDaysWithNoExercisesWhenNoEquipmentIsSelected() = runTest(dispatcher) {
        val viewModel = viewModel(availableEquipment = emptySet())
        advanceUntilIdle()

        val days = viewModel.state.value.plan!!.days
        assertEquals(4, days.size)
        assertTrue(days.all { it.exercises.isEmpty() })
    }

    private fun viewModel(availableEquipment: Set<EquipmentTag>): SplitBuilderViewModel {
        val catalog = FakeExerciseCatalog()
        return SplitBuilderViewModel(
            generateWeeklySplit = GenerateWeeklySplitUseCase(
                WorkoutPlannerEngineProvider { DeterministicWorkoutPlannerEngine(catalog) }
            ),
            equipmentSelectionRepository = FakeEquipmentSelectionRepository(availableEquipment),
            workoutLogRepository = FakeWorkoutLogRepository,
            calculateMuscleFatigue = CalculateMuscleFatigueUseCase(),
            exerciseCatalog = catalog,
            timeProvider = TimeProvider { 0L }
        )
    }

    private class FakeExerciseCatalog : ExerciseCatalog {
        private val exercises = listOf(
            Exercise(
                id = "goblet-squat",
                name = "Goblet Squat",
                requiredEquipment = setOf(EquipmentTag.DUMBBELL),
                primaryMuscles = setOf(MuscleGroup.QUADS)
            ),
            Exercise(
                id = "back-squat",
                name = "Back Squat",
                requiredEquipment = setOf(EquipmentTag.BARBELL),
                primaryMuscles = setOf(MuscleGroup.QUADS)
            )
        )

        override suspend fun all(): List<Exercise> = exercises
    }

    private class FakeEquipmentSelectionRepository(private val selected: Set<EquipmentTag>) :
        EquipmentSelectionRepository {
        override suspend fun selected(): Set<EquipmentTag> = selected

        override suspend fun setSelected(tags: Set<EquipmentTag>) = Unit
    }

    private object FakeWorkoutLogRepository : WorkoutLogRepository {
        override suspend fun add(set: WorkoutSet) = Unit

        override suspend fun all(): List<WorkoutSet> = emptyList()

        override suspend fun loggedSets(): List<LoggedSet> = emptyList()

        override suspend fun clear() = Unit
    }
}
