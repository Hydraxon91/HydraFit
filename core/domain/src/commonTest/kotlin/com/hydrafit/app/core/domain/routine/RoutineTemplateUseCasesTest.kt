package com.hydrafit.app.core.domain.routine

import com.hydrafit.app.core.domain.engine.AcceptedDay
import com.hydrafit.app.core.domain.engine.AcceptedExercise
import com.hydrafit.app.core.domain.engine.AcceptedPlan
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.time.TimeProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest

class RoutineTemplateUseCasesTest {

    private var now = 1_000L
    private val timeProvider = TimeProvider { now }

    private fun save(repository: RoutineTemplateRepository) =
        SaveRoutineTemplateUseCase(repository, timeProvider)

    @Test
    fun savingANewTemplateAssignsAnIdAndTheFirstRevision() = runTest {
        val repository = FakeRoutineTemplateRepository()

        val saved = save(repository)(
            RoutineTemplate(
                name = "  Upper Day  ",
                workouts = listOf(
                    RoutineWorkout(
                        name = "Day 1",
                        entries = listOf(RoutineEntry(exerciseId = "bench-press", sets = 3, reps = 8))
                    )
                )
            )
        )

        assertEquals(1L, saved.id)
        assertEquals("Upper Day", saved.name)
        assertEquals(1, saved.revision)
        assertEquals(1_000L, saved.createdAtMillis)
        assertEquals(1_000L, saved.updatedAtMillis)
        assertEquals(1L, repository.get(1L)?.id)
    }

    @Test
    fun editingBumpsTheRevisionAndPreservesCreationAndArchival() = runTest {
        val repository = FakeRoutineTemplateRepository()
        val saver = save(repository)
        val created = saver(
            RoutineTemplate(
                name = "Upper",
                workouts = listOf(RoutineWorkout(name = "Day 1"))
            )
        )
        repository.setArchived(created.id, 55L)

        now = 2_000L
        val edited = saver(
            created.copy(
                name = "Upper v2",
                archivedAtMillis = null,
                workouts = listOf(
                    RoutineWorkout(
                        name = "Day 1",
                        entries = listOf(RoutineEntry(exerciseId = "bench-press", sets = 4, reps = 6))
                    )
                )
            )
        )

        assertEquals(2, edited.revision)
        assertEquals(1_000L, edited.createdAtMillis)
        assertEquals(2_000L, edited.updatedAtMillis)
        assertEquals(55L, edited.archivedAtMillis)
        assertEquals("Upper v2", edited.name)
    }

    @Test
    fun reorderingWorkoutsAndSlotsPreservesTheirIds() = runTest {
        val repository = FakeRoutineTemplateRepository()
        val saver = save(repository)
        val created = saver(
            RoutineTemplate(
                name = "Push Pull",
                workouts = listOf(
                    RoutineWorkout(
                        name = "Push",
                        entries = listOf(
                            RoutineEntry(exerciseId = "bench-press", sets = 3, reps = 8),
                            RoutineEntry(exerciseId = "overhead-press", sets = 3, reps = 8)
                        )
                    ),
                    RoutineWorkout(
                        name = "Pull",
                        entries = listOf(RoutineEntry(exerciseId = "row", sets = 3, reps = 8))
                    )
                )
            )
        )
        val pushId = created.workouts[0].id
        val benchId = created.workouts[0].entries[0].id
        val ohpId = created.workouts[0].entries[1].id

        val reordered = saver(
            created.copy(
                workouts = listOf(
                    created.workouts[1],
                    created.workouts[0].copy(
                        entries = listOf(created.workouts[0].entries[1], created.workouts[0].entries[0])
                    )
                )
            )
        )

        assertEquals("Pull", reordered.workouts[0].name)
        val push = reordered.workouts[1]
        assertEquals(pushId, push.id)
        assertEquals(listOf(ohpId, benchId), push.entries.map { it.id })
        assertEquals(listOf("overhead-press", "bench-press"), push.entries.map { it.exerciseId })
        assertEquals(listOf(0, 1), push.entries.map { it.position })
    }

