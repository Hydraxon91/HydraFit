package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.engine.SlotProgressionContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class SqlDelightSlotProgressionContextRepositoryTest {

    private lateinit var driver: SqlDriver
    private lateinit var database: HydraFitDatabase
    private lateinit var repository: SqlDelightSlotProgressionContextRepository

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        database = HydraFitDatabase(driver)
        SeedExerciseCatalog(database).seed()
        repository = SqlDelightSlotProgressionContextRepository(database)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun theDerivedKeyIsStableAcrossTwoActivationsOfTheSameSlot() = runTest {
        val first = seedBlock(templateId = 7L, sourcePlanId = null)
        val firstEntry = first.entries.getValue(0)
        val firstSet = seedSet(firstEntry, performedAt = 1L, sessionId = "first")
        val firstContext = contextFor(first.occurrenceId, firstEntry)

        assertEquals("T7:0:0", firstContext.trackKey)
        assertEquals(setOf(firstSet), firstContext.history.sets.map { it.id }.toSet())

        val second = seedBlock(templateId = 7L, sourcePlanId = null)
        val secondEntry = second.entries.getValue(0)
        val secondSet = seedSet(secondEntry, performedAt = 2L, sessionId = "second")
        val secondContext = contextFor(second.occurrenceId, secondEntry)

        assertEquals(firstContext.trackKey, secondContext.trackKey)
        assertEquals(
            setOf(firstSet, secondSet),
            secondContext.history.sets.map { it.id }.toSet()
        )
    }

    @Test
    fun aSecondSlotWithTheSameExerciseHasADistinctKeyAndHistory() = runTest {
        val block = seedBlock(templateId = 7L, sourcePlanId = null)
        val slotA = block.entries.getValue(0)
        val slotB = block.entries.getValue(1)
        val setA = seedSet(slotA, performedAt = 1L, sessionId = "a")
        val setB = seedSet(slotB, performedAt = 2L, sessionId = "b")

        val a = contextFor(block.occurrenceId, slotA)
        val b = contextFor(block.occurrenceId, slotB)

        assertEquals("T7:0:0", a.trackKey)
        assertEquals("T7:0:1", b.trackKey)
        assertEquals(setOf(setA), a.history.sets.map { it.id }.toSet())
        assertEquals(setOf(setB), b.history.sets.map { it.id }.toSet())
        assertEquals(setOf(setA), a.history.legacyTargets.keys)
    }

    @Test
    fun aPlanBackedSlotUsesThePlanIdentity() = runTest {
        val block = seedBlock(templateId = null, sourcePlanId = 9L)
        val entry = block.entries.getValue(0)
        seedSet(entry, performedAt = 1L, sessionId = "plan")

        val context = contextFor(block.occurrenceId, entry)

        assertEquals("P9:0:0", context.trackKey)
        assertEquals(1, context.history.sets.size)
    }

    @Test
    fun anUnattributedOccurrenceEntryHasNoKeyOrEvidence() = runTest {
        val block = seedBlock(templateId = 7L, sourcePlanId = null)
        val attributed = block.entries.getValue(0)
        seedSet(attributed, performedAt = 1L, sessionId = "attributed")
        val legacyEntry = seedOccurrenceEntry(
            block = block,
            sourceActivationEntryId = null,
            position = 2
        )
        seedSet(legacyEntry, performedAt = 2L, sessionId = "legacy")

        val context = contextFor(block.occurrenceId, legacyEntry)

        assertNull(context.trackKey)
        assertEquals(emptyList(), context.history.sets)
        assertEquals(emptyMap(), context.history.legacyTargets)
    }

    @Test
    fun aDifferentTemplateIdIsExcluded() = runTest {
        val own = seedBlock(templateId = 7L, sourcePlanId = null)
        val ownEntry = own.entries.getValue(0)
        val ownSet = seedSet(ownEntry, performedAt = 1L, sessionId = "own")
        val other = seedBlock(templateId = 8L, sourcePlanId = null)
        val foreignSet = seedSet(other.entries.getValue(0), performedAt = 2L, sessionId = "other")

        val context = contextFor(own.occurrenceId, ownEntry)

        assertEquals(setOf(ownSet), context.history.sets.map { it.id }.toSet())
        assertEquals(setOf(ownSet), context.history.legacyTargets.keys)
        assertEquals(false, foreignSet in context.history.legacyTargets)
    }

    @Test
    fun aDifferentSourcePlanIdIsExcluded() = runTest {
        val own = seedBlock(templateId = null, sourcePlanId = 9L)
        val ownEntry = own.entries.getValue(0)
        val ownSet = seedSet(ownEntry, performedAt = 1L, sessionId = "own")
        val other = seedBlock(templateId = null, sourcePlanId = 10L)
        val foreignSet = seedSet(other.entries.getValue(0), performedAt = 2L, sessionId = "other")

        val context = contextFor(own.occurrenceId, ownEntry)

        assertEquals(setOf(ownSet), context.history.sets.map { it.id }.toSet())
        assertEquals(setOf(ownSet), context.history.legacyTargets.keys)
        assertEquals(false, foreignSet in context.history.legacyTargets)
    }

    @Test
    fun aDifferentActivationWorkoutPositionIsExcludedAtTheSameEntryPosition() = runTest {
        val own = seedBlock(templateId = 7L, sourcePlanId = null, workoutPosition = 0L)
        val ownEntry = own.entries.getValue(0)
        val ownSet = seedSet(ownEntry, performedAt = 1L, sessionId = "own")
        val other = seedBlock(templateId = 7L, sourcePlanId = null, workoutPosition = 1L)
        val foreignSet = seedSet(other.entries.getValue(0), performedAt = 2L, sessionId = "other")

        val context = contextFor(own.occurrenceId, ownEntry)

        assertEquals(setOf(ownSet), context.history.sets.map { it.id }.toSet())
        assertEquals(setOf(ownSet), context.history.legacyTargets.keys)
        assertEquals(false, foreignSet in context.history.legacyTargets)
    }

    private suspend fun contextFor(occurrenceId: Long, entry: SeededEntry): SlotProgressionContext =
        repository.inputsFor(occurrenceId).contexts.getValue(entry.occurrenceEntryId)

    private fun seedBlock(
        templateId: Long?,
        sourcePlanId: Long?,
        workoutPosition: Long = 0L
    ): SeededBlock {
        database.trainingScheduleQueries.insertActivation(
            templateId = templateId,
            templateRevision = null,
            sourcePlanId = sourcePlanId,
            name = "Block",
            createdAtMillis = 0L,
            startEpochDay = 0L,
            mode = "SEQUENCE",
            weekdayMask = 0L,
            status = "ACTIVE",
            weekNumber = 1L,
            cycleNumber = 1L,
            endedAtMillis = null,
            revision = 1L
        )
        val activationId = database.trainingScheduleQueries.lastInsertedActivationId()
            .executeAsOne()
        database.trainingScheduleQueries.insertActivationWorkout(
            activationId = activationId,
            sourceWorkoutId = null,
            position = workoutPosition,
            name = "Day",
            focus = null
        )
        val workoutId = database.trainingScheduleQueries.lastInsertedActivationWorkoutId()
            .executeAsOne()
        val sourceEntries = listOf(0L, 1L).associateWith { position ->
            database.trainingScheduleQueries.insertActivationEntry(
                workoutId = workoutId,
                position = position,
                exerciseId = "barbell-bench-press",
                exerciseName = "Bench Press",
                movementPattern = "HORIZONTAL_PUSH",
                requiredEquipment = "",
                involvements = null,
                isUnilateral = 0L,
                sets = 3L,
                reps = 5L,
                durationSeconds = null,
                weightKg = 80.0,
                loadCapability = "EXTERNAL",
                loadKind = "EXTERNAL",
                progressionPolicy = null,
                setTargets = null,
                distanceMeters = null,
                forceNewtons = null
            )
            database.trainingScheduleQueries.lastInsertedActivationEntryId().executeAsOne()
        }
        database.trainingScheduleQueries.insertOccurrence(
            activationId = activationId,
            activationWorkoutId = workoutId,
            queuePosition = 0L,
            scheduledEpochDay = null,
            notBeforeEpochDay = null,
            startedAtMillis = null,
            resolvedAtMillis = null,
            status = "PENDING",
            revision = 1L
        )
        val occurrenceId = database.trainingScheduleQueries.lastInsertedOccurrenceId()
            .executeAsOne()
        val entries = sourceEntries.mapValues { (position, sourceId) ->
            seedOccurrenceEntry(
                block = SeededBlock(occurrenceId),
                sourceActivationEntryId = sourceId,
                position = position
            )
        }
        return SeededBlock(occurrenceId, entries)
    }

    private fun seedOccurrenceEntry(
        block: SeededBlock,
        sourceActivationEntryId: Long?,
        position: Long
    ): SeededEntry {
        database.trainingScheduleQueries.insertOccurrenceEntry(
            occurrenceId = block.occurrenceId,
            sourceActivationEntryId = sourceActivationEntryId,
            position = position,
            exerciseId = "barbell-bench-press",
            exerciseName = "Bench Press",
            movementPattern = "HORIZONTAL_PUSH",
            requiredEquipment = "",
            involvements = null,
            isUnilateral = 0L,
            sets = 3L,
            reps = 5L,
            durationSeconds = null,
            weightKg = 80.0,
            loadCapability = "EXTERNAL",
            loadKind = "EXTERNAL",
            remainingDisposition = null,
            terminalRemainingSets = null,
            progressionPolicy = null,
            setTargets = null,
            distanceMeters = null,
            forceNewtons = null
        )
        return SeededEntry(
            occurrenceId = block.occurrenceId,
            occurrenceEntryId = database.trainingScheduleQueries.lastInsertedOccurrenceEntryId()
                .executeAsOne()
        )
    }

    private fun seedSet(entry: SeededEntry, performedAt: Long, sessionId: String): Long {
        database.workoutSessionQueries.insertSession(sessionId, performedAt, performedAt + 1L, 0L)
        database.workoutLogQueries.insertSetWithTimingProvenance(
            exerciseId = "barbell-bench-press",
            reps = 5L,
            weightKg = 80.0,
            performedAt = performedAt,
            isWarmup = 0L,
            involvements = null,
            weekNumber = null,
            cycleNumber = null,
            dayIndex = null,
            rir = null,
            sessionId = sessionId,
            loadKind = "EXTERNAL",
            timingProvenance = "UNKNOWN",
            startedAtElapsedMillis = null,
            completedAtElapsedMillis = null,
            durationSeconds = null,
            prescriptionSnapshot = null
        )
        val setId = database.workoutLogQueries.lastInsertedSetId().executeAsOne()
        database.workoutLogQueries.assignOccurrence(
            entry.occurrenceId,
            entry.occurrenceEntryId,
            setId
        )
        return setId
    }

    private data class SeededBlock(
        val occurrenceId: Long,
        val entries: Map<Long, SeededEntry> = emptyMap()
    )

    private data class SeededEntry(val occurrenceId: Long, val occurrenceEntryId: Long)
}
