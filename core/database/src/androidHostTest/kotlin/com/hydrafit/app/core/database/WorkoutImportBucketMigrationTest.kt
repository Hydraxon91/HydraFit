package com.hydrafit.app.core.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Migration 51 -> 52 adds the workout-import bucket/partition ledger. It is additive: existing
 * history and the legacy provenance ledger survive, and a fresh schema creates the same tables.
 */
class WorkoutImportBucketMigrationTest {
    private lateinit var driver: SqlDriver

    @AfterTest
    fun tearDown() {
        if (::driver.isInitialized) driver.close()
    }

    @Test
    fun migrationAddsTheBucketLedgerWithoutChangingExistingHistory() {
        driver = HistoricalDatabaseFixtures.v1()
        HydraFitDatabase.Schema.migrate(driver, 1, 51)
        val database = HydraFitDatabase(driver)
        database.exerciseQueries.insert("squat", "Squat", "BARBELL", "SQUAT", "EXTERNAL")
        // Schema 53 adds `durationSeconds`, so the generated set queries can no longer run against
        // the 51 schema; write and read the stored row with raw SQL that matches it.
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO workoutSet(exerciseId, reps, weightKg, performedAt, isWarmup, loadKind) " +
                "VALUES ('squat', 10, 60.0, 100, 0, 'EXTERNAL')"
        )
        database.workoutImportQueries.insertProvenance("FITNOTES", "key", "content", "session-a")
        val before = storedSets()
        assertEquals(0L, bucketTableCount())

        HydraFitDatabase.Schema.migrate(driver, 51, 52)

        assertEquals(2L, bucketTableCount())
        assertEquals(before, storedSets())
        assertEquals(
            listOf("session-a"),
            database.workoutImportQueries.selectAllProvenance().executeAsList()
                .map { it.importedSessionId }
        )
        database.workoutImportBucketQueries.insertBucket("FITNOTES", "key", "content", "partition")
        database.workoutImportBucketQueries.insertLink("FITNOTES", "key", 0L, "session-a")
        val link = database.workoutImportBucketQueries.selectLinks("FITNOTES", "key").executeAsOne()
        assertEquals("session-a", link.importedSessionId)
        assertEquals(0L, HistoricalDatabaseFixtures.count(driver, "workoutSession"))
    }

    private fun bucketTableCount(): Long = HistoricalDatabaseFixtures.long(
        driver,
        "SELECT count(*) FROM sqlite_master WHERE type = 'table' " +
            "AND name IN ('workoutImportBucket', 'workoutImportBucketSession')"
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
}
