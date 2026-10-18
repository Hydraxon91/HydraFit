package com.hydrafit.app.core.database

import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.SqlDriver
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Migration 53 -> 54 makes a prescription slot measurable by elapsed whole seconds: `routineEntry`,
 * `activationEntry` and `occurrenceEntry` accept NULL reps with a stored duration. SQLite cannot
 * drop NOT NULL in place, so all three tables are rebuilt: every stored row must keep its id, every
 * snapshot column and its link, and each table's index must be recreated.
 * A pre-existing (counted) row keeps its reps verbatim and gains a NULL duration; a new timed slot
 * stores its seconds with NULL reps.
 */
class PrescriptionDurationMigrationTest {

    private lateinit var driver: SqlDriver

    @AfterTest
    fun tearDown() {
        if (::driver.isInitialized) driver.close()
    }

    @Test
    fun migrationRebuildKeepsEveryStoredColumnAndLink() {
        driver = HistoricalDatabaseFixtures.v1()
        // 53 is the pre-timed-prescription schema: reps is NOT NULL and no duration column exists.
        HydraFitDatabaseSchema.migrate(driver, 1, 53)
        insertCountedSlotsAt53()
        assertEquals(3L, indexCount())
        assertEquals(41L, sequenceHighWater("routineEntry"))

        HydraFitDatabaseSchema.migrate(driver, 53, HydraFitDatabaseSchema.version)

        assertEquals(3L, indexCount())
        val database = HydraFitDatabase(driver)

        val routine = database.routineTemplateQueries.selectEntriesForWorkout(7L).executeAsOne()
        assertEquals(41L, routine.id)
        assertEquals(1L, routine.position)
        assertEquals("back-squat", routine.exerciseId)
        assertEquals(5L, routine.sets)
        assertEquals(6L, routine.reps)
        assertNull(routine.durationSeconds)
        assertEquals(100.0, routine.weightKg)
        assertEquals("EXTERNAL", routine.loadKind)

        val activation =
            database.trainingScheduleQueries.selectActivationEntriesForWorkout(9L).executeAsOne()
        assertEquals(51L, activation.id)
        assertEquals("Back Squat", activation.exerciseName)
        assertEquals("SQUAT", activation.movementPattern)
        assertEquals("BARBELL", activation.requiredEquipment)
        assertEquals("QUADS:1.0", activation.involvements)
        assertEquals(0L, activation.isUnilateral)
        assertEquals(3L, activation.sets)
        assertEquals(5L, activation.reps)
        assertNull(activation.durationSeconds)
        assertEquals(100.0, activation.weightKg)
        assertEquals("EXTERNAL", activation.loadCapability)
        assertEquals("EXTERNAL", activation.loadKind)

        val occurrence = database.trainingScheduleQueries
            .selectOccurrenceEntriesForOccurrence(11L).executeAsOne()
        assertEquals(61L, occurrence.id)
        assertEquals(51L, occurrence.sourceActivationEntryId)
        assertEquals("Back Squat", occurrence.exerciseName)
        assertEquals(3L, occurrence.sets)
        assertEquals(5L, occurrence.reps)
        assertNull(occurrence.durationSeconds)
        assertEquals("OMITTED", occurrence.remainingDisposition)
        assertEquals(2L, occurrence.terminalRemainingSets)

        // The rebuilt tables accept a timed slot: NULL reps with a positive stored duration.
        database.routineTemplateQueries.insertEntry(
            workoutId = 7L,
            position = 2,
            exerciseId = "plank",
            sets = 3,
            reps = null,
            durationSeconds = 45,
            weightKg = null,
            loadKind = "BODYWEIGHT",
            progressionPolicy = null
        )
        val timedRoutine = database.routineTemplateQueries
            .selectEntriesForWorkout(7L).executeAsList().first { it.durationSeconds != null }
        assertNull(timedRoutine.reps)
        assertEquals(45L, timedRoutine.durationSeconds)
    }

    @Test
    fun generatedRebuildAloneLosesDeletedAllocationHistory() {
        driver = HistoricalDatabaseFixtures.v1()
        HydraFitDatabaseSchema.migrate(driver, 1, 53)
        val tables = listOf("routineEntry", "activationEntry", "occurrenceEntry")
        tables.forEach { table ->
            insertCountedSlotsAt53For(table, explicitId = 1000)
            HistoricalDatabaseFixtures.exec(driver, "DELETE FROM $table")
            assertEquals(1000L, sequenceHighWater(table))
        }
        HydraFitDatabase.Schema.migrate(driver, 53, HydraFitDatabaseSchema.version)
        tables.forEach { table ->
            insertCountedSlotsAt53For(table, explicitId = null)
            assertEquals(
                1L,
                HistoricalDatabaseFixtures.long(driver, "SELECT max(id) FROM $table")
            )
        }
    }

    @Test
    fun rebuildPreservesDeletedIdsBeforeCallbacksAndSubsequentInserts() {
        verifyDeletedIds(53, keepRows = false)
    }

    @Test
    fun multiHopUpgradePreservesDeletedIdsWithSurvivingRows() {
        verifyDeletedIds(52, keepRows = true)
    }

