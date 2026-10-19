package com.hydrafit.app.feature.routines

import com.hydrafit.app.core.domain.engine.AcceptedPlan
import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.ExerciseExclusion
import com.hydrafit.app.core.domain.fatigue.LoggedSet
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.routine.ArchiveRoutineTemplateUseCase
import com.hydrafit.app.core.domain.routine.DeleteRoutineTemplateUseCase
import com.hydrafit.app.core.domain.routine.DuplicateRoutineTemplateUseCase
import com.hydrafit.app.core.domain.routine.ObserveRoutineTemplatesUseCase
import com.hydrafit.app.core.domain.routine.RoutineTemplate
import com.hydrafit.app.core.domain.routine.RoutineTemplateActions
import com.hydrafit.app.core.domain.routine.RoutineTemplateRepository
import com.hydrafit.app.core.domain.routine.SaveRoutineTemplateUseCase
import com.hydrafit.app.core.domain.routine.Superset
import com.hydrafit.app.core.domain.schedule.ActivateRoutineUseCase
import com.hydrafit.app.core.domain.schedule.CancelTrainingActivationUseCase
import com.hydrafit.app.core.domain.schedule.CreateTrainingActivationUseCase
import com.hydrafit.app.core.domain.schedule.EditUnstartedOccurrenceUseCase
import com.hydrafit.app.core.domain.schedule.FinishTrainingBlockUseCase
import com.hydrafit.app.core.domain.schedule.MoveWorkoutOccurrenceUseCase
import com.hydrafit.app.core.domain.schedule.OccurrenceEntry
import com.hydrafit.app.core.domain.schedule.OccurrenceStatus
import com.hydrafit.app.core.domain.schedule.PreviewWorkoutScheduleUseCase
import com.hydrafit.app.core.domain.schedule.RepeatTrainingBlockUseCase
import com.hydrafit.app.core.domain.schedule.SelectWorkoutOccurrenceUseCase
import com.hydrafit.app.core.domain.schedule.StartWorkoutOccurrenceUseCase
import com.hydrafit.app.core.domain.schedule.SwitchScheduleModeUseCase
import com.hydrafit.app.core.domain.schedule.TrainingActivation
import com.hydrafit.app.core.domain.schedule.WorkoutOccurrence
import com.hydrafit.app.core.domain.schedule.WorkoutScheduleActions
import com.hydrafit.app.core.domain.schedule.WorkoutScheduleRepository
import com.hydrafit.app.core.domain.schedule.WorkoutScheduleState
import com.hydrafit.app.core.domain.time.DayOfWeek
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.workout.WorkoutLogRepository
import com.hydrafit.app.core.domain.workout.WorkoutSet
import com.hydrafit.app.core.userdata.equipment.ExerciseExclusionRepository
import com.hydrafit.app.core.userdata.settings.PreferredWeekdaysRepository
import com.hydrafit.app.core.userdata.settings.WeightUnitRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * The superset authoring surface: pairing two adjacent slots through the editor and the guarantees
 * that survive it (one identical group on both slots, prescriptions untouched, both slots cleared on
 * removal, and no pair without a valid next slot or valid rest values).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RoutinesSupersetTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var routines: TestRoutineTemplateRepository
    private lateinit var schedule: TestWorkoutScheduleRepository
    private val catalog = TestCatalog()
    private val timeProvider = TimeProvider { 0L }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        routines = TestRoutineTemplateRepository()
        schedule = TestWorkoutScheduleRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun pairingTwoAdjacentSlotsStoresOneIdenticalGroupOnBothAndKeepsTheirPrescriptions() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()
            openRoutineWithSlots(viewModel, "bench-press", "overhead-press")
            setProgressionPolicy(viewModel)
            setOrderedTargets(viewModel)

            viewModel.onEditSuperset(0, 0)
            viewModel.onSupersetTransitionChanged("30")
            viewModel.onSupersetPairRestChanged("90")
            viewModel.onSupersetApply()

            assertNull(viewModel.state.value.supersetEditor)
            val entries = editorEntries(viewModel)
            val pair = assertNotNull(entries[0].superset)
            assertEquals(pair, entries[1].superset)
            assertEquals(30, pair.transitionSeconds)
            assertEquals(90, pair.pairRestSeconds)
            // Pairing rewrites nothing else about either slot.
            assertEquals(5, assertNotNull(entries[0].progressionPolicy).minReps)
            assertEquals(3, assertNotNull(entries[1].setTargets).sets)

            viewModel.onSaveEditor()
            advanceUntilIdle()

            // A real save proves the group passes RoutineTemplateValidator's superset rule.
            assertNull(viewModel.state.value.editor)
            val saved = viewModel.state.value.templates.single().workouts.single().entries
            assertEquals(assertNotNull(saved[0].superset), saved[1].superset)
            assertEquals(30, assertNotNull(saved[0].superset).transitionSeconds)
            assertEquals(90, assertNotNull(saved[0].superset).pairRestSeconds)
            assertEquals(5, assertNotNull(saved[0].progressionPolicy).minReps)
            assertEquals(3, assertNotNull(saved[1].setTargets).sets)
        }

    @Test
    fun reopeningASavedPairKeepsItAndResavingDoesNotDeleteIt() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        openRoutineWithSlots(viewModel, "bench-press", "overhead-press")
        pairFirstTwoSlots(viewModel)
        viewModel.onSaveEditor()
        advanceUntilIdle()
        val savedTemplate = viewModel.state.value.templates.single()
        val savedPair = assertNotNull(savedTemplate.workouts.single().entries[0].superset)

        viewModel.onEditRoutine(savedTemplate)
        advanceUntilIdle()

        // SS-01 regression: loading the saved routine must show the stored group, not an empty editor.
        val reopened = editorEntries(viewModel)
        assertEquals(savedPair, reopened[0].superset)
        assertEquals(savedPair, reopened[1].superset)

        viewModel.onSaveEditor()
        advanceUntilIdle()

        val resaved = viewModel.state.value.templates.single().workouts.single().entries
        assertEquals(savedPair, resaved[0].superset)
        assertEquals(savedPair, resaved[1].superset)
    }

    @Test
    fun removingAPairClearsBothOfItsSlots() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        openRoutineWithSlots(viewModel, "bench-press", "overhead-press")
        pairFirstTwoSlots(viewModel)

        // Removing from the second member clears the pair on both, like removing from the first.
        viewModel.onRemoveSuperset(0, 1)

        val entries = editorEntries(viewModel)
        assertNull(entries[0].superset)
        assertNull(entries[1].superset)
    }

    @Test
    fun aSlotWithNoNextSlotCannotBePaired() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        openRoutineWithSlots(viewModel, "bench-press", "overhead-press")

        // The last slot has nothing after it, so the editor never opens a pair for it.
        viewModel.onEditSuperset(0, 1)

        assertNull(viewModel.state.value.supersetEditor)
        assertNull(editorEntries(viewModel)[1].superset)
    }

    @Test
    fun anInvalidRestValueIsRejectedWithTheSpecificFieldError() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        openRoutineWithSlots(viewModel, "bench-press", "overhead-press")

        viewModel.onEditSuperset(0, 0)
        viewModel.onSupersetTransitionChanged("abc")
        viewModel.onSupersetApply()
        assertEquals(
            SupersetError.TRANSITION_RANGE,
            assertNotNull(viewModel.state.value.supersetEditor).error
        )

        viewModel.onSupersetTransitionChanged("-1")
        viewModel.onSupersetApply()
        assertEquals(
            SupersetError.TRANSITION_RANGE,
            assertNotNull(viewModel.state.value.supersetEditor).error
        )

        viewModel.onSupersetTransitionChanged("0")
        viewModel.onSupersetPairRestChanged("0")
        viewModel.onSupersetApply()
        assertEquals(
            SupersetError.PAIR_REST_RANGE,
            assertNotNull(viewModel.state.value.supersetEditor).error
        )

        viewModel.onSupersetPairRestChanged("")
        viewModel.onSupersetApply()
        assertEquals(
            SupersetError.PAIR_REST_RANGE,
            assertNotNull(viewModel.state.value.supersetEditor).error
        )

        viewModel.onSupersetPairRestChanged((Superset.MAX_REST_SECONDS + 1).toString())
        viewModel.onSupersetApply()
        assertEquals(
            SupersetError.PAIR_REST_RANGE,
            assertNotNull(viewModel.state.value.supersetEditor).error
        )

        // Nothing was written while the pair was invalid.
        assertNull(editorEntries(viewModel)[0].superset)
        assertNull(editorEntries(viewModel)[1].superset)
    }

    @Test
    fun aPairOfTheSameExerciseIsRejectedWithItsOwnError() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        openRoutineWithSlots(viewModel, "bench-press", "bench-press")

        viewModel.onEditSuperset(0, 0)
        viewModel.onSupersetApply()

        assertEquals(
            SupersetError.SAME_EXERCISE,
            assertNotNull(viewModel.state.value.supersetEditor).error
        )
        assertNull(editorEntries(viewModel)[0].superset)
    }

    @Test
    fun deletingAPairedSlotClearsItsPartner() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        openRoutineWithSlots(viewModel, "bench-press", "overhead-press")
        pairFirstTwoSlots(viewModel)

        viewModel.onRemoveEntry(0, 1)

        val entries = editorEntries(viewModel)
        assertEquals(1, entries.size)
        assertNull(entries[0].superset)
    }

    @Test
    fun aMoveThatWouldSplitAPairIsRefused() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        openRoutineWithSlots(viewModel, "bench-press", "overhead-press", "barbell-squat")
        pairFirstTwoSlots(viewModel)

        // Moving the second member down would leave the pair at slots 0 and 2; the move is refused.
        viewModel.onMoveEntry(0, 1, 1)

        assertEquals(
            listOf("bench-press", "overhead-press", "barbell-squat"),
            editorEntries(viewModel).map { it.exerciseId }
        )
        val entries = editorEntries(viewModel)
        assertEquals(assertNotNull(entries[0].superset), entries[1].superset)

        // A move that keeps the pair adjacent is still allowed.
        viewModel.onMoveEntry(0, 0, 1)

        assertEquals(
            listOf("overhead-press", "bench-press", "barbell-squat"),
            editorEntries(viewModel).map { it.exerciseId }
        )
        val reordered = editorEntries(viewModel)
        assertEquals(assertNotNull(reordered[0].superset), reordered[1].superset)
    }

    @Test
    fun aMoveDirectionThatWouldSplitAPairIsDisabledWhileAnAdjacentMoveStaysEnabled() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            advanceUntilIdle()
            openRoutineWithSlots(viewModel, "bench-press", "overhead-press", "barbell-squat")
            pairFirstTwoSlots(viewModel)

            val workout = requireNotNull(viewModel.state.value.editor).workouts.single()
            // The second member cannot move down: that would leave the pair at slots 0 and 2.
            assertFalse(workout.canMoveEntry(entryIndex = 1, delta = 1))
            // The same member can move up: the pair stays adjacent, just in the other order.
            assertTrue(workout.canMoveEntry(entryIndex = 1, delta = -1))
            // The first member can move down for the same reason; its up direction is out of range.
            assertTrue(workout.canMoveEntry(entryIndex = 0, delta = 1))
            assertFalse(workout.canMoveEntry(entryIndex = 0, delta = -1))
        }

    private fun openRoutineWithSlots(viewModel: RoutinesViewModel, vararg exerciseIds: String) {
        viewModel.onNewRoutine()
        viewModel.onEditorNameChanged("Upper")
        viewModel.onAddWorkout()
        viewModel.onWorkoutNameChanged(0, "Day 1")
        exerciseIds.forEach { id ->
            viewModel.onAddExercise(0)
            viewModel.onPickerExerciseSelected(id, id)
        }
    }

    private fun setProgressionPolicy(viewModel: RoutinesViewModel) {
        viewModel.onEditProgressionPolicy(0, 0)
        viewModel.onProgressionPolicyMinRepsChanged("5")
        viewModel.onProgressionPolicyMaxRepsChanged("8")
        viewModel.onProgressionPolicyIncrementChanged("2.5")
        viewModel.onProgressionPolicySave()
    }

    private fun setOrderedTargets(viewModel: RoutinesViewModel) {
        viewModel.onEditSetTargets(0, 1)
        viewModel.onSetTargetsSetsChanged("3")
        viewModel.onSetTargetsStartRepsChanged("8")
        viewModel.onSetTargetsEndRepsChanged("8")
        viewModel.onSetTargetsStartLoadChanged("60")
        viewModel.onSetTargetsEndLoadChanged("60")
        viewModel.onSetTargetsApply()
    }

    private fun pairFirstTwoSlots(viewModel: RoutinesViewModel) {
        viewModel.onEditSuperset(0, 0)
        viewModel.onSupersetTransitionChanged("30")
        viewModel.onSupersetPairRestChanged("90")
        viewModel.onSupersetApply()
    }

    private fun editorEntries(viewModel: RoutinesViewModel): List<EditorEntry> = requireNotNull(
        viewModel.state.value.editor
    ).workouts.single().entries

    private fun viewModel(): RoutinesViewModel {
        val preview = PreviewWorkoutScheduleUseCase()
        val createActivation = CreateTrainingActivationUseCase(schedule, preview, timeProvider)
        val routineActions = RoutineTemplateActions(
            observeTemplates = ObserveRoutineTemplatesUseCase(routines),
            saveTemplate = SaveRoutineTemplateUseCase(routines, timeProvider),
            duplicateTemplate = DuplicateRoutineTemplateUseCase(
                routines,
                SaveRoutineTemplateUseCase(routines, timeProvider)
            ),
            archiveTemplate = ArchiveRoutineTemplateUseCase(routines, timeProvider),
            deleteTemplate = DeleteRoutineTemplateUseCase(routines)
        )
        val scheduleActions = WorkoutScheduleActions(
            repository = schedule,
            previewSchedule = preview,
            activateRoutine = ActivateRoutineUseCase(catalog, createActivation),
            repeatBlock = RepeatTrainingBlockUseCase(schedule, createActivation),
            cancelActivation = CancelTrainingActivationUseCase(schedule, timeProvider),
            finishTrainingBlock = FinishTrainingBlockUseCase(schedule, timeProvider),
            moveOccurrence = MoveWorkoutOccurrenceUseCase(schedule, preview),
            switchScheduleMode = SwitchScheduleModeUseCase(schedule, preview),
            selectOccurrence = SelectWorkoutOccurrenceUseCase(schedule),
            startOccurrence = StartWorkoutOccurrenceUseCase(schedule, timeProvider),
            editOccurrence = EditUnstartedOccurrenceUseCase(
                schedule,
                TestWorkoutLogRepository(),
                catalog
            )
        )
        return RoutinesViewModel(
            routineActions = routineActions,
            scheduleActions = scheduleActions,
            exerciseCatalog = catalog,
            timeProvider = timeProvider,
            weightUnitRepository = TestWeightUnitRepository(),
            exclusionRepository = TestExerciseExclusionRepository(),
            preferredWeekdaysRepository = TestPreferredWeekdaysRepository()
        )
    }
}

