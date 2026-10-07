package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.schedule.ActivationEntry
import com.hydrafit.app.core.domain.schedule.ActivationStatus
import com.hydrafit.app.core.domain.schedule.ActivationWorkout
import com.hydrafit.app.core.domain.schedule.OccurrenceEntry
import com.hydrafit.app.core.domain.schedule.OccurrenceStatus
import com.hydrafit.app.core.domain.schedule.RemainingDisposition
import com.hydrafit.app.core.domain.schedule.ScheduleMode
import com.hydrafit.app.core.domain.schedule.TrainingActivation
import com.hydrafit.app.core.domain.schedule.WorkoutOccurrence
import com.hydrafit.app.core.domain.schedule.WorkoutScheduleState
import com.hydrafit.app.core.domain.time.DayOfWeek
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class SqlDelightWorkoutScheduleRepositoryTest {

    private lateinit var driver: SqlDriver
    private lateinit var database: HydraFitDatabase
    private lateinit var repository: SqlDelightWorkoutScheduleRepository

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        database = HydraFitDatabase(driver)
        repository = SqlDelightWorkoutScheduleRepository(database)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun scheduleStateDefaultsToLegacyFallbackWhenUnset() = runTest {
        assertEquals(WorkoutScheduleState(), repository.scheduleState())

        repository.setScheduleState(
            WorkoutScheduleState(
                activeActivationId = 5L,
                selectedOccurrenceId = 9L,
                legacyFallbackEnabled = false
            )
        )

        assertEquals(
            WorkoutScheduleState(5L, 9L, legacyFallbackEnabled = false),
            repository.scheduleState()
        )
        assertEquals(
            WorkoutScheduleState(5L, 9L, legacyFallbackEnabled = false),
            repository.observeScheduleState().first()
        )
    }

    @Test
    fun insertsAndReloadsAnActivationWithFrozenWorkoutsAndEntries() = runTest {
        val activation = activation()
        val id = repository.insertActivation(activation)

        val reloaded = requireNotNull(repository.getActivation(id))
        assertEquals("Upper block", reloaded.name)
        assertEquals(ScheduleMode.WEEKDAY, reloaded.mode)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), reloaded.weekdays)
        assertEquals(ActivationStatus.ACTIVE, reloaded.status)
        assertEquals(7L, reloaded.templateId)
        assertEquals(3, reloaded.templateRevision)
        assertEquals(SplitFocus.PUSH, reloaded.workouts.single().focus)
        val slot = reloaded.workouts.single().entries.single()
        assertEquals("bench-press", slot.exerciseId)
        assertEquals("Barbell Bench Press", slot.exerciseName)
        assertEquals(setOf(EquipmentTag("barbell")), slot.requiredEquipment)
        assertEquals(mapOf(MuscleGroup.CHEST_UPPER to 0.6), slot.involvements)
        assertTrue(slot.isUnilateral.not())
        assertEquals(82.5, slot.weightKg)

        assertEquals(id, requireNotNull(repository.activeActivation()).id)
        assertEquals(id, repository.observeActiveActivation().first()?.id)
    }

    @Test
    fun insertsOccurrencesWithWorkingCopyEntriesAndResolvesThem() = runTest {
        val id = repository.insertActivation(activation())
        val saved = requireNotNull(repository.getActivation(id))
        val workoutId = saved.workouts.single().id
        val sourceEntryId = saved.workouts.single().entries.single().id

        val inserted = repository.insertOccurrences(
            listOf(
                occurrence(
                    activationId = id,
                    activationWorkoutId = workoutId,
                    queuePosition = 0,
                    scheduledEpochDay = 20_000L,
                    sourceEntryId = sourceEntryId
                ),
                occurrence(
                    activationId = id,
                    activationWorkoutId = workoutId,
                    queuePosition = 1,
                    scheduledEpochDay = 20_002L,
                    sourceEntryId = sourceEntryId
                )
            )
        )

        assertEquals(listOf(0, 1), inserted.map { it.queuePosition })
        assertEquals(listOf(20_000L, 20_002L), inserted.map { it.scheduledEpochDay })
        val reloaded = repository.occurrences(id)
        assertEquals(inserted.map { it.id }, reloaded.map { it.id })
        assertEquals(sourceEntryId, reloaded[0].entries.single().sourceActivationEntryId)
        assertEquals(0, reloaded[0].entries.single().position)
        assertNull(reloaded[0].entries.single().weightKg)
    }

    @Test
    fun updatingAnOccurrencePersistsStatusAndCompletionFields() = runTest {
        val id = repository.insertActivation(activation())
        val saved = requireNotNull(repository.getActivation(id))
        val inserted = repository.insertOccurrences(
            listOf(
                occurrence(
                    id,
                    saved.workouts.single().id,
                    0,
                    20_000L,
                    saved.workouts.single().entries.single().id
                )
            )
        ).single()

        repository.updateOccurrence(
            inserted.copy(
                status = OccurrenceStatus.FINISHED_PARTIAL,
                startedAtMillis = 111L,
                resolvedAtMillis = 222L,
                revision = 2
            )
        )

        val reloaded = requireNotNull(repository.getOccurrence(inserted.id))
        assertEquals(OccurrenceStatus.FINISHED_PARTIAL, reloaded.status)
        assertEquals(111L, reloaded.startedAtMillis)
        assertEquals(222L, reloaded.resolvedAtMillis)
        assertEquals(2, reloaded.revision)
    }

    @Test
    fun replacingOccurrenceEntriesPreservesKeptIdsAndDeletesRemovedRows() = runTest {
        val id = repository.insertActivation(activation())
        val saved = requireNotNull(repository.getActivation(id))
        val sourceEntryId = saved.workouts.single().entries.single().id
        val base = occurrence(id, saved.workouts.single().id, 0, 20_000L, sourceEntryId)
        val occurrence = repository.insertOccurrences(
            listOf(
                base.copy(
                    entries = listOf(
                        base.entries.single(),
                        base.entries.single().copy(
                            position = 1,
                            exerciseId = "overhead-press",
                            exerciseName = "Overhead Press",
                            movementPattern = MovementPattern.VERTICAL_PUSH
                        )
                    )
                )
            )
        ).single()
        val bench = occurrence.entries[0]

        repository.replaceOccurrenceEntries(
            occurrence.id,
            listOf(
                bench.copy(
                    sets = 5,
                    remainingDisposition = RemainingDisposition.OMITTED,
                    terminalRemainingSets = 2
                )
            )
        )

        val reloaded = requireNotNull(repository.getOccurrence(occurrence.id))
        assertEquals(1, reloaded.entries.size)
        assertEquals(bench.id, reloaded.entries[0].id)
        assertEquals(5, reloaded.entries[0].sets)
        assertEquals(RemainingDisposition.OMITTED, reloaded.entries[0].remainingDisposition)
        assertEquals(2, reloaded.entries[0].terminalRemainingSets)
    }

    @Test
    fun replacingOccurrenceEntriesInsertsNewRows() = runTest {
        val id = repository.insertActivation(activation())
        val saved = requireNotNull(repository.getActivation(id))
        val sourceEntryId = saved.workouts.single().entries.single().id
        val occurrence = repository.insertOccurrences(
            listOf(occurrence(id, saved.workouts.single().id, 0, 20_000L, sourceEntryId))
        ).single()
        val original = occurrence.entries.single()

        repository.replaceOccurrenceEntries(
            occurrence.id,
            listOf(
                original,
                OccurrenceEntry(
                    sourceActivationEntryId = sourceEntryId,
                    position = 1,
                    exerciseId = "overhead-press",
                    exerciseName = "Overhead Press",
                    movementPattern = MovementPattern.VERTICAL_PUSH,
                    sets = 3,
                    reps = 8
                )
            )
        )

        val reloaded = requireNotNull(repository.getOccurrence(occurrence.id))
        assertEquals(2, reloaded.entries.size)
        assertEquals(original.id, reloaded.entries[0].id)
        assertEquals("overhead-press", reloaded.entries[1].exerciseId)
        assertTrue(reloaded.entries[1].id != 0L)
    }

    @Test
    fun deletingOccurrencesCascadesTheirEntries() = runTest {
        val id = repository.insertActivation(activation())
        val saved = requireNotNull(repository.getActivation(id))
        val sourceEntryId = saved.workouts.single().entries.single().id
        repository.insertOccurrences(
            listOf(occurrence(id, saved.workouts.single().id, 0, 20_000L, sourceEntryId))
        )

        repository.deleteOccurrencesForActivation(id)

        assertTrue(repository.occurrences(id).isEmpty())
        assertEquals(
            0,
            database.trainingScheduleQueries.selectAllOccurrenceEntries().executeAsList().size
        )
    }

    @Test
    fun templateReferenceGuardReflectsStoredActivations() = runTest {
        assertFalse(repository.isTemplateReferenced(7L))
        repository.insertActivation(activation())
        assertTrue(repository.isTemplateReferenced(7L))
        assertFalse(repository.isTemplateReferenced(8L))
    }

    @Test
    fun deletingAPlanDetachesTheActivationProvenance() = runTest {
        database.planHistoryQueries.insertPlan(
            engineId = "DETERMINISTIC",
            acceptedAt = 1L,
            weekNumber = 1L,
            cycleNumber = 1L
        )
        val planId = database.planHistoryQueries.lastInsertedPlanId().executeAsOne()
        val activationId = repository.insertActivation(activation().copy(sourcePlanId = planId))
        assertEquals(planId, requireNotNull(repository.getActivation(activationId)).sourcePlanId)

        SqlDelightPlanHistoryRepository(database).delete(planId)

        assertNull(requireNotNull(repository.getActivation(activationId)).sourcePlanId)
    }

    private fun activation() = TrainingActivation(
        templateId = 7L,
        templateRevision = 3,
        sourcePlanId = null,
        name = "Upper block",
        createdAtMillis = 1_000L,
        startEpochDay = 20_000L,
        mode = ScheduleMode.WEEKDAY,
        weekdays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY),
        status = ActivationStatus.ACTIVE,
        workouts = listOf(
            ActivationWorkout(
                position = 0,
                name = "Day 1",
                focus = SplitFocus.PUSH,
                entries = listOf(
                    ActivationEntry(
                        position = 0,
                        exerciseId = "bench-press",
                        exerciseName = "Barbell Bench Press",
                        movementPattern = MovementPattern.HORIZONTAL_PUSH,
                        requiredEquipment = setOf(EquipmentTag("barbell")),
                        involvements = mapOf(MuscleGroup.CHEST_UPPER to 0.6),
                        isUnilateral = false,
                        sets = 3,
                        reps = 8,
                        weightKg = 82.5
                    )
                )
            )
        )
    )

    private fun occurrence(
        activationId: Long,
        activationWorkoutId: Long,
        queuePosition: Int,
        scheduledEpochDay: Long,
        sourceEntryId: Long
    ) = WorkoutOccurrence(
        activationId = activationId,
        activationWorkoutId = activationWorkoutId,
        queuePosition = queuePosition,
        scheduledEpochDay = scheduledEpochDay,
        status = OccurrenceStatus.PENDING,
        entries = listOf(
            OccurrenceEntry(
                sourceActivationEntryId = sourceEntryId,
                position = 0,
                exerciseId = "bench-press",
                exerciseName = "Barbell Bench Press",
                movementPattern = MovementPattern.HORIZONTAL_PUSH,
                sets = 3,
                reps = 8
            )
        )
    )
}
