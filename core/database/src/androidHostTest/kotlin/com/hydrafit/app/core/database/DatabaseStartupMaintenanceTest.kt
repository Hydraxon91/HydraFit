package com.hydrafit.app.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.backup.ApplyStagedBackupUseCase
import com.hydrafit.app.core.domain.backup.BACKUP_FORMAT
import com.hydrafit.app.core.domain.backup.BACKUP_FORMAT_VERSION
import com.hydrafit.app.core.domain.backup.BackupApplyError
import com.hydrafit.app.core.domain.backup.BackupCatalog
import com.hydrafit.app.core.domain.backup.BackupCatalogManifest
import com.hydrafit.app.core.domain.backup.BackupFailure
import com.hydrafit.app.core.domain.backup.BackupFile
import com.hydrafit.app.core.domain.backup.BackupRepository
import com.hydrafit.app.core.domain.backup.BackupRestPreferenceRecord
import com.hydrafit.app.core.domain.backup.BackupStagingRepository
import com.hydrafit.app.core.domain.backup.BackupValidator
import com.hydrafit.app.core.domain.backup.PendingBackup
import com.hydrafit.app.core.domain.backup.PreviewBackupUseCase
import com.hydrafit.app.core.domain.time.TimeProvider
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class DatabaseStartupMaintenanceTest {

    private val dispatcher = StandardTestDispatcher()

    @Test
    fun marksReadyOnlyAfterMaintenanceRunsAndSeedsTheCatalog() = runTest(dispatcher) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        val database = HydraFitDatabase(driver)

        val maintenance = DatabaseStartupMaintenance(
            applyStagedBackup = noStagedBackup(),
            seedExerciseCatalog = SeedExerciseCatalog(database),
            seedEquipmentCatalog = SeedEquipmentCatalog(database),
            customExerciseDedupe = CustomExerciseDedupe(database),
            workoutSessionBackfill = WorkoutSessionBackfill(database, TimeProvider { 0L }),
            scope = CoroutineScope(dispatcher)
        )

        assertFalse(maintenance.isReady.value)

        maintenance.start()
        advanceUntilIdle()

        assertTrue(maintenance.isReady.value)
        assertTrue(database.exerciseQueries.selectAll().executeAsList().isNotEmpty())
        assertTrue(database.equipmentQueries.selectAll().executeAsList().isNotEmpty())

        driver.close()
    }

    /** An [ApplyStagedBackupUseCase] whose staging store is empty, so `start()` applies nothing. */
    private fun noStagedBackup(): ApplyStagedBackupUseCase = ApplyStagedBackupUseCase(
        preview = PreviewBackupUseCase(
            BackupValidator(
                object : BackupCatalog {
                    override fun seedExerciseIds(): Set<String> = emptySet()
                    override fun builtInEquipmentIds(): Set<String> = emptySet()
                }
            )
        ),
        repository = object : BackupRepository {
            override suspend fun export(appVersion: String, exportedAtMillis: Long): BackupFile =
                emptyBackupFile(appVersion, exportedAtMillis)

            override suspend fun restore(file: BackupFile) = Unit
        },
        staging = object : BackupStagingRepository {
            override suspend fun stage(payload: String, appVersion: String, stagedAtMillis: Long) =
                Unit

            override suspend fun staged(): PendingBackup? = null

            override suspend fun clearStaged() = Unit

            override suspend fun recordApplyError(
                failure: BackupFailure,
                message: String?,
                occurredAtMillis: Long
            ) = Unit

            override suspend fun applyError(): BackupApplyError? = null

            override suspend fun clearApplyError() = Unit
        },
        timeProvider = TimeProvider { 0L }
    )
}

private fun emptyBackupFile(appVersion: String, exportedAtMillis: Long): BackupFile = BackupFile(
    format = BACKUP_FORMAT,
    formatVersion = BACKUP_FORMAT_VERSION,
    appVersion = appVersion,
    exportedAtMillis = exportedAtMillis,
    catalog = BackupCatalogManifest(emptyList(), seedProfiles = emptyList()),
    settings = null,
    customExercises = emptyList(),
    exerciseOverrides = emptyList(),
    equipment = emptyList(),
    selectedEquipment = emptyList(),
    workoutSets = emptyList(),
    workoutSessions = emptyList(),
    plans = emptyList(),
    planDays = emptyList(),
    planEntries = emptyList(),
    volumeExplanations = emptyList(),
    volumeExplanationStates = emptyList(),
    routines = emptyList(),
    routineWorkouts = emptyList(),
    routineEntries = emptyList(),
    activations = emptyList(),
    activationWorkouts = emptyList(),
    activationEntries = emptyList(),
    occurrences = emptyList(),
    occurrenceEntries = emptyList(),
    scheduleState = null,
    personalRecords = emptyList(),
    preferences = emptyList(),
    exclusions = emptyList(),
    restPreferences = listOf(BackupRestPreferenceRecord(null, 120L))
)