private class TestExerciseExclusionRepository : ExerciseExclusionRepository {
    override fun observe(): Flow<List<ExerciseExclusion>> = flowOf(emptyList())

    override suspend fun exclusion(exerciseId: String): ExerciseExclusion? = null

    override suspend fun set(exclusion: ExerciseExclusion) = Unit

    override suspend fun clear(exerciseId: String) = Unit
}

private class TestWeightUnitRepository : WeightUnitRepository {
    private val unit = MutableStateFlow(WeightUnit.KG)

    override suspend fun selectedUnit(): WeightUnit = unit.value

    override fun unitFlow(): Flow<WeightUnit> = unit

    override suspend fun setUnit(unit: WeightUnit) {
        this.unit.value = unit
    }
}

private class TestPreferredWeekdaysRepository : PreferredWeekdaysRepository {
    private val weekdays = MutableStateFlow<Set<DayOfWeek>?>(null)

    override suspend fun current(): Set<DayOfWeek>? = weekdays.value

    override fun observe(): Flow<Set<DayOfWeek>?> = weekdays

    override suspend fun set(weekdays: Set<DayOfWeek>?) {
        this.weekdays.value = weekdays
    }
}

private class TestCatalog : ExerciseCatalog {
    override suspend fun all(): List<Exercise> = listOf(
        Exercise(
            id = "bench-press",
            name = "Barbell Bench Press",
            requiredEquipment = setOf(EquipmentTag("barbell")),
            primaryMuscles = emptySet(),
            involvements = mapOf(MuscleGroup.CHEST_UPPER to 0.6)
        ),
        Exercise(
            id = "overhead-press",
            name = "Overhead Press",
            requiredEquipment = setOf(EquipmentTag("barbell")),
            primaryMuscles = emptySet(),
            involvements = mapOf(MuscleGroup.FRONT_DELTS to 0.5)
        ),
        Exercise(
            id = "barbell-squat",
            name = "Barbell Squat",
            requiredEquipment = setOf(EquipmentTag("barbell")),
            primaryMuscles = emptySet(),
            involvements = mapOf(MuscleGroup.QUADS to 0.7)
        )
    )
}

