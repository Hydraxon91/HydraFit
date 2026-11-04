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
import com.hydrafit.app.core.domain.backup.BackupMergeKeys
import com.hydrafit.app.core.domain.backup.BackupMergeSelection
import com.hydrafit.app.core.domain.backup.BackupRepository
import com.hydrafit.app.core.domain.backup.BackupRestPreferenceRecord
import com.hydrafit.app.core.domain.backup.BackupStagingRepository
import com.hydrafit.app.core.domain.backup.BackupValidator
import com.hydrafit.app.core.domain.backup.PendingBackup
import com.hydrafit.app.core.domain.backup.PreviewBackupUseCase
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.workout.unicodeCaseFold
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
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
            searchProjectionBackfill = SearchProjectionBackfill(database),
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

    @Test
    fun backfillsFoldedSearchProjectionsForRowsWrittenBeforeTheColumnsExisted() =
        runTest(dispatcher) {
            val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
            HydraFitDatabase.Schema.create(driver)
            val database = HydraFitDatabase(driver)

            // Rows written before migration 42 left both projections at their empty default.
            database.exerciseQueries.insertCustom(
                id = "user-atlas",
                name = "Atlas Épaules",
                requiredEquipment = "BARBELL",
                movementPattern = "SQUAT",
                isUnilateral = 0L,
                loadCapability = "EXTERNAL",
                involvements = null
            )
            database.workoutSessionQueries.insertSession(
                id = "s1",
                startedAtMillis = 100L,
                endedAtMillis = null,
                localEpochDay = 10L
            )
            database.sessionNoteQueries.upsert("s1", "FOCUS ΣΟΦΙΑ")

            val maintenance = DatabaseStartupMaintenance(
                applyStagedBackup = noStagedBackup(),
                seedExerciseCatalog = SeedExerciseCatalog(database),
                seedEquipmentCatalog = SeedEquipmentCatalog(database),
                customExerciseDedupe = CustomExerciseDedupe(database),
                workoutSessionBackfill = WorkoutSessionBackfill(database, TimeProvider { 0L }),
                searchProjectionBackfill = SearchProjectionBackfill(database),
                scope = CoroutineScope(dispatcher)
            )
            maintenance.start()
            advanceUntilIdle()

            assertEquals(
                unicodeCaseFold("Atlas Épaules"),
                database.exerciseQueries.selectById("user-atlas").executeAsOne().searchName
            )
            assertEquals(
                unicodeCaseFold("FOCUS ΣΟΦΙΑ"),
                database.sessionNoteQueries.selectAll().executeAsList().single().searchNote
            )

            driver.close()
        }

    /**
     * A projection that drifted from the name it is written by must be healed at startup, including
     * when a user override decides the effective display name.
     */
    @Test
    fun backfillsFoldedSearchProjectionsFromTheEffectiveDisplayName() = runTest(dispatcher) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        val database = HydraFitDatabase(driver)
        database.exerciseQueries.insertCustom(
            id = "user-atlas",
            name = "Atlas Épaules",
            requiredEquipment = "BARBELL",
            movementPattern = "SQUAT",
            isUnilateral = 0L,
            loadCapability = "EXTERNAL",
            involvements = null
        )
        // A rename written without the projection, plus a stale projection on another row.
        database.exerciseOverrideQueries.upsert(
            exerciseId = "user-atlas",
            name = "Atlas Renommé",
            requiredEquipment = null,
            movementPattern = null,
            isUnilateral = null,
            loadCapability = null,
            involvements = null
        )
        database.exerciseQueries.updateSearchName(searchName = "stale", id = "user-atlas")

        val maintenance = DatabaseStartupMaintenance(
            applyStagedBackup = noStagedBackup(),
            seedExerciseCatalog = SeedExerciseCatalog(database),
            seedEquipmentCatalog = SeedEquipmentCatalog(database),
            customExerciseDedupe = CustomExerciseDedupe(database),
            workoutSessionBackfill = WorkoutSessionBackfill(database, TimeProvider { 0L }),
            searchProjectionBackfill = SearchProjectionBackfill(database),
            scope = CoroutineScope(dispatcher)
        )
        maintenance.start()
        advanceUntilIdle()

        assertEquals(
            unicodeCaseFold("Atlas Renommé"),
            database.exerciseQueries.selectById("user-atlas").executeAsOne().searchName
        )

        driver.close()
    }

    /**
     * A storage failure must not crash the process or leave the shell waiting on a gate that will
     * never open: the failure is reported, the first screen stays closed and nothing is seeded from a
     * half-maintained database.
     */
    @Test
    fun reportsFailureInsteadOfCrashingWhenTheStagedBackupCannotBeRead() = runTest(dispatcher) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        val database = HydraFitDatabase(driver)
        val maintenance = maintenance(
            database = database,
            staging = FailingStagingRepository { IllegalStateException("unable to open database") }
        )

        maintenance.start()
        advanceUntilIdle()

        assertTrue(maintenance.hasFailed.value)
        assertFalse(maintenance.isReady.value)
        assertTrue(database.exerciseQueries.selectAll().executeAsList().isEmpty())

        driver.close()
    }

    @Test
    fun retryRunsMaintenanceAgainAfterATransientStorageFailure() = runTest(dispatcher) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        val database = HydraFitDatabase(driver)
        val staging = FailingStagingRepository(failOnce = true) {
            IllegalStateException("database is locked")
        }
        val maintenance = maintenance(database = database, staging = staging)

        maintenance.start()
        advanceUntilIdle()
        assertTrue(maintenance.hasFailed.value)

        maintenance.retry()
        advanceUntilIdle()

        assertFalse(maintenance.hasFailed.value)
        assertTrue(maintenance.isReady.value)
        assertEquals(2, staging.readAttempts)
        assertTrue(database.exerciseQueries.selectAll().executeAsList().isNotEmpty())

        driver.close()
    }

    /** A second `start()` must not race a second attempt against the first. */
    @Test
    fun startTwiceLaunchesOneAttempt() = runTest(dispatcher) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        val database = HydraFitDatabase(driver)
        val staging = CountingStagingRepository()
        val maintenance = maintenance(database = database, staging = staging)

        maintenance.start()
        maintenance.start()
        advanceUntilIdle()

        assertEquals(1, staging.reads)
        assertTrue(maintenance.isReady.value)

        driver.close()
    }

    @Test
    fun retryBeforeAnyFailureDoesNotStartMaintenance() = runTest(dispatcher) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        val database = HydraFitDatabase(driver)
        val maintenance = maintenance(database)

        maintenance.retry()
        advanceUntilIdle()

        assertFalse(maintenance.isReady.value)
        assertFalse(maintenance.hasFailed.value)
        assertTrue(database.exerciseQueries.selectAll().executeAsList().isEmpty())

        driver.close()
    }

    @Test
    fun repeatedFailuresStayRetryableAndNeverReportReady() = runTest(dispatcher) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        val database = HydraFitDatabase(driver)
        val staging = FailingStagingRepository { IllegalStateException("disk I/O error") }
        val maintenance = maintenance(database = database, staging = staging)

        maintenance.start()
        advanceUntilIdle()
        maintenance.retry()
        advanceUntilIdle()
        maintenance.retry()
        advanceUntilIdle()

        assertEquals(3, staging.readAttempts)
        assertTrue(maintenance.hasFailed.value)
        assertFalse(maintenance.isReady.value)

        driver.close()
    }

    /** A shutdown must not be relabelled as a storage failure the user is asked to retry. */
    @Test
    fun cancellationIsNotReportedAsAStorageFailure() = runTest(dispatcher) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        val database = HydraFitDatabase(driver)
        val maintenance = maintenance(
            database = database,
            staging = FailingStagingRepository { CancellationException("scope ended") }
        )

        maintenance.start()
        advanceUntilIdle()

        assertFalse(maintenance.hasFailed.value)
        assertFalse(maintenance.isReady.value)

        driver.close()
    }

    private fun maintenance(
        database: HydraFitDatabase,
        staging: BackupStagingRepository = EmptyStagingRepository
    ): DatabaseStartupMaintenance = DatabaseStartupMaintenance(
        applyStagedBackup = stagedBackup(staging),
        seedExerciseCatalog = SeedExerciseCatalog(database),
        seedEquipmentCatalog = SeedEquipmentCatalog(database),
        customExerciseDedupe = CustomExerciseDedupe(database),
        workoutSessionBackfill = WorkoutSessionBackfill(database, TimeProvider { 0L }),
        searchProjectionBackfill = SearchProjectionBackfill(database),
        scope = CoroutineScope(dispatcher)
    )

    /** An [ApplyStagedBackupUseCase] whose staging store is empty, so `start()` applies nothing. */
    private fun noStagedBackup(): ApplyStagedBackupUseCase = stagedBackup(EmptyStagingRepository)

    private fun stagedBackup(staging: BackupStagingRepository): ApplyStagedBackupUseCase =
        ApplyStagedBackupUseCase(
            preview = PreviewBackupUseCase(
                BackupValidator(
                    object : BackupCatalog {
                        override fun seedExerciseIds(): Set<String> = emptySet()
                        override fun builtInEquipmentIds(): Set<String> = emptySet()
                    }
                )
            ),
            repository = object : BackupRepository {
                override suspend fun export(
                    appVersion: String,
                    exportedAtMillis: Long
                ): BackupFile = emptyBackupFile(appVersion, exportedAtMillis)

                override suspend fun restore(file: BackupFile) = Unit

                override suspend fun mergeKeys(): BackupMergeKeys =
                    throw UnsupportedOperationException()

                override suspend fun merge(selection: BackupMergeSelection) =
                    throw UnsupportedOperationException()
            },
            staging = staging,
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

/** A staging store with nothing pending. */
private object EmptyStagingRepository : BackupStagingRepository {
    override suspend fun stage(payload: String, appVersion: String, stagedAtMillis: Long) = Unit

    override suspend fun staged(): PendingBackup? = null

    override suspend fun clearStaged() = Unit

    override suspend fun recordApplyError(
        failure: BackupFailure,
        message: String?,
        occurredAtMillis: Long
    ) = Unit

    override suspend fun applyError(): BackupApplyError? = null

    override suspend fun clearApplyError() = Unit
}

/** Counts how many maintenance attempts reached the staged-backup read. */
private class CountingStagingRepository : BackupStagingRepository by EmptyStagingRepository {
    var reads = 0
        private set

    override suspend fun staged(): PendingBackup? {
        reads += 1
        return null
    }
}

/**
 * A staging store whose first read fails like an unreadable database. With [failOnce] the following
 * reads behave like an empty store, modelling a transient failure that a retry can clear.
 */
private class FailingStagingRepository(
    private val failOnce: Boolean = false,
    private val failure: () -> Exception
) : BackupStagingRepository {
    var readAttempts = 0
        private set

    override suspend fun stage(payload: String, appVersion: String, stagedAtMillis: Long) = Unit

    override suspend fun staged(): PendingBackup? {
        readAttempts += 1
        if (!failOnce || readAttempts == 1) throw failure()
        return null
    }

    override suspend fun clearStaged() = Unit

    override suspend fun recordApplyError(
        failure: BackupFailure,
        message: String?,
        occurredAtMillis: Long
    ) = Unit

    override suspend fun applyError(): BackupApplyError? = null

    override suspend fun clearApplyError() = Unit
}
