package com.hydrafit.app.core.database

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Migration 50 -> 51 adds the workout-import provenance ledger. It is an additive table: existing
 * history must survive untouched, and a fresh schema must create the same table.
 */
class WorkoutImportProvenanceMigrationTest {
    private lateinit var driver: SqlDriver

    @AfterTest
    fun tearDown() {
        if (::driver.isInitialized) driver.close()
    }

    @Test
    fun migrationAddsTheLedgerWithoutChangingExistingHistory() {
        driver = HistoricalDatabaseFixtures.v1()
        HydraFitDatabase.Schema.migrate(driver, 1, 50)
        val database = HydraFitDatabase(driver)
        database.exerciseQueries.insert("squat", "Squat", "BARBELL", "SQUAT", "EXTERNAL")
        // Schema 53 adds `durationSeconds`, so the generated set queries can no longer run against
        // the 50 schema; write and read the stored row with raw SQL that matches it.
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO workoutSet(exerciseId, reps, weightKg, performedAt, isWarmup, loadKind) " +
                "VALUES ('squat', 10, 60.0, 100, 0, 'EXTERNAL')"
        )
        val before = storedSets()
        assertEquals(0L, ledgerTableCount())

        HydraFitDatabase.Schema.migrate(driver, 50, 51)

        assertEquals(1L, ledgerTableCount())
        assertEquals(before, storedSets())
        database.workoutImportQueries.insertProvenance("FITNOTES", "key", "content", "session-a")
        val row = database.workoutImportQueries.selectProvenanceBySource("FITNOTES")
            .executeAsOne()
        assertEquals("session-a", row.importedSessionId)
        assertEquals(0L, HistoricalDatabaseFixtures.count(driver, "workoutSession"))
    }

    @Test
    fun deletingAnImportedSessionLeavesItsProvenanceRow() {
        driver = HistoricalDatabaseFixtures.empty()
        HydraFitDatabase.Schema.create(driver)
        val database = HydraFitDatabase(driver)
        database.workoutImportQueries.insertProvenance("HEVY", "key", "content", "session-b")

        database.workoutSessionQueries.deleteAllSessions()

        assertEquals(
            listOf("session-b"),
            database.workoutImportQueries.selectAllProvenance().executeAsList()
                .map { it.importedSessionId }
        )
    }

    private fun ledgerTableCount(): Long = HistoricalDatabaseFixtures.long(
        driver,
        "SELECT count(*) FROM sqlite_master WHERE type = 'table' " +
            "AND name = 'workoutImportProvenance'"
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