private class TestRoutineTemplateRepository : RoutineTemplateRepository {
    private val templates = MutableStateFlow<List<RoutineTemplate>>(emptyList())
    private var nextTemplateId = 1L
    private var nextWorkoutId = 1L
    private var nextEntryId = 1L

    override fun observeAll(): Flow<List<RoutineTemplate>> = templates.map { it }

    override suspend fun get(id: Long): RoutineTemplate? = templates.value.firstOrNull {
        it.id == id
    }

    override suspend fun save(template: RoutineTemplate): Long {
        val id = if (template.id == 0L) nextTemplateId++ else template.id
        val stored = assign(template.copy(id = id))
        templates.value = templates.value.filterNot { it.id == id } + stored
        return id
    }

    override suspend fun setArchived(id: Long, archivedAtMillis: Long?) {
        templates.value = templates.value.map {
            if (it.id == id) it.copy(archivedAtMillis = archivedAtMillis) else it
        }
    }

    override suspend fun isReferencedByActivation(id: Long): Boolean = false

    override suspend fun delete(id: Long) {
        templates.value = templates.value.filterNot { it.id == id }
    }

    private fun assign(template: RoutineTemplate): RoutineTemplate = template.copy(
        workouts = template.workouts.map { workout ->
            workout.copy(
                id = if (workout.id == 0L) nextWorkoutId++ else workout.id,
                entries = workout.entries.map { entry ->
                    entry.copy(id = if (entry.id == 0L) nextEntryId++ else entry.id)
                }
            )
        }
    )
}

