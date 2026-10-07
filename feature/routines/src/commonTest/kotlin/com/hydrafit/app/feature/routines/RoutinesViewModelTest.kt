package com.hydrafit.app.feature.routines

import com.hydrafit.app.core.domain.engine.AcceptedPlan
import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.fatigue.LoggedSet
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.routine.ArchiveRoutineTemplateUseCase
import com.hydrafit.app.core.domain.routine.DeleteRoutineTemplateUseCase
import com.hydrafit.app.core.domain.routine.DuplicateRoutineTemplateUseCase
import com.hydrafit.app.core.domain.routine.ObserveRoutineTemplatesUseCase
import com.hydrafit.app.core.domain.routine.RoutineEntry
import com.hydrafit.app.core.domain.routine.RoutineTemplate
import com.hydrafit.app.core.domain.routine.RoutineTemplateActions
import com.hydrafit.app.core.domain.routine.RoutineTemplateRepository
import com.hydrafit.app.core.domain.routine.RoutineWorkout
import com.hydrafit.app.core.domain.routine.SaveRoutineTemplateUseCase
import com.hydrafit.app.core.domain.schedule.ActivateRoutineUseCase
import com.hydrafit.app.core.domain.schedule.ActivationStatus
import com.hydrafit.app.core.domain.schedule.CancelTrainingActivationUseCase
import com.hydrafit.app.core.domain.schedule.CreateTrainingActivationUseCase
import com.hydrafit.app.core.domain.schedule.EditUnstartedOccurrenceUseCase
import com.hydrafit.app.core.domain.schedule.FinishTrainingBlockUseCase
import com.hydrafit.app.core.domain.schedule.MoveWorkoutOccurrenceUseCase
import com.hydrafit.app.core.domain.schedule.OccurrenceEntry
import com.hydrafit.app.core.domain.schedule.OccurrenceStatus
import com.hydrafit.app.core.domain.schedule.PreviewWorkoutScheduleUseCase
import com.hydrafit.app.core.domain.schedule.RepeatTrainingBlockUseCase
import com.hydrafit.app.core.domain.schedule.ScheduleException
import com.hydrafit.app.core.domain.schedule.ScheduleMode
import com.hydrafit.app.core.domain.schedule.SelectWorkoutOccurrenceUseCase
import com.hydrafit.app.core.domain.schedule.StartWorkoutOccurrenceUseCase
import com.hydrafit.app.core.domain.schedule.SwitchScheduleModeUseCase
import com.hydrafit.app.core.domain.schedule.TrainingActivation
import com.hydrafit.app.core.domain.schedule.WorkoutOccurrence
import com.hydrafit.app.core.domain.schedule.WorkoutScheduleActions
import com.hydrafit.app.core.domain.schedule.WorkoutScheduleRepository
import com.hydrafit.app.core.domain.schedule.WorkoutScheduleState
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.workout.WorkoutLogRepository
import com.hydrafit.app.core.domain.workout.WorkoutSet
import com.hydrafit.app.core.userdata.settings.WeightUnitRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
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