    @Test
    fun removingASlotDeletesOnlyThatRow() = runTest {
        val repository = FakeRoutineTemplateRepository()
        val saver = save(repository)
        val created = saver(
            RoutineTemplate(
                name = "Upper",
                workouts = listOf(
                    RoutineWorkout(
                        name = "Day 1",
                        entries = listOf(
                            RoutineEntry(exerciseId = "bench-press", sets = 3, reps = 8),
                            RoutineEntry(exerciseId = "overhead-press", sets = 3, reps = 8)
                        )
                    )
                )
            )
        )
        val kept = created.workouts[0].entries[1]

        val edited = saver(
            created.copy(
                workouts = listOf(created.workouts[0].copy(entries = listOf(kept)))
            )
        )

        val reloaded = requireNotNull(repository.get(edited.id))
        assertEquals(listOf("overhead-press"), reloaded.workouts[0].entries.map { it.exerciseId })
        assertEquals(listOf(kept.id), reloaded.workouts[0].entries.map { it.id })
    }

    @Test
    fun rejectsInvalidDrafts() = runTest {
        val repository = FakeRoutineTemplateRepository()
        val saver = save(repository)

        assertRoutineFailure {
            saver(RoutineTemplate(name = " ", workouts = listOf(RoutineWorkout(name = "Day 1"))))
        }
        assertRoutineFailure {
            saver(RoutineTemplate(name = "Empty"))
        }
        assertRoutineFailure {
            saver(
                RoutineTemplate(
                    name = "Blank workout",
                    workouts = listOf(RoutineWorkout(name = " "))
                )
            )
        }
        assertRoutineFailure {
            saver(
                RoutineTemplate(
                    name = "Too many sets",
                    workouts = listOf(
                        RoutineWorkout(
                            name = "Day 1",
                            entries = listOf(
                                RoutineEntry(exerciseId = "bench-press", sets = 9, reps = 8)
                            )
                        )
                    )
                )
            )
        }
        assertRoutineFailure {
            saver(
                RoutineTemplate(
                    name = "Negative load",
                    workouts = listOf(
                        RoutineWorkout(
                            name = "Day 1",
                            entries = listOf(
                                RoutineEntry(exerciseId = "bench-press", sets = 3, reps = 8, weightKg = -1.0)
                            )
                        )
                    )
                )
            )
        }
        assertRoutineFailure {
            saver(
                RoutineTemplate(
                    name = "Not a number",
                    workouts = listOf(
                        RoutineWorkout(
                            name = "Day 1",
                            entries = listOf(
                                RoutineEntry(
                                    exerciseId = "bench-press",
                                    sets = 3,
                                    reps = 8,
                                    weightKg = Double.NaN
                                )
                            )
                        )
                    )
                )
            )
        }
    }

    private suspend fun assertRoutineFailure(block: suspend () -> Unit) {
        val error = runCatching { block() }.exceptionOrNull()
        assertTrue(
            error is RoutineTemplateException,
            "Expected RoutineTemplateException but was $error"
        )
    }

    @Test
    fun anExplicitZeroLoadIsAcceptedAndDistinctFromUnspecified() = runTest {
        val repository = FakeRoutineTemplateRepository()

        val saved = save(repository)(
            RoutineTemplate(
                name = "Bodyweight",
                workouts = listOf(
                    RoutineWorkout(
                        name = "Day 1",
                        entries = listOf(
                            RoutineEntry(exerciseId = "push-up", sets = 3, reps = 12, weightKg = 0.0),
                            RoutineEntry(exerciseId = "pull-up", sets = 3, reps = 8)
                        )
                    )
                )
            )
        )

        val entries = requireNotNull(repository.get(saved.id)).workouts[0].entries
        assertEquals(0.0, entries[0].weightKg)
        assertNull(entries[1].weightKg)
    }

    @Test
    fun duplicatingCreatesAnIndependentCopyWithFreshIds() = runTest {
        val repository = FakeRoutineTemplateRepository()
        val saver = save(repository)
        val duplicate = DuplicateRoutineTemplateUseCase(repository, saver)
        val source = saver(
            RoutineTemplate(
                name = "Upper",
                workouts = listOf(
                    RoutineWorkout(
                        name = "Day 1",
                        entries = listOf(RoutineEntry(exerciseId = "bench-press", sets = 3, reps = 8))
                    )
                )
            )
        )

        val copyId = requireNotNull(duplicate(source.id))

        val sourceReloaded = requireNotNull(repository.get(source.id))
        val copy = requireNotNull(repository.get(copyId))
        assertEquals("Upper copy", copy.name)
        assertEquals(1, copy.revision)
        assertTrue(copy.workouts[0].id != sourceReloaded.workouts[0].id)
        assertTrue(copy.workouts[0].entries[0].id != sourceReloaded.workouts[0].entries[0].id)
        assertEquals("bench-press", copy.workouts[0].entries[0].exerciseId)
        assertNull(duplicate(9_999L))
    }