/** A scheduling store that is never driven by the editor tests; only its observation is read. */
private class TestWorkoutScheduleRepository : WorkoutScheduleRepository {
    private val activation = MutableStateFlow<TrainingActivation?>(null)
    private val state = MutableStateFlow(WorkoutScheduleState())

    override fun observeScheduleState(): Flow<WorkoutScheduleState> = state

    override fun observeActiveActivation(): Flow<TrainingActivation?> = activation

    override fun observeOccurrences(activationId: Long): Flow<List<WorkoutOccurrence>> =
        flowOf(emptyList())

    override suspend fun scheduleState(): WorkoutScheduleState = state.value

    override suspend fun setScheduleState(state: WorkoutScheduleState) {
        this.state.value = state
    }

    override suspend fun activeActivation(): TrainingActivation? = activation.value

    override suspend fun getActivation(id: Long): TrainingActivation? = null

    override suspend fun occurrences(activationId: Long): List<WorkoutOccurrence> = emptyList()

    override suspend fun getOccurrence(id: Long): WorkoutOccurrence? = null

    override suspend fun acceptAndActivate(
        acceptedPlan: AcceptedPlan?,
        activation: TrainingActivation,
        scheduledEpochDays: List<Long?>,
        replaceActive: Boolean
    ): Long = error("The superset editor never activates a routine")

