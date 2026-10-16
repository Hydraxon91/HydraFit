package com.hydrafit.app.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.backup.BackupJson
import com.hydrafit.app.core.domain.backup.BackupValidator
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

/**
 * Import provenance is user-owned data, so it round-trips through a validated backup. A record whose
 * imported session is gone still travels, which is what keeps a deleted import from reappearing after
 * a restore plus a repeated export.
 */
class WorkoutImportProvenanceBackupTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: HydraFitDatabase
    private lateinit var backups: SqlDelightBackupRepository

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        database = HydraFitDatabase(driver)
        database.plannerEngineQueries.insertIgnoreRow("DETERMINISTIC")
        SeedExerciseCatalog(database).seed()
        SeedEquipmentCatalog(database).seed()
        backups = SqlDelightBackupRepository(database, SqlDelightBackupCatalog())
    }

    @AfterTest
    fun tearDown() = driver.close()

    @Test
    fun provenanceRoundTripsIncludingARecordWhoseSessionWasDeleted() = runTest {
        database.workoutImportQueries.insertProvenance(
            "FITNOTES",
            "key-a",
            "content-a",
            "session-a"
        )
        database.workoutImportQueries.insertProvenance("HEVY", "key-b", "content-b", "deleted")
        val expected = backups.export("test", 1L)
        assertEquals(2, expected.importProvenance.size)

        val restored = BackupValidator(SqlDelightBackupCatalog())
            .prepareForRestore(BackupJson.decode(BackupJson.encode(expected)))
        database.workoutImportQueries.deleteAllProvenance()

        backups.restore(restored)

        assertEquals(expected.importProvenance, backups.export("test", 1L).importProvenance)
    }

    @Test
    fun restoreReplacesTheLedgerRatherThanMergingIt() = runTest {
        database.workoutImportQueries.insertProvenance("FITNOTES", "old", "old", "old-session")
        val empty = backups.export("test", 1L).copy(importProvenance = emptyList())

        backups.restore(empty)

        assertEquals(emptyList(), backups.export("test", 1L).importProvenance)
    }
}