    @Test
    fun archivingAndRestoringTogglesTheFlag() = runTest {
        val repository = FakeRoutineTemplateRepository()
        val archive = ArchiveRoutineTemplateUseCase(repository, timeProvider)
        val saved = save(repository)(RoutineTemplate(name = "Upper", workouts = listOf(RoutineWorkout(name = "Day 1"))))

        archive(saved.id, archived = true)
        assertEquals(1_000L, repository.get(saved.id)?.archivedAtMillis)

        archive(saved.id, archived = false)
        assertNull(repository.get(saved.id)?.archivedAtMillis)
        assertFalse(requireNotNull(repository.get(saved.id)).isArchived)
    }

    @Test
    fun deletingRemovesTheTemplate() = runTest {
        val repository = FakeRoutineTemplateRepository()
        val saver = save(repository)
        val delete = DeleteRoutineTemplateUseCase(repository)
        val saved = saver(RoutineTemplate(name = "Upper", workouts = listOf(RoutineWorkout(name = "Day 1"))))

        assertTrue(delete(saved.id))
        assertNull(repository.get(saved.id))
        assertFalse(delete(saved.id))
    }

    @Test
    fun convertsAnAcceptedPlanIntoAnEditableTemplate() {
        val plan = AcceptedPlan(
            engine = PlannerEngineId.DETERMINISTIC,
            acceptedAtMillis = 10L,
            id = 7L,
            days = listOf(
                AcceptedDay(
                    dayIndex = 0,
                    focus = SplitFocus.PUSH,
                    exercises = listOf(
                        AcceptedExercise(
                            exerciseId = "bench-press",
                            sets = 3,
                            reps = 8,
                            name = "Barbell Bench Press",
                            movementPattern = MovementPattern.HORIZONTAL_PUSH,
                            suggestedWeightKg = 82.5
                        )
                    )
                ),
                AcceptedDay(dayIndex = 1, focus = SplitFocus.LEGS, exercises = emptyList())
            )
        )

        val template = ConvertPlanToTemplateUseCase()(plan, "My Plan")

        assertEquals("My Plan", template.name)
        assertEquals(7L, template.sourcePlanId)
        assertEquals(2, template.workouts.size)
        assertEquals(SplitFocus.PUSH, template.workouts[0].focus)
        assertEquals("bench-press", template.workouts[0].entries[0].exerciseId)
        assertEquals(82.5, template.workouts[0].entries[0].weightKg)
        assertTrue(template.workouts[1].entries.isEmpty())
    }

    @Test
    fun conversionOmitsProvenanceForAnUnsavedPlan() {
        val plan = AcceptedPlan(
            engine = PlannerEngineId.DETERMINISTIC,
            acceptedAtMillis = 0L,
            id = 0L,
            days = listOf(AcceptedDay(dayIndex = 0, focus = SplitFocus.PUSH, exercises = emptyList()))
        )

        val template = ConvertPlanToTemplateUseCase()(plan, "Draft")

        assertNull(template.sourcePlanId)
    }
}

private class FakeRoutineTemplateRepository : RoutineTemplateRepository {
    private val templates = MutableStateFlow<List<RoutineTemplate>>(emptyList())
    private var nextTemplateId = 1L
    private var nextWorkoutId = 1L
    private var nextEntryId = 1L

    override fun observeAll(): Flow<List<RoutineTemplate>> = templates.map { it }

    override suspend fun get(id: Long): RoutineTemplate? = templates.value.firstOrNull { it.id == id }

    override suspend fun save(template: RoutineTemplate): Long {
        val id = if (template.id == 0L) nextTemplateId++ else template.id
        val stored = assignIds(template.copy(id = id))
        templates.value = templates.value.filterNot { it.id == id } + stored
        return id
    }

    override suspend fun setArchived(id: Long, archivedAtMillis: Long?) {
        templates.value = templates.value.map {
            if (it.id == id) it.copy(archivedAtMillis = archivedAtMillis) else it
        }
    }

    override suspend fun delete(id: Long) {
        templates.value = templates.value.filterNot { it.id == id }
    }

    private fun assignIds(template: RoutineTemplate): RoutineTemplate = template.copy(
        workouts = template.workouts.map { workout ->
            val workoutId = if (workout.id == 0L) nextWorkoutId++ else workout.id
            workout.copy(
                id = workoutId,
                entries = workout.entries.map { entry ->
                    entry.copy(id = if (entry.id == 0L) nextEntryId++ else entry.id)
                }
            )
        }
    )
}
