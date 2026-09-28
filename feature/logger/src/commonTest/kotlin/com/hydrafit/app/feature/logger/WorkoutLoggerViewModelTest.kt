package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.engine.DeterministicWorkoutPlannerEngine
import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.GenerateWeeklySplitUseCase
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.engine.WorkoutPlannerEngineProvider
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.CalculateMuscleFatigueUseCase
import com.hydrafit.app.core.domain.fatigue.LoggedSet
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.workout.GetWorkoutLogUseCase
import com.hydrafit.app.core.domain.workout.LogWorkoutSetUseCase
import com.hydrafit.app.core.domain.workout.WorkoutLogRepository
import com.hydrafit.app.core.domain.workout.WorkoutSet
import com.hydrafit.app.core.userdata.equipment.EquipmentSelectionRepository
import com.hydrafit.app.core.userdata.settings.EnginePreferenceRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
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

    @Test
    fun prioritizesTodaysPlannedExercises() = runTest(dispatcher) {
        val viewModel = viewModel(
            timeMillis = 345_600_000L,
            availableEquipment = setOf(EquipmentTag.BARBELL),
            daysPerWeek = 3
        )
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(SplitFocus.FULL_BODY, state.todayFocus)
        assertEquals(
            listOf("Back Squat", "Bench Press", "Plank", "Dumbbell Curl"),
            state.exercises.map { it.name }
        )
    }

    @Test
    fun reprioritizesExercisesWhenEquipmentChanges() = runTest(dispatcher) {
        val equipment = FakeEquipmentSelectionRepository(emptySet())
        val viewModel = viewModel(equipmentRepository = equipment)
        advanceUntilIdle()

        assertEquals("Back Squat", viewModel.state.value.exercises.first().name)

        equipment.setSelected(setOf(EquipmentTag.BARBELL))
        advanceUntilIdle()

        assertEquals("Bench Press", viewModel.state.value.exercises.first().name)
    }

    private fun viewModel(
        repository: WorkoutLogRepository = FakeWorkoutLogRepository(),
        timeMillis: Long = 1_000L,
        availableEquipment: Set<EquipmentTag> = emptySet(),
        daysPerWeek: Int = 4,
        equipmentRepository: FakeEquipmentSelectionRepository =
            FakeEquipmentSelectionRepository(availableEquipment)
    ) = WorkoutLoggerViewModel(
        logWorkoutSet = LogWorkoutSetUseCase(repository),
        getWorkoutLog = GetWorkoutLogUseCase(repository),
        generateWeeklySplit = GenerateWeeklySplitUseCase(
            WorkoutPlannerEngineProvider { DeterministicWorkoutPlannerEngine(FakeExerciseCatalog) }
        ),
        equipmentSelectionRepository = equipmentRepository,
        enginePreference = FakeEnginePreferenceRepository(daysPerWeek),
        workoutLogRepository = repository,
        calculateMuscleFatigue = CalculateMuscleFatigueUseCase(),
        exerciseCatalog = FakeExerciseCatalog,
        timeProvider = TimeProvider { timeMillis }
    )

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

    private class FakeEquipmentSelectionRepository(private val selected: Set<EquipmentTag>) :
        EquipmentSelectionRepository {
        private val state = MutableStateFlow(selected)

        override suspend fun selected(): Set<EquipmentTag> = state.value

        override fun selectedFlow(): Flow<Set<EquipmentTag>> = state.asStateFlow()

        override suspend fun setSelected(tags: Set<EquipmentTag>) {
            state.value = tags
        }
    }

    private class FakeEnginePreferenceRepository(private val daysPerWeek: Int) :
        EnginePreferenceRepository {
        override suspend fun selectedEngine(): PlannerEngineId = PlannerEngineId.DETERMINISTIC

        override fun engineFlow(): Flow<PlannerEngineId> = flowOf(PlannerEngineId.DETERMINISTIC)

        override suspend fun setEngine(engine: PlannerEngineId) = Unit

        override suspend fun selectedDaysPerWeek(): Int = daysPerWeek

        override fun daysPerWeekFlow(): Flow<Int> = flowOf(daysPerWeek)

        override suspend fun setDaysPerWeek(daysPerWeek: Int) = Unit
    }

    private class FakeWorkoutLogRepository : WorkoutLogRepository {
        private val sets = mutableListOf<WorkoutSet>()

        override suspend fun add(set: WorkoutSet) {
            sets.add(set)
        }

        override suspend fun all(): List<WorkoutSet> = sets.toList()

        override suspend fun loggedSets(): List<LoggedSet> = emptyList()

        override fun loggedSetsFlow(): Flow<List<LoggedSet>> = flowOf(emptyList())

        override suspend fun clear() {
            sets.clear()
        }
    }
}