@OptIn(ExperimentalCoroutinesApi::class)
class RoutinesViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private lateinit var routines: FakeRoutineTemplateRepository
    private lateinit var schedule: FakeWorkoutScheduleRepository
    private lateinit var logs: FakeWorkoutLogRepository
    private val catalog = FakeCatalog()
    private val timeProvider = TimeProvider { 0L }

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        routines = FakeRoutineTemplateRepository()
        schedule = FakeWorkoutScheduleRepository()
        logs = FakeWorkoutLogRepository()
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun savingANewRoutinePersistsItAndClosesTheEditor() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onNewRoutine()
        viewModel.onEditorNameChanged("Upper")
        viewModel.onAddWorkout()
        viewModel.onWorkoutNameChanged(0, "Day 1")
        viewModel.onAddExercise(0)
        viewModel.onPickerQueryChanged("bench")
        viewModel.onPickerExerciseSelected("bench-press", "Barbell Bench Press")
        viewModel.onEntrySetsChanged(0, 0, "4")
        viewModel.onEntryRepsChanged(0, 0, "6")
        viewModel.onEntryWeightChanged(0, 0, "80")
        viewModel.onSaveEditor()
        advanceUntilIdle()

        assertNull(viewModel.state.value.editor)
        val saved = viewModel.state.value.templates.single()
        assertEquals("Upper", saved.name)
        val entry = saved.workouts.single().entries.single()
        assertEquals("bench-press", entry.exerciseId)
        assertEquals(4, entry.sets)
        assertEquals(6, entry.reps)
        assertEquals(80.0, entry.weightKg)
        assertEquals(1, saved.revision)
    }

    @Test
    fun anInvalidDraftKeepsTheEditorAndShowsAMessage() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onNewRoutine()
        viewModel.onSaveEditor()
        advanceUntilIdle()

        assertNotNull(viewModel.state.value.editor)
        assertNotNull(viewModel.state.value.message)
        assertTrue(viewModel.state.value.templates.isEmpty())
    }

    @Test
    fun editingRoutinesPreservesTheIdAndBumpsTheRevision() = runTest(dispatcher) {
        val id = routines.seed(template(name = "Upper"))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onEditRoutine(viewModel.state.value.templates.single())
        viewModel.onEditorNameChanged("Upper v2")
        viewModel.onSaveEditor()
        advanceUntilIdle()

        val saved = viewModel.state.value.templates.single()
        assertEquals(id, saved.id)
        assertEquals(2, saved.revision)
        assertEquals("Upper v2", saved.name)
    }

    @Test
    fun duplicatingAndArchivingUpdateTheLibrary() = runTest(dispatcher) {
        routines.seed(template(name = "Upper"))
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onDuplicate(viewModel.state.value.templates.single())
        advanceUntilIdle()
        assertEquals(2, viewModel.state.value.templates.size)

        val original = viewModel.state.value.templates.first { it.name == "Upper" }
        viewModel.onToggleArchive(original)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.templates.first { it.id == original.id }.isArchived)
    }

    @Test
    fun deletingAReferencedTemplateShowsAMessage() = runTest(dispatcher) {
        val id = routines.seed(template(name = "Upper"))
        routines.referencedTemplates = setOf(id)
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onDelete(viewModel.state.value.templates.single())
        advanceUntilIdle()

        assertNotNull(viewModel.state.value.message)
        assertEquals(1, viewModel.state.value.templates.size)
    }

    @Test
    fun activatingPreviewsDatesAndCreatesOccurrences() = runTest(dispatcher) {
        routines.seed(twoWorkoutTemplate())
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onActivateRequested(viewModel.state.value.templates.single())
        val activation = assertNotNull(viewModel.state.value.activation)
        assertEquals(2, activation.preview.size)
        assertEquals(0L, activation.startEpochDay)

        viewModel.onConfirmActivation()
        advanceUntilIdle()

        assertNull(viewModel.state.value.activation)
        val active = assertNotNull(viewModel.state.value.activeActivation)
        assertEquals(2, viewModel.state.value.occurrences.size)
        assertEquals(ActivationStatus.ACTIVE, active.status)
        assertEquals(ScheduleMode.WEEKDAY, active.mode)
    }

    @Test
    fun postponingMovesAnOccurrence() = runTest(dispatcher) {
        routines.seed(twoWorkoutTemplate())
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onActivateRequested(viewModel.state.value.templates.single())
        viewModel.onConfirmActivation()
        advanceUntilIdle()

        val occurrence = viewModel.state.value.occurrences.first()
        viewModel.onPostpone(occurrence.id, 999L)
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.pendingPostpone)

        viewModel.onConfirmPostpone()
        advanceUntilIdle()

        assertEquals(
            999L,
            viewModel.state.value.occurrences.first { it.id == occurrence.id }.scheduledEpochDay
        )
    }

    @Test
    fun switchingScheduleModeTogglesWeekdayAndSequence() = runTest(dispatcher) {
        routines.seed(twoWorkoutTemplate())
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onActivateRequested(viewModel.state.value.templates.single())
        viewModel.onConfirmActivation()
        advanceUntilIdle()

        viewModel.onSwitchScheduleMode()
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.pendingSwitchMode)
        viewModel.onConfirmSwitchMode()
        advanceUntilIdle()
        assertEquals(ScheduleMode.SEQUENCE, viewModel.state.value.activeActivation?.mode)

        viewModel.onSwitchScheduleMode()
        advanceUntilIdle()
        viewModel.onConfirmSwitchMode()
        advanceUntilIdle()
        assertEquals(ScheduleMode.WEEKDAY, viewModel.state.value.activeActivation?.mode)
    }

    @Test
    fun replacingOneUnsavedSlotLeavesItsSiblingUntouched() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onNewRoutine()
        viewModel.onEditorNameChanged("Upper")
        viewModel.onAddWorkout()
        viewModel.onWorkoutNameChanged(0, "Day 1")
        viewModel.onAddExercise(0)
        viewModel.onPickerExerciseSelected("bench-press", "Barbell Bench Press")
        viewModel.onAddExercise(0)
        viewModel.onPickerExerciseSelected("bench-press", "Barbell Bench Press")

        viewModel.onReplaceExercise(0, 1)
        viewModel.onPickerExerciseSelected("overhead-press", "Overhead Press")

        val entries = requireNotNull(viewModel.state.value.editor).workouts[0].entries
        assertEquals(2, entries.size)
        assertEquals("bench-press", entries[0].exerciseId)
        assertEquals("overhead-press", entries[1].exerciseId)
    }

    @Test
    fun anInvalidWeightIsRejectedInsteadOfSilentlyCleared() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()
        viewModel.onNewRoutine()
        viewModel.onEditorNameChanged("Upper")
        viewModel.onAddWorkout()
        viewModel.onWorkoutNameChanged(0, "Day 1")
        viewModel.onAddExercise(0)
        viewModel.onPickerExerciseSelected("bench-press", "Barbell Bench Press")
        viewModel.onEntryWeightChanged(0, 0, "abc")

        viewModel.onSaveEditor()
        advanceUntilIdle()

        assertNotNull(viewModel.state.value.editor)
        assertNotNull(viewModel.state.value.message)
        assertTrue(viewModel.state.value.templates.isEmpty())
    }

    @Test
    fun theEditorFreezesTheUnitItWasOpenedWith() = runTest(dispatcher) {
        val unitRepository = FakeWeightUnitRepository()
        val viewModel = viewModel(unitRepository)
        advanceUntilIdle()
        viewModel.onNewRoutine()
        viewModel.onEditorNameChanged("Upper")
        viewModel.onAddWorkout()
        viewModel.onWorkoutNameChanged(0, "Day 1")
        viewModel.onAddExercise(0)
        viewModel.onPickerExerciseSelected("bench-press", "Barbell Bench Press")
        viewModel.onEntryWeightChanged(0, 0, "100")

        unitRepository.setUnit(WeightUnit.LB)
        advanceUntilIdle()

        viewModel.onSaveEditor()
        advanceUntilIdle()

        val entry = viewModel.state.value.templates.single().workouts.single().entries.single()
        assertEquals(100.0, entry.weightKg)
    }

    private fun viewModel(
        weightUnitRepository: FakeWeightUnitRepository = FakeWeightUnitRepository()
    ): RoutinesViewModel {
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
            editOccurrence = EditUnstartedOccurrenceUseCase(schedule, logs, catalog)
        )
        return RoutinesViewModel(
            routineActions = routineActions,
            scheduleActions = scheduleActions,
            exerciseCatalog = catalog,
            timeProvider = timeProvider,
            weightUnitRepository = weightUnitRepository
        )
    }

    private fun template(name: String) = RoutineTemplate(
        name = name,
        revision = 1,
        workouts = listOf(
            RoutineWorkout(
                name = "Day 1",
                entries = listOf(RoutineEntry(exerciseId = "bench-press", sets = 3, reps = 8))
            )
        )
    )

    private fun twoWorkoutTemplate() = RoutineTemplate(
        name = "Upper/Lower",
        workouts = listOf(
            RoutineWorkout(
                name = "Upper",
                entries = listOf(RoutineEntry(exerciseId = "bench-press", sets = 3, reps = 8))
            ),
            RoutineWorkout(
                name = "Lower",
                entries = listOf(RoutineEntry(exerciseId = "barbell-squat", sets = 3, reps = 5))
            )
        )
    )
}

