package com.hydrafit.app.core.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Migration 47 -> 48 adds the `(occurrenceId, exerciseId)` index the summary's live occurrence read
 * relies on. It is a schema addition only: it must exist after the chain, must not change stored
 * sets, and its query plan must seek one occurrence without a scan or a temporary B-tree.
 */
class WorkoutSetOccurrenceIndexMigrationTest {
    private lateinit var driver: SqlDriver

    @AfterTest
    fun tearDown() {
        if (::driver.isInitialized) driver.close()
    }

    @Test
    fun migrationPreservesRowsAndAddsOccurrenceExerciseIdIndex() {
        driver = HistoricalDatabaseFixtures.v1()
        HydraFitDatabase.Schema.migrate(driver, 1, 47)
        val database = HydraFitDatabase(driver)
        database.exerciseQueries.insert("squat", "Squat", "BARBELL", "SQUAT", "EXTERNAL")
        // Schema 53 adds `durationSeconds`, so the generated set queries can no longer run against
        // the 47 schema; write and read the stored row with raw SQL that matches it.
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO workoutSet(exerciseId, reps, weightKg, performedAt, isWarmup, loadKind) " +
                "VALUES ('squat', 10, 60.0, 100, 0, 'EXTERNAL')"
        )
        database.workoutLogQueries.assignOccurrence(
            7L,
            null,
            database.workoutLogQueries.lastInsertedSetId().executeAsOne()
        )
        val before = storedSets()
        assertEquals(0L, indexCount())

        HydraFitDatabase.Schema.migrate(driver, 47, 48)

        assertEquals(before, storedSets())
        assertEquals(1L, indexCount())
        assertIndexedOccurrenceExerciseIds()
    }

    @Test
    fun freshSchemaAlsoUsesTheOccurrenceIndex() {
        driver = HistoricalDatabaseFixtures.empty()
        HydraFitDatabase.Schema.create(driver)
        assertEquals(1L, indexCount())
        assertIndexedOccurrenceExerciseIds()
    }

    private fun indexCount() = HistoricalDatabaseFixtures.long(
        driver,
        "SELECT count(*) FROM sqlite_master " +
            "WHERE type = 'index' AND name = 'workoutSet_occurrenceId_exerciseId'"
    )

    /** Every stored set column as text: a pre-53 database has no `durationSeconds` to select. */
    private fun storedSets(): List<List<String?>> = driver.executeQuery(
        identifier = null,
        sql = "SELECT id, exerciseId, reps, weightKg, performedAt, isWarmup, involvements, " +
            "weekNumber, cycleNumber, dayIndex, rir, sessionId, occurrenceId, occurrenceEntryId, " +
            "loadKind, timingProvenance, startedAtElapsedMillis, completedAtElapsedMillis " +
            "FROM workoutSet ORDER BY performedAt, id",
        mapper = { cursor ->
            val rows = mutableListOf<List<String?>>()
            while (cursor.next().value) rows += (0 until 18).map { cursor.getString(it) }
            QueryResult.Value(rows)
        },
        parameters = 0
    ).value

    private fun assertIndexedOccurrenceExerciseIds() {
        // The live read must seek only the occurrence's index entries and satisfy DISTINCT and
        // ORDER BY from the index order, without a scan of the set index or a temporary B-tree.
        val plan = strings(
            "EXPLAIN QUERY PLAN " +
                "SELECT DISTINCT exerciseId FROM workoutSet WHERE occurrenceId = 7 " +
                "ORDER BY exerciseId",
            3
        ).joinToString("\n")
        assertTrue(plan.contains("workoutSet_occurrenceId_exerciseId"), plan)
        assertTrue(plan.contains("(occurrenceId=?)"), plan)
        assertFalse(plan.contains("SCAN workoutSet"), plan)
        assertFalse(plan.contains("TEMP B-TREE"), plan)
    }

    private fun strings(sql: String, column: Int): List<String> = driver.executeQuery(
        identifier = null,
        sql = sql,
        mapper = { cursor ->
            val values = mutableListOf<String>()
            while (cursor.next().value) values += requireNotNull(cursor.getString(column))
            QueryResult.Value(values)
        },
        parameters = 0
    ).value
}
