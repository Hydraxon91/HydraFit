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
        // Schema 53 adds `durationSeconds`, so the generated set queries can no longer run against
        // the 46 schema; write and read the stored row with raw SQL that matches it.
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO workoutSet(exerciseId, reps, weightKg, performedAt, isWarmup, loadKind) " +
                "VALUES ('squat', 10, 60.0, 100, 0, 'EXTERNAL')"
        )
        val before = storedSets()
        assertEquals(0L, indexCount())

        HydraFitDatabase.Schema.migrate(driver, 46, 47)

        assertEquals(before, storedSets())
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

    private fun assertIndexedReplayAndHistory() {
        val historyProjection = "SELECT workoutSet.id, workoutSet.exerciseId, " +
            "COALESCE(exerciseOverride.name, exercise.name) AS exerciseName, " +
            "workoutSet.reps, workoutSet.weightKg, workoutSet.loadKind, workoutSet.isWarmup, " +
            "workoutSet.rir, workoutSet.performedAt, workoutSet.timingProvenance, " +
            "workoutSet.occurrenceId " +
            "FROM workoutSet LEFT JOIN exercise ON exercise.id = workoutSet.exerciseId " +
            "LEFT JOIN exerciseOverride ON exerciseOverride.exerciseId = workoutSet.exerciseId"
        val replayProjection = "SELECT id, exerciseId, reps, weightKg, loadKind, performedAt, " +
            "isWarmup, occurrenceId FROM workoutSet"
        // Every page shape must bind its cursor as an indexed range, not an optional OR predicate
        // that would rescan the rows earlier pages already read.
        listOf(
            "$historyProjection WHERE workoutSet.exerciseId = 'squat' " +
                "ORDER BY workoutSet.performedAt DESC, workoutSet.id DESC LIMIT 31" to
                "workoutSet_exerciseId_performedAt_id (exerciseId=?)",
            "$historyProjection WHERE workoutSet.exerciseId = 'squat' " +
                "AND workoutSet.performedAt = 100 AND workoutSet.id < 2 " +
                "ORDER BY workoutSet.performedAt DESC, workoutSet.id DESC LIMIT 31" to
                "(exerciseId=? AND performedAt=? AND id<?)",
            "$historyProjection WHERE workoutSet.exerciseId = 'squat' " +
                "AND workoutSet.performedAt < 100 " +
                "ORDER BY workoutSet.performedAt DESC, workoutSet.id DESC LIMIT 31" to
                "(exerciseId=? AND performedAt<?)",
            "$replayProjection WHERE exerciseId = 'squat' AND performedAt <= 100000 " +
                "ORDER BY performedAt ASC, id ASC LIMIT 256" to
                "(exerciseId=? AND performedAt<?)",
            "$replayProjection WHERE exerciseId = 'squat' AND performedAt <= 100000 " +
                "AND performedAt = 10 AND id > 2 " +
                "ORDER BY performedAt ASC, id ASC LIMIT 256" to
                "(exerciseId=? AND performedAt=? AND id>?)",
            "$replayProjection WHERE exerciseId = 'squat' AND performedAt <= 100000 " +
                "AND performedAt > 10 " +
                "ORDER BY performedAt ASC, id ASC LIMIT 256" to
                "(exerciseId=? AND performedAt>? AND performedAt<?)"
        ).forEach { (query, constraint) ->
            val plan = strings("EXPLAIN QUERY PLAN $query", 3).joinToString("\n")
            assertTrue(plan.contains("workoutSet_exerciseId_performedAt_id"), plan)
            assertTrue(plan.contains(constraint), plan)
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