    private fun verifyDeletedIds(oldVersion: Long, keepRows: Boolean) {
        driver = HistoricalDatabaseFixtures.v1()
        HydraFitDatabaseSchema.migrate(driver, 1, oldVersion)
        insertCountedSlotsAt53()
        val tables = listOf("routineEntry", "activationEntry", "occurrenceEntry")
        tables.forEach { table ->
            insertCountedSlotsAt53For(table, explicitId = 1000)
            if (!keepRows) {
                HistoricalDatabaseFixtures.exec(driver, "DELETE FROM $table WHERE id < 1000")
            }
            HistoricalDatabaseFixtures.exec(driver, "DELETE FROM $table WHERE id = 1000")
            assertEquals(1000L, sequenceHighWater(table))
        }
        val callbackMarks = mutableListOf<Long>()
        HydraFitDatabaseSchema.migrate(
            driver,
            oldVersion,
            HydraFitDatabaseSchema.version,
            AfterVersion(53) {
                tables.forEach { callbackMarks += sequenceHighWater(it) }
            }
        )
        assertEquals(listOf(1000L, 1000L, 1000L), callbackMarks)
        tables.forEach { table ->
            assertEquals(1000L, sequenceHighWater(table))
            insertCountedSlotsAt53For(table, explicitId = null)
            assertEquals(
                1001L,
                HistoricalDatabaseFixtures.long(driver, "SELECT max(id) FROM $table")
            )
        }
    }

    private fun insertCountedSlotsAt53For(table: String, explicitId: Long?) {
        val columns = when (table) {
            "routineEntry" -> "workoutId, position, exerciseId, sets, reps"
            "activationEntry" ->
                "workoutId, position, exerciseId, exerciseName, " +
                    "movementPattern, requiredEquipment, sets, reps"
            else ->
                "occurrenceId, position, exerciseId, exerciseName, " +
                    "movementPattern, requiredEquipment, sets, reps"
        }
        val values = if (table == "routineEntry") {
            "7, 2, 'plank', 3, 5"
        } else {
            "9, 2, 'plank', 'Plank', 'CORE', '', 3, 5"
        }
        HistoricalDatabaseFixtures.exec(
            driver,
            if (explicitId != null) {
                "INSERT INTO $table(id, $columns) VALUES ($explicitId, $values)"
            } else {
                "INSERT INTO $table($columns) VALUES ($values)"
            }
        )
    }

    private fun sequenceHighWater(table: String) = HistoricalDatabaseFixtures.long(
        driver,
        "SELECT COALESCE((SELECT seq FROM sqlite_sequence WHERE name = '$table'), 0)"
    )

    @Test
    fun freshSchemaStoresTimedAndCountedSlots() {
        driver = HistoricalDatabaseFixtures.empty()
        HydraFitDatabaseSchema.create(driver)
        val database = HydraFitDatabase(driver)

        database.routineTemplateQueries.insertEntry(
            workoutId = 7L,
            position = 0,
            exerciseId = "plank",
            sets = 3,
            reps = null,
            durationSeconds = 60,
            weightKg = null,
            loadKind = "BODYWEIGHT",
            progressionPolicy = null
        )
        database.routineTemplateQueries.insertEntry(
            workoutId = 7L,
            position = 1,
            exerciseId = "back-squat",
            sets = 3,
            reps = 5,
            durationSeconds = null,
            weightKg = null,
            loadKind = "EXTERNAL",
            progressionPolicy = null
        )

        val entries = database.routineTemplateQueries.selectEntriesForWorkout(7L).executeAsList()
        val timed = entries.first { it.durationSeconds != null }
        assertNull(timed.reps)
        assertEquals(60L, timed.durationSeconds)
        val counted = entries.first { it.durationSeconds == null }
        assertEquals(5L, counted.reps)
        assertEquals(3L, indexCount())
    }

    /** The pre-54 schema stores reps as NOT NULL, so only those columns are needed here. */
    private fun insertCountedSlotsAt53() {
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO routineEntry(id, workoutId, position, exerciseId, sets, reps, weightKg, " +
                "loadKind) VALUES (41, 7, 1, 'back-squat', 5, 6, 100.0, 'EXTERNAL')"
        )
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO activationEntry(id, workoutId, position, exerciseId, exerciseName, " +
                "movementPattern, requiredEquipment, involvements, isUnilateral, sets, reps, " +
                "weightKg, loadCapability, loadKind) " +
                "VALUES (51, 9, 0, 'back-squat', 'Back Squat', " +
                "'SQUAT', 'BARBELL', 'QUADS:1.0', 0, 3, 5, 100.0, 'EXTERNAL', 'EXTERNAL')"
        )
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO occurrenceEntry(id, occurrenceId, sourceActivationEntryId, position, " +
                "exerciseId, exerciseName, movementPattern, requiredEquipment, involvements, " +
                "isUnilateral, sets, reps, weightKg, remainingDisposition, " +
                "terminalRemainingSets, " +
                "loadCapability, loadKind) VALUES (61, 11, 51, 0, 'back-squat', 'Back Squat', " +
                "'SQUAT', 'BARBELL', 'QUADS:1.0', 0, 3, 5, 100.0, 'OMITTED', 2, 'EXTERNAL', " +
                "'EXTERNAL')"
        )
    }

    private fun indexCount() = HistoricalDatabaseFixtures.long(
        driver,
        "SELECT count(*) FROM sqlite_master WHERE type = 'index' AND name IN " +
            "('routineEntry_workoutId', 'activationEntry_workoutId', 'occurrenceEntry_occurrenceId')"
    )
}
