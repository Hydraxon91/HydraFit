package com.hydrafit.app.core.database

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
        database.workoutLogQueries.insertSet(
            "squat", 10, 60.0, 100, 0, null, null, null, null, null, null, "EXTERNAL"
        )
        val before = database.workoutLogQueries.selectAllSets().executeAsList()
        assertEquals(0L, ledgerTableCount())

        HydraFitDatabase.Schema.migrate(driver, 50, 51)

        assertEquals(1L, ledgerTableCount())
        assertEquals(before, database.workoutLogQueries.selectAllSets().executeAsList())
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
}
