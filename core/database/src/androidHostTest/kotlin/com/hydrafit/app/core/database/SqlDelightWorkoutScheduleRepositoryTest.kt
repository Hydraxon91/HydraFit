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
import com.hydrafit.app.core.domain.schedule.ScheduleException
import com.hydrafit.app.core.domain.schedule.ScheduleMode
import com.hydrafit.app.core.domain.schedule.TrainingActivation
import com.hydrafit.app.core.domain.schedule.WorkoutScheduleState
import com.hydrafit.app.core.domain.time.DayOfWeek
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    fun acceptAndActivateWritesActivationOccurrencesAndCursorAtomically() = runTest {
        val id = repository.acceptAndActivate(
            acceptedPlan = null,
            activation = activation(),
            scheduledEpochDays = listOf(20_000L, 20_002L),
            replaceActive = false
        )

        val reloaded = requireNotNull(repository.getActivation(id))
        assertEquals("Upper block", reloaded.name)
        assertEquals(ScheduleMode.WEEKDAY, reloaded.mode)
        assertEquals(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY), reloaded.weekdays)
        assertEquals(ActivationStatus.ACTIVE, reloaded.status)
        assertEquals(7L, reloaded.templateId)
        assertEquals(3, reloaded.templateRevision)
        assertEquals(SplitFocus.PUSH, reloaded.workouts[0].focus)
        val slot = reloaded.workouts[0].entries.single()
        assertEquals("bench-press", slot.exerciseId)
        assertEquals("Barbell Bench Press", slot.exerciseName)
        assertEquals(setOf(EquipmentTag("barbell")), slot.requiredEquipment)
        assertEquals(mapOf(MuscleGroup.CHEST_UPPER to 0.6), slot.involvements)
        assertTrue(slot.isUnilateral.not())
        assertEquals(82.5, slot.weightKg)

        val occurrences = repository.occurrences(id)
        assertEquals(listOf(0, 1), occurrences.map { it.queuePosition })
        assertEquals(listOf(20_000L, 20_002L), occurrences.map { it.scheduledEpochDay })
        assertTrue(occurrences.all { it.status == OccurrenceStatus.PENDING })
        assertEquals(occurrences[0].id, repository.scheduleState().selectedOccurrenceId)
        assertEquals(id, repository.scheduleState().activeActivationId)

        val copiedEntry = occurrences[0].entries.single()
        assertEquals(slot.id, copiedEntry.sourceActivationEntryId)
        assertEquals("bench-press", copiedEntry.exerciseId)
        assertNull(copiedEntry.remainingDisposition)
    }

    @Test
    fun acceptAndActivateRejectsASecondBlockUnlessReplacing() = runTest {
        val first = repository.acceptAndActivate(
            acceptedPlan = null,
            activation = activation(),
            scheduledEpochDays = listOf(20_000L, 20_002L),
            replaceActive = false
        )

        assertFailsWith<ScheduleException> {
            repository.acceptAndActivate(
                acceptedPlan = null,
                activation = activation(),
                scheduledEpochDays = listOf(20_010L, 20_012L),
                replaceActive = false
            )
        }

        // The rejected write leaves the first block fully intact and writes nothing new.
        assertEquals(
            1,
            database.trainingScheduleQueries.selectAllActivations().executeAsList().size
        )
        assertEquals(
            2,
            database.trainingScheduleQueries.selectAllOccurrences().executeAsList().size
        )
        assertEquals(first, repository.scheduleState().activeActivationId)

        val second = repository.acceptAndActivate(
            acceptedPlan = null,
            activation = activation(),
            scheduledEpochDays = listOf(20_010L, 20_012L),
            replaceActive = true
        )

        assertEquals(ActivationStatus.CANCELLED, repository.getActivation(first)?.status)
        assertEquals(second, repository.scheduleState().activeActivationId)
    }

    @Test
    fun resolveOccurrenceAdvancesTheCursorAndRejectsStaleRevisions() = runTest {
        val id = repository.acceptAndActivate(
            acceptedPlan = null,
            activation = activation(),
            scheduledEpochDays = listOf(20_000L, 20_002L),
            replaceActive = false
        )
        val first = repository.occurrences(id).first()

        val state = repository.resolveOccurrence(
            occurrenceId = first.id,
            expectedRevision = first.revision,
            status = OccurrenceStatus.FINISHED,
            resolvedAtMillis = 500L,
            entries = first.entries
        )

        assertEquals(OccurrenceStatus.FINISHED, repository.getOccurrence(first.id)?.status)
        assertEquals(500L, repository.getOccurrence(first.id)?.resolvedAtMillis)
        assertEquals(first.id + 1, state.selectedOccurrenceId)

        assertFailsWith<ScheduleException> {
            repository.resolveOccurrence(
                occurrenceId = first.id,
                expectedRevision = first.revision,
                status = OccurrenceStatus.FINISHED,
                resolvedAtMillis = 600L,
                entries = first.entries
            )
        }

        // The stale attempt changes neither the occurrence nor the cursor.
        assertEquals(OccurrenceStatus.FINISHED, repository.getOccurrence(first.id)?.status)
        assertEquals(500L, repository.getOccurrence(first.id)?.resolvedAtMillis)
        assertEquals(first.id + 1, repository.scheduleState().selectedOccurrenceId)
    }

    @Test
    fun resolveOccurrencePersistsRemainingDisposition() = runTest {
        val id = repository.acceptAndActivate(
            acceptedPlan = null,
            activation = activation(),
            scheduledEpochDays = listOf(20_000L, 20_002L),
            replaceActive = false
        )
        val occurrence = repository.occurrences(id).first()
        val entries = occurrence.entries.map {
            it.copy(remainingDisposition = RemainingDisposition.OMITTED, terminalRemainingSets = 2)
        }

        repository.resolveOccurrence(
            occurrenceId = occurrence.id,
            expectedRevision = occurrence.revision,
            status = OccurrenceStatus.FINISHED_PARTIAL,
            resolvedAtMillis = 500L,
            entries = entries
        )

        val reloaded = requireNotNull(repository.getOccurrence(occurrence.id))
        assertEquals(RemainingDisposition.OMITTED, reloaded.entries[0].remainingDisposition)
        assertEquals(2, reloaded.entries[0].terminalRemainingSets)
    }

    @Test
    fun resolveOccurrenceRejectsAnOccurrenceFromANonActiveBlock() = runTest {
        val first = repository.acceptAndActivate(
            acceptedPlan = null,
            activation = activation(),
            scheduledEpochDays = listOf(20_000L, 20_002L),
            replaceActive = false
        )
        val foreign = repository.occurrences(first).first()

        repository.acceptAndActivate(
            acceptedPlan = null,
            activation = activation(),
            scheduledEpochDays = listOf(20_010L, 20_012L),
            replaceActive = true
        )

        assertFailsWith<ScheduleException> {
            repository.resolveOccurrence(
                occurrenceId = foreign.id,
                expectedRevision = foreign.revision,
                status = OccurrenceStatus.FINISHED,
                resolvedAtMillis = 500L,
                entries = foreign.entries
            )
        }
    }

    @Test
    fun updatingAnOccurrencePersistsStatusAndCompletionFields() = runTest {
        val id = repository.acceptAndActivate(
            acceptedPlan = null,
            activation = activation(),
            scheduledEpochDays = listOf(20_000L, 20_002L),
            replaceActive = false
        )
        val occurrence = repository.occurrences(id).first()

        repository.updateOccurrence(
            occurrence.copy(
                status = OccurrenceStatus.IN_PROGRESS,
                startedAtMillis = 111L,
                resolvedAtMillis = 222L,
                revision = 2
            )
        )

        val reloaded = requireNotNull(repository.getOccurrence(occurrence.id))
        assertEquals(OccurrenceStatus.IN_PROGRESS, reloaded.status)
        assertEquals(111L, reloaded.startedAtMillis)
        assertEquals(222L, reloaded.resolvedAtMillis)
        assertEquals(2, reloaded.revision)
    }

    @Test
    fun replacingOccurrenceEntriesPreservesKeptIdsAndDeletesRemovedRows() = runTest {
        val id = repository.acceptAndActivate(
            acceptedPlan = null,
            activation = activation(),
            scheduledEpochDays = listOf(20_000L, 20_002L),
            replaceActive = false
        )
        val occurrence = repository.occurrences(id).first()
        val original = occurrence.entries.single()

        repository.replaceOccurrenceEntries(
            occurrence.id,
            listOf(
                OccurrenceEntry(
                    position = 0,
                    exerciseId = "overhead-press",
                    exerciseName = "Overhead Press",
                    movementPattern = MovementPattern.VERTICAL_PUSH,
                    sets = 3,
                    reps = 8
                )
            )
        )

        val reloaded = requireNotNull(repository.getOccurrence(occurrence.id))
        assertEquals(1, reloaded.entries.size)
        assertTrue(reloaded.entries[0].id != original.id)
        assertEquals("overhead-press", reloaded.entries[0].exerciseId)
    }

    @Test
    fun replacingOccurrenceEntriesInsertsNewRows() = runTest {
        val id = repository.acceptAndActivate(
            acceptedPlan = null,
            activation = activation(),
            scheduledEpochDays = listOf(20_000L, 20_002L),
            replaceActive = false
        )
        val occurrence = repository.occurrences(id).first()
        val original = occurrence.entries.single()

        repository.replaceOccurrenceEntries(
            occurrence.id,
            listOf(
                original.copy(sets = 5),
                OccurrenceEntry(
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
        assertEquals(5, reloaded.entries[0].sets)
        assertEquals("overhead-press", reloaded.entries[1].exerciseId)
        assertTrue(reloaded.entries[1].id != 0L)
    }

    @Test
    fun deletingOccurrencesCascadesTheirEntries() = runTest {
        val id = repository.acceptAndActivate(
            acceptedPlan = null,
            activation = activation(),
            scheduledEpochDays = listOf(20_000L, 20_002L),
            replaceActive = false
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
        repository.acceptAndActivate(
            acceptedPlan = null,
            activation = activation(),
            scheduledEpochDays = listOf(20_000L, 20_002L),
            replaceActive = false
        )
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
        val activationId = repository.acceptAndActivate(
            acceptedPlan = null,
            activation = activation().copy(sourcePlanId = planId),
            scheduledEpochDays = listOf(20_000L, 20_002L),
            replaceActive = false
        )
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
            ),
            ActivationWorkout(
                position = 1,
                name = "Day 2",
                focus = SplitFocus.PULL,
                entries = listOf(
                    ActivationEntry(
                        position = 0,
                        exerciseId = "cable-row",
                        exerciseName = "Cable Row",
                        movementPattern = MovementPattern.HORIZONTAL_PULL,
                        requiredEquipment = setOf(EquipmentTag("cable")),
                        involvements = mapOf(MuscleGroup.LATS to 0.7),
                        isUnilateral = false,
                        sets = 3,
                        reps = 10,
                        weightKg = 60.0
                    )
                )
            )
        )
    )
}
