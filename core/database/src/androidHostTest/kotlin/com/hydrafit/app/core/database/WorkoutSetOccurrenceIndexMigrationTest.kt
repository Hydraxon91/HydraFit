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
        database.workoutLogQueries.insertSet(
            "squat", 10, 60.0, 100, 0, null, null, null, null, null, null, "EXTERNAL"
        )
        database.workoutLogQueries.assignOccurrence(
            7L,
            null,
            database.workoutLogQueries.lastInsertedSetId().executeAsOne()
        )
        val before = database.workoutLogQueries.selectAllSets().executeAsList()
        assertEquals(0L, indexCount())

        HydraFitDatabase.Schema.migrate(driver, 47, 48)

        assertEquals(before, database.workoutLogQueries.selectAllSets().executeAsList())
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
