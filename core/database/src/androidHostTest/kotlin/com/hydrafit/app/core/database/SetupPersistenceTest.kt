package com.hydrafit.app.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.backup.BackupJson
import com.hydrafit.app.core.domain.backup.BackupSetupRecord
import com.hydrafit.app.core.domain.backup.BackupValidator
import com.hydrafit.app.core.domain.setup.SetupDisposition
import com.hydrafit.app.core.domain.setup.SetupStep
import com.hydrafit.app.core.domain.time.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class SetupPersistenceTest {
    @Test
    fun freshSchemaOffersSetupAndMigrationTreatsEvenEmptyDatabasesAsReturning() = runTest {
        val fresh = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        val upgraded = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            HydraFitDatabase.Schema.create(fresh)
            assertEquals(
                SetupDisposition.PENDING,
                SqlDelightSetupRepository(HydraFitDatabase(fresh)).current().disposition
            )
            upgraded.execute(null, "CREATE TABLE retained (value TEXT)", 0)
            upgraded.execute(null, "INSERT INTO retained VALUES ('untouched')", 0)
            HydraFitDatabase.Schema.migrate(upgraded, 44, 45)
            assertEquals(
                "RETURNING",
                HistoricalDatabaseFixtures.text(upgraded, "SELECT disposition FROM setupState")
            )
            assertEquals(
                "untouched",
                HistoricalDatabaseFixtures.text(upgraded, "SELECT value FROM retained")
            )
        } finally {
            fresh.close()
            upgraded.close()
        }
    }

    @Test
    fun preferencesSetFrequencyButDecidingLaterPreservesItAndCreatesNoTrainingData() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            HydraFitDatabase.Schema.create(driver)
            val database = HydraFitDatabase(driver)
            val preferences = SqlDelightPreferredWeekdaysRepository(database)
            val days = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
            assertNull(preferences.current())
            preferences.set(days)
            assertEquals(days, preferences.current())
            assertEquals(3, SqlDelightEnginePreferenceRepository(database).selectedDaysPerWeek())
            preferences.set(null)
            assertNull(preferences.current())
            assertEquals(3, SqlDelightEnginePreferenceRepository(database).selectedDaysPerWeek())
            assertFailsWith<IllegalArgumentException> { preferences.set(emptySet()) }
            listOf("workoutSet", "workoutSession", "planHistory", "trainingActivation").forEach {
                assertEquals(0L, HistoricalDatabaseFixtures.count(driver, it))
            }
        } finally {
            driver.close()
        }
    }

    @Test
    fun backupRoundTripsResumeAndPreferencesAndLegacyRestoreSuppressesSetup() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            HydraFitDatabase.Schema.create(driver)
            val database = HydraFitDatabase(driver)
            SeedExerciseCatalog(database).seed()
            SeedEquipmentCatalog(database).seed()
            val catalog = SqlDelightBackupCatalog()
            val backup = SqlDelightBackupRepository(database, catalog)
            database.setupQueries.updateState(1L, "PENDING", "PERSONAL_RECORDS", 4L)
            SqlDelightPreferredWeekdaysRepository(database).set(
                setOf(DayOfWeek.TUESDAY, DayOfWeek.SATURDAY)
            )
            val file = BackupJson.decode(BackupJson.encode(backup.export("dev", 0L)))
            BackupValidator(catalog).validate(file)
            backup.restore(file)
            val state = SqlDelightSetupRepository(database).current()
            assertEquals(SetupStep.PERSONAL_RECORDS, state.step)
            assertEquals(SetupDisposition.PENDING, state.disposition)
            assertEquals(0L, state.revision)
            assertEquals(file, backup.export("dev", 0L))
            val legacy = file.copy(
                formatVersion = 10,
                equipmentProfiles = emptyList(),
                activeEquipmentProfileId = null,
                setup = BackupSetupRecord(),
                preferredWeekdays = null,
                workoutReminders = null
            )
            BackupValidator(catalog).validate(legacy)
            backup.restore(legacy)
            assertEquals(
                SetupDisposition.RETURNING,
                SqlDelightSetupRepository(database).current().disposition
            )
            assertNull(SqlDelightPreferredWeekdaysRepository(database).current())
        } finally {
            driver.close()
        }
    }

    @Test
    fun failedWholeRestorePreservesSetupAndWeekdaysAlongsideExistingSettings() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            HydraFitDatabase.Schema.create(driver)
            val database = HydraFitDatabase(driver)
            SeedExerciseCatalog(database).seed()
            SeedEquipmentCatalog(database).seed()
            val backup = SqlDelightBackupRepository(database, SqlDelightBackupCatalog())
            val original = backup.export("dev", 0L)
            driver.execute(
                null,
                "CREATE TRIGGER fail_restore BEFORE UPDATE ON plannerEngine " +
                    "BEGIN SELECT RAISE(ABORT, 'injected'); END",
                0
            )
            assertFailsWith<Exception> {
                backup.restore(
                    original.copy(
                        setup = BackupSetupRecord(disposition = "SKIPPED", step = "GOAL"),
                        preferredWeekdays = listOf("MONDAY", "FRIDAY")
                    )
                )
            }
            assertEquals(original, backup.export("dev", 0L))
        } finally {
            driver.close()
        }
    }

    @Test
    fun preferenceTransactionRollsBackWhenFrequencyWriteFails() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            HydraFitDatabase.Schema.create(driver)
            val database = HydraFitDatabase(driver)
            database.plannerEngineQueries.insertIgnoreRow("DETERMINISTIC")
            driver.execute(
                null,
                "CREATE TRIGGER fail_frequency BEFORE UPDATE ON plannerEngine " +
                    "BEGIN SELECT RAISE(ABORT, 'injected'); END",
                0
            )
            val preferences = SqlDelightPreferredWeekdaysRepository(database)
            assertFailsWith<Exception> {
                preferences.set(setOf(DayOfWeek.MONDAY, DayOfWeek.FRIDAY))
            }
            assertNull(preferences.current())
            assertEquals(4, SqlDelightEnginePreferenceRepository(database).selectedDaysPerWeek())
        } finally {
            driver.close()
        }
    }
}
