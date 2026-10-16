package com.hydrafit.app.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.backup.BackupJson
import com.hydrafit.app.core.domain.backup.BackupValidator
import com.hydrafit.app.core.domain.transfer.WorkoutImportPartition
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

/**
 * The bucket/partition import ledger is user-owned data, so it round-trips through a validated
 * backup. Unlike per-session provenance it can carry two sessions with identical content, which is
 * exactly the split case it exists for.
 */
class WorkoutImportBucketBackupTest {
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
    fun aSplitBucketRoundTripsIncludingALinkWhoseSessionWasDeleted() = runTest {
        val partition =
            WorkoutImportPartition(2, listOf(listOf(0), listOf(1)), emptySet()).canonical()
        database.workoutImportBucketQueries.insertBucket(
            "FITNOTES",
            "key-a",
            "content-a",
            partition
        )
        database.workoutImportBucketQueries.insertLink("FITNOTES", "key-a", 0L, "session-a")
        database.workoutImportBucketQueries.insertLink("FITNOTES", "key-a", 1L, "deleted")
        val expected = backups.export("test", 1L)
        assertEquals(1, expected.importBuckets.size)
        assertEquals(
            listOf("session-a", "deleted"),
            expected.importBuckets.single().importedSessionIds
        )

        val restored = BackupValidator(SqlDelightBackupCatalog())
            .prepareForRestore(BackupJson.decode(BackupJson.encode(expected)))
        database.workoutImportBucketQueries.deleteBucketLinks()
        database.workoutImportBucketQueries.deleteBuckets()

        backups.restore(restored)

        assertEquals(expected.importBuckets, backups.export("test", 1L).importBuckets)
    }

    @Test
    fun restoreReplacesTheBucketLedgerRatherThanMergingIt() = runTest {
        database.workoutImportBucketQueries.insertBucket("FITNOTES", "old", "old", "old")
        database.workoutImportBucketQueries.insertLink("FITNOTES", "old", 0L, "old-session")
        val empty = backups.export("test", 1L).copy(importBuckets = emptyList())

        backups.restore(empty)

        assertEquals(emptyList(), backups.export("test", 1L).importBuckets)
        assertEquals(
            0,
            database.workoutImportBucketQueries.selectAllBuckets().executeAsList().size
        )
    }
}
