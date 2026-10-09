package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.backup.BackupApplyError
import com.hydrafit.app.core.domain.backup.BackupFailure
import com.hydrafit.app.core.domain.backup.BackupStagingRepository
import com.hydrafit.app.core.domain.backup.PendingBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Persists the staged backup text and the last apply failure in single-row tables. All access runs on
 * [Dispatchers.IO]; the startup gate applies the staged payload before the first screen is shown.
 */
class SqlDelightBackupStagingRepository(private val database: HydraFitDatabase) :
    BackupStagingRepository {

    override suspend fun stage(payload: String, appVersion: String, stagedAtMillis: Long) {
        withContext(Dispatchers.IO) {
            database.backupStagingQueries.stageBackup(payload, appVersion, stagedAtMillis)
        }
    }

    override suspend fun staged(): PendingBackup? = withContext(Dispatchers.IO) {
        database.backupStagingQueries.selectStagedBackup().executeAsOneOrNull()?.let { row ->
            PendingBackup(
                payload = row.payload,
                appVersion = row.appVersion,
                stagedAtMillis = row.stagedAtMillis
            )
        }
    }

    override suspend fun clearStaged() {
        withContext(Dispatchers.IO) {
            database.backupStagingQueries.clearStagedBackup()
        }
    }

    override suspend fun recordApplyError(
        failure: BackupFailure,
        message: String?,
        occurredAtMillis: Long
    ) {
        withContext(Dispatchers.IO) {
            database.backupStagingQueries.recordApplyError(failure.name, message, occurredAtMillis)
        }
    }

    override suspend fun applyError(): BackupApplyError? = withContext(Dispatchers.IO) {
        database.backupStagingQueries.selectApplyError().executeAsOneOrNull()?.let { row ->
            BackupApplyError(
                failure = BackupFailure.entries.firstOrNull { it.name == row.failure }
                    ?: BackupFailure.IO,
                message = row.message,
                occurredAtMillis = row.occurredAtMillis
            )
        }
    }

    override suspend fun clearApplyError() {
        withContext(Dispatchers.IO) {
            database.backupStagingQueries.clearApplyError()
        }
    }
}
