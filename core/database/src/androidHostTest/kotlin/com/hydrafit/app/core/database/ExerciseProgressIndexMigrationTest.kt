package com.hydrafit.app.core.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExerciseProgressIndexMigrationTest {
    private lateinit var driver: SqlDriver

    @AfterTest
    fun tearDown() {
        if (::driver.isInitialized) driver.close()
    }

    @Test
    fun migrationPreservesRowsAndAddsExerciseTimeIdentityIndex() {
        driver = HistoricalDatabaseFixtures.v1()
        HydraFitDatabase.Schema.migrate(driver, 1, 46)
        val database = HydraFitDatabase(driver)
        database.exerciseQueries.insert("squat", "Squat", "BARBELL", "SQUAT", "EXTERNAL")
        database.workoutLogQueries.insertSet(
            "squat", 10, 60.0, 100, 0, null, null, null, null, null, null, "EXTERNAL"
        )
        val before = database.workoutLogQueries.selectAllSets().executeAsList()
        assertEquals(0L, indexCount())

        HydraFitDatabase.Schema.migrate(driver, 46, 47)

        assertEquals(before, database.workoutLogQueries.selectAllSets().executeAsList())
        assertEquals(1L, indexCount())
        assertEquals(
            listOf("exerciseId", "performedAt", "id"),
            strings("PRAGMA index_info(workoutSet_exerciseId_performedAt_id)", 2)
        )
        assertIndexedReplayAndHistory()
    }

    @Test
    fun freshSchemaAlsoAvoidsLogScansAndTemporarySorts() {
        driver = HistoricalDatabaseFixtures.empty()
        HydraFitDatabase.Schema.create(driver)
        assertEquals(1L, indexCount())
        assertIndexedReplayAndHistory()
    }

    private fun indexCount() = HistoricalDatabaseFixtures.long(
        driver,
        "SELECT count(*) FROM sqlite_master " +
            "WHERE type = 'index' AND name = 'workoutSet_exerciseId_performedAt_id'"
    )

    private fun assertIndexedReplayAndHistory() {
        val replay = """
            SELECT id, exerciseId, reps, weightKg, loadKind, performedAt, isWarmup, occurrenceId
            FROM workoutSet
            WHERE exerciseId = 'squat' AND performedAt <= 100000
              AND (1 = 0 OR performedAt > 10 OR (performedAt = 10 AND id > 2))
            ORDER BY performedAt ASC, id ASC LIMIT 256
        """.trimIndent()
        val history = """
            SELECT workoutSet.id, workoutSet.exerciseId,
                   COALESCE(exerciseOverride.name, exercise.name) AS exerciseName,
                   workoutSet.reps, workoutSet.weightKg, workoutSet.loadKind, workoutSet.isWarmup,
                   workoutSet.rir, workoutSet.performedAt, workoutSet.timingProvenance,
                   workoutSet.occurrenceId
            FROM workoutSet
            LEFT JOIN exercise ON exercise.id = workoutSet.exerciseId
            LEFT JOIN exerciseOverride ON exerciseOverride.exerciseId = workoutSet.exerciseId
            WHERE workoutSet.exerciseId = 'squat'
              AND (1 = 0 OR workoutSet.performedAt < 100
                   OR (workoutSet.performedAt = 100 AND workoutSet.id < 2))
            ORDER BY workoutSet.performedAt DESC, workoutSet.id DESC LIMIT 31
        """.trimIndent()
        listOf(replay, history).forEach { query ->
            val plan = strings("EXPLAIN QUERY PLAN $query", 3).joinToString("\n")
            assertTrue(plan.contains("workoutSet_exerciseId_performedAt_id"), plan)
            assertFalse(plan.contains("SCAN workoutSet"), plan)
            assertFalse(plan.contains("TEMP B-TREE"), plan)
        }
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
