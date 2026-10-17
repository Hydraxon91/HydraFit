package com.hydrafit.app.core.domain.routine

import com.hydrafit.app.core.domain.workout.LoadKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest

/**
 * A prescription slot is measured by repetitions or by whole seconds, never both and never neither,
 * and a timed slot survives the routine save/reload path with its load intact.
 */
class PrescriptionTimedSlotTest {

    @Test
    fun acceptsACountedSlotAndATimedSlotAndRejectsBothOrNeither() {
        PrescriptionBounds.validate(sets = 3, reps = 8, durationSeconds = null, weightKg = 80.0)
        PrescriptionBounds.validate(sets = 3, reps = null, durationSeconds = 90, weightKg = 80.0)
        PrescriptionBounds.validate(sets = 3, reps = null, durationSeconds = 90, weightKg = null)

        // Both measurements, and the empty shape, are refused outright.
        assertFailsWith<RoutineTemplateException> {
            PrescriptionBounds.validate(sets = 3, reps = 8, durationSeconds = 90, weightKg = null)
        }
        assertFailsWith<RoutineTemplateException> {
            PrescriptionBounds.validate(
                sets = 3,
                reps = null,
                durationSeconds = null,
                weightKg = null
            )
        }
        assertFailsWith<RoutineTemplateException> {
            PrescriptionBounds.validate(sets = 3, reps = 0, durationSeconds = null, weightKg = null)
        }
        assertFailsWith<RoutineTemplateException> {
            PrescriptionBounds.validate(sets = 3, reps = null, durationSeconds = 0, weightKg = null)
        }
        assertFailsWith<RoutineTemplateException> {
            PrescriptionBounds.validate(
                sets = 3,
                reps = null,
                durationSeconds = -30,
                weightKg = null
            )
        }
        assertFailsWith<RoutineTemplateException> {
            PrescriptionBounds.validate(
                sets = 3,
                reps = 101,
                durationSeconds = null,
                weightKg = null
            )
        }
        assertFailsWith<RoutineTemplateException> {
            PrescriptionBounds.validate(sets = 0, reps = 8, durationSeconds = null, weightKg = null)
        }
    }

    @Test
    fun savingAndReloadingARoutineKeepsATimedSlotExpressedInSecondsWithItsLoad() = runTest {
        val repository = InMemoryRoutineTemplateRepository()
        val save = SaveRoutineTemplateUseCase(repository) { 5L }

        val saved = save(
            routine(
                RoutineEntry(
                    exerciseId = "bench-press",
                    sets = 3,
                    reps = null,
                    durationSeconds = 45,
                    weightKg = 60.0,
                    loadKind = LoadKind.EXTERNAL
                ),
                RoutineEntry(exerciseId = "barbell-squat", sets = 5, reps = 5, weightKg = 100.0)
            )
        )

        val reloaded = requireNotNull(repository.get(saved.id))
        val timed = reloaded.workouts.single().entries[0]
        assertNull(timed.reps)
        assertEquals(45, timed.durationSeconds)
        assertEquals(60.0, timed.weightKg)
        assertEquals(LoadKind.EXTERNAL, timed.loadKind)
        // The counted sibling keeps the rep semantics it was written with.
        val counted = reloaded.workouts.single().entries[1]
        assertEquals(5, counted.reps)
        assertNull(counted.durationSeconds)
    }

    @Test
    fun aDraftCarryingBothOrNeitherMeasurementIsRefusedBeforeItIsStored() = runTest {
        val repository = InMemoryRoutineTemplateRepository()
        val save = SaveRoutineTemplateUseCase(repository) { 5L }

        assertFailsWith<RoutineTemplateException> {
            save(
                routine(
                    RoutineEntry(
                        exerciseId = "bench-press",
                        sets = 3,
                        reps = 8,
                        durationSeconds = 45
                    )
                )
            )
        }
        assertFailsWith<RoutineTemplateException> {
            save(RoutineEntry(exerciseId = "bench-press", sets = 3).let { routine(it) })
        }
        assertEquals(0, repository.stored.value.size)
    }

    private fun routine(vararg entries: RoutineEntry) = RoutineTemplate(
        name = "Push",
        workouts = listOf(
            RoutineWorkout(position = 0, name = "Day 1", entries = entries.toList())
        )
    )
}

/** An in-memory store that assigns ids the way storage does, so save/reload is exercised. */
private class InMemoryRoutineTemplateRepository : RoutineTemplateRepository {
    val stored = MutableStateFlow<List<RoutineTemplate>>(emptyList())
    private var nextId = 1L

    override fun observeAll(): Flow<List<RoutineTemplate>> = stored.map { it }

    override suspend fun get(id: Long): RoutineTemplate? = stored.value.firstOrNull { it.id == id }

    override suspend fun save(template: RoutineTemplate): Long {
        val id = if (template.id == 0L) nextId++ else template.id
        stored.value = stored.value.filterNot { it.id == id } + template.copy(id = id)
        return id
    }

    override suspend fun setArchived(id: Long, archivedAtMillis: Long?) {
        stored.value = stored.value.map {
            if (it.id == id) it.copy(archivedAtMillis = archivedAtMillis) else it
        }
    }

    override suspend fun isReferencedByActivation(id: Long): Boolean = false

    override suspend fun delete(id: Long) {
        stored.value = stored.value.filterNot { it.id == id }
    }
}