private class FakeCatalog : ExerciseCatalog {
    override suspend fun all(): List<Exercise> = listOf(
        Exercise(
            id = "bench-press",
            name = "Barbell Bench Press",
            requiredEquipment = setOf(EquipmentTag("barbell")),
            primaryMuscles = emptySet(),
            involvements = mapOf(MuscleGroup.CHEST_UPPER to 0.6)
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

private class FakeWeightUnitRepository : WeightUnitRepository {
    private val unit = MutableStateFlow(WeightUnit.KG)

    override suspend fun selectedUnit(): WeightUnit = unit.value

    override fun unitFlow(): Flow<WeightUnit> = unit

    override suspend fun setUnit(unit: WeightUnit) {
        this.unit.value = unit
    }
}

private class FakeRoutineTemplateRepository : RoutineTemplateRepository {
    private val templates = MutableStateFlow<List<RoutineTemplate>>(emptyList())
    var referencedTemplates: Set<Long> = emptySet()
    private var nextTemplateId = 1L
    private var nextWorkoutId = 1L
    private var nextEntryId = 1L

    fun seed(template: RoutineTemplate): Long {
        val id = nextTemplateId++
        val stored = assign(template.copy(id = id))
        templates.value = templates.value + stored
        return id
    }

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

    override suspend fun isReferencedByActivation(id: Long): Boolean = id in referencedTemplates

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

private class FakeWorkoutScheduleRepository : WorkoutScheduleRepository {
    private val activationState = MutableStateFlow<List<TrainingActivation>>(emptyList())
    private val occurrenceState = MutableStateFlow<List<WorkoutOccurrence>>(emptyList())
    private val state = MutableStateFlow(WorkoutScheduleState())
    private var nextActivationId = 1L
    private var nextWorkoutId = 1L
    private var nextActivationEntryId = 1L
    private var nextOccurrenceId = 1L
    private var nextOccurrenceEntryId = 1L

    override fun observeScheduleState(): Flow<WorkoutScheduleState> = state.map { it }

    override fun observeActiveActivation(): Flow<TrainingActivation?> =
        activationState.map { list -> list.lastOrNull { it.status == ActivationStatus.ACTIVE } }

    override fun observeOccurrences(activationId: Long): Flow<List<WorkoutOccurrence>> =
        occurrenceState.map { list ->
            list.filter { it.activationId == activationId }.sortedBy { it.queuePosition }
        }

    override suspend fun scheduleState(): WorkoutScheduleState = state.value

    override suspend fun setScheduleState(state: WorkoutScheduleState) {
        this.state.value = state
    }

    override suspend fun activeActivation(): TrainingActivation? =
        activationState.value.lastOrNull { it.status == ActivationStatus.ACTIVE }

    override suspend fun getActivation(id: Long): TrainingActivation? =
        activationState.value.firstOrNull { it.id == id }

    override suspend fun occurrences(activationId: Long): List<WorkoutOccurrence> =
        occurrenceState.value.filter {
            it.activationId == activationId
        }.sortedBy { it.queuePosition }

    override suspend fun getOccurrence(id: Long): WorkoutOccurrence? =
        occurrenceState.value.firstOrNull { it.id == id }

    override suspend fun acceptAndActivate(
        acceptedPlan: AcceptedPlan?,
        activation: TrainingActivation,
        scheduledEpochDays: List<Long?>,
        replaceActive: Boolean
    ): Long {
        val active = activationState.value.lastOrNull { it.status == ActivationStatus.ACTIVE }
        if (active != null) {
            if (!replaceActive) throw ScheduleException("A training block is already active")
            activationState.value = activationState.value.map {
                if (it.id == active.id) {
                    it.copy(
                        status = ActivationStatus.CANCELLED,
                        endedAtMillis = activation.createdAtMillis,
                        revision = it.revision + 1
                    )
                } else {
                    it
                }
            }
        }
        val id = nextActivationId++
        val stored = activation.copy(
            id = id,
            workouts = activation.workouts.map { workout ->
                workout.copy(
                    id = nextWorkoutId++,
                    entries = workout.entries.map { it.copy(id = nextActivationEntryId++) }
                )
            }
        )
        activationState.value = activationState.value + stored
        val created = stored.workouts.mapIndexed { index, workout ->
            WorkoutOccurrence(
                id = nextOccurrenceId++,
                activationId = id,
                activationWorkoutId = workout.id,
                queuePosition = index,
                scheduledEpochDay = scheduledEpochDays.getOrNull(index),
                status = OccurrenceStatus.PENDING,
                revision = 1,
                entries = workout.entries.mapIndexed { entryIndex, entry ->
                    OccurrenceEntry(
                        id = nextOccurrenceEntryId++,
                        sourceActivationEntryId = entry.id,
                        position = entryIndex,
                        exerciseId = entry.exerciseId,
                        exerciseName = entry.exerciseName,
                        movementPattern = entry.movementPattern,
                        requiredEquipment = entry.requiredEquipment,
                        involvements = entry.involvements,
                        isUnilateral = entry.isUnilateral,
                        sets = entry.sets,
                        reps = entry.reps,
                        weightKg = entry.weightKg
                    )
                }
            )
        }
        occurrenceState.value = occurrenceState.value + created
        state.value = WorkoutScheduleState(
            activeActivationId = id,
            selectedOccurrenceId = created.firstOrNull()?.id,
            legacyFallbackEnabled = false
        )
        return id
    }

    override suspend fun resolveOccurrence(
        occurrenceId: Long,
        expectedRevision: Int,
        status: OccurrenceStatus,
        resolvedAtMillis: Long,
        entries: List<OccurrenceEntry>
    ): WorkoutScheduleState {
        val occurrence = occurrenceState.value.firstOrNull { it.id == occurrenceId }
            ?: throw ScheduleException("That workout no longer exists")
        if (state.value.activeActivationId != occurrence.activationId) {
            throw ScheduleException("That workout is not in the active block")
        }
        if (occurrence.revision != expectedRevision) {
            throw ScheduleException("That workout changed; refresh and try again")
        }
        occurrenceState.value = occurrenceState.value.map {
            if (it.id != occurrenceId) {
                it
            } else {
                it.copy(
                    entries = entries.map { entry ->
                        entry.copy(id = if (entry.id == 0L) nextOccurrenceEntryId++ else entry.id)
                    },
                    status = status,
                    startedAtMillis = it.startedAtMillis ?: resolvedAtMillis,
                    resolvedAtMillis = resolvedAtMillis,
                    revision = it.revision + 1
                )
            }
        }
        val next = occurrenceState.value
            .filter { it.activationId == occurrence.activationId && !it.isResolved }
            .minByOrNull { it.queuePosition }
        val updated = state.value.copy(selectedOccurrenceId = next?.id)
        state.value = updated
        return updated
    }

    override suspend fun updateActivationHeader(activation: TrainingActivation) {
        activationState.value =
            activationState.value.map { if (it.id == activation.id) activation else it }
    }

    override suspend fun updateOccurrence(occurrence: WorkoutOccurrence) {
        occurrenceState.value = occurrenceState.value.map {
            if (it.id != occurrence.id) {
                it
            } else {
                it.copy(
                    queuePosition = occurrence.queuePosition,
                    scheduledEpochDay = occurrence.scheduledEpochDay,
                    notBeforeEpochDay = occurrence.notBeforeEpochDay,
                    startedAtMillis = occurrence.startedAtMillis,
                    resolvedAtMillis = occurrence.resolvedAtMillis,
                    status = occurrence.status,
                    revision = occurrence.revision
                )
            }
        }
    }

    override suspend fun replaceOccurrenceEntries(
        occurrenceId: Long,
        entries: List<OccurrenceEntry>
    ) {
        occurrenceState.value = occurrenceState.value.map { occurrence ->
            if (occurrence.id != occurrenceId) {
                occurrence
            } else {
                occurrence.copy(
                    entries = entries.map {
                        it.copy(
                            id = if (it.id ==
                                0L
                            ) {
                                nextOccurrenceEntryId++
                            } else {
                                it.id
                            }
                        )
                    }
                )
            }
        }
    }

    override suspend fun deleteOccurrencesForActivation(activationId: Long) {
        occurrenceState.value = occurrenceState.value.filterNot { it.activationId == activationId }
    }

    override suspend fun isTemplateReferenced(templateId: Long): Boolean =
        activationState.value.any { it.templateId == templateId }
}

private class FakeWorkoutLogRepository : WorkoutLogRepository {
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