    override suspend fun resolveOccurrence(
        occurrenceId: Long,
        expectedRevision: Int,
        status: OccurrenceStatus,
        resolvedAtMillis: Long,
        entries: List<OccurrenceEntry>
    ): WorkoutScheduleState = error("The superset editor never resolves an occurrence")

    override suspend fun updateActivationHeader(activation: TrainingActivation) = Unit

    override suspend fun updateOccurrence(occurrence: WorkoutOccurrence) = Unit

    override suspend fun replaceOccurrenceEntries(
        occurrenceId: Long,
        entries: List<OccurrenceEntry>
    ) = Unit

    override suspend fun deleteOccurrencesForActivation(activationId: Long) = Unit

    override suspend fun isTemplateReferenced(templateId: Long): Boolean = false
}

private class TestWorkoutLogRepository : WorkoutLogRepository {
    override suspend fun add(set: WorkoutSet) = Unit

    override suspend fun assignSession(setId: Long, sessionId: String) = Unit

    override suspend fun delete(id: Long) = Unit

    override suspend fun updateSetPerformedAt(setId: Long, performedAtMillis: Long) = Unit

    override suspend fun all(): List<WorkoutSet> = emptyList()

    override suspend fun lastSetBySession(sessionId: String): WorkoutSet? = null

    override suspend fun setsForOccurrence(occurrenceId: Long): List<WorkoutSet> = emptyList()

    override fun setsFlow(): Flow<List<WorkoutSet>> = flowOf(emptyList())

    override suspend fun loggedSets(): List<LoggedSet> = emptyList()

    override fun loggedSetsFlow(): Flow<List<LoggedSet>> = flowOf(emptyList())

    override suspend fun clear() = Unit
}
