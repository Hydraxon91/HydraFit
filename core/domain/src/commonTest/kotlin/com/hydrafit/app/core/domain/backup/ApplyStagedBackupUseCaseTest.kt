package com.hydrafit.app.core.domain.backup

import com.hydrafit.app.core.domain.time.TimeProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class ApplyStagedBackupUseCaseTest {

    private val catalog = object : BackupCatalog {
        override fun seedExerciseIds(): Set<String> = emptySet()

        override fun builtInEquipmentIds(): Set<String> = emptySet()
    }

    private val preview = PreviewBackupUseCase(BackupValidator(catalog))

    @Test
    fun replaceDocumentRestores() = runTest {
        val staging = ApplyStaging()
        val repository = RecordingRepository()
        staging.pending = PendingBackup(
            payload = StagedBackupDocumentJson.encode(BackupApplyMode.REPLACE, backupText()),
            appVersion = "t",
            stagedAtMillis = 1L
        )

        ApplyStagedBackupUseCase(preview, repository, staging, TimeProvider { 5L })()

        assertEquals(1, repository.restoreCalls)
        assertEquals(0, repository.mergeCalls)
        assertNull(staging.pending)
    }

    @Test
    fun mergeDocumentAppliesTheSelection() = runTest {
        val staging = ApplyStaging()
        val repository = RecordingRepository()
        staging.pending = PendingBackup(
            payload = StagedBackupDocumentJson.encode(BackupApplyMode.MERGE, backupText()),
            appVersion = "t",
            stagedAtMillis = 1L
        )

        ApplyStagedBackupUseCase(preview, repository, staging, TimeProvider { 5L })()

        assertEquals(0, repository.restoreCalls)
        assertEquals(1, repository.mergeCalls)
        assertEquals(1, repository.mergeKeysCalls)
        assertNull(staging.pending)
    }

    @Test
    fun legacyRawPayloadRestores() = runTest {
        val staging = ApplyStaging()
        val repository = RecordingRepository()
        staging.pending = PendingBackup(
            payload = backupText(),
            appVersion = "t",
            stagedAtMillis = 1L
        )

        ApplyStagedBackupUseCase(preview, repository, staging, TimeProvider { 5L })()

        assertEquals(1, repository.restoreCalls)
        assertEquals(0, repository.mergeCalls)
        assertNull(staging.pending)
    }

    @Test
    fun invalidStagedPayloadRecordsOneTimeError() = runTest {
        val staging = ApplyStaging()
        val repository = RecordingRepository()
        staging.pending = PendingBackup(
            payload = "not json",
            appVersion = "t",
            stagedAtMillis = 1L
        )

        ApplyStagedBackupUseCase(preview, repository, staging, TimeProvider { 5L })()

        assertEquals(BackupFailure.MALFORMED, staging.recorded?.failure)
        assertEquals(5L, staging.recorded?.occurredAtMillis)
        assertNull(staging.pending)
        assertEquals(0, repository.restoreCalls)
        assertEquals(0, repository.mergeCalls)
    }

    private fun backupText(): String = BackupJson.encode(emptyBackupFile())
}

private class ApplyStaging : BackupStagingRepository {
    var pending: PendingBackup? = null
    var recorded: BackupApplyError? = null

    override suspend fun stage(payload: String, appVersion: String, stagedAtMillis: Long) {
        pending = PendingBackup(payload, appVersion, stagedAtMillis)
    }

    override suspend fun staged(): PendingBackup? = pending

    override suspend fun clearStaged() {
        pending = null
    }

    override suspend fun recordApplyError(
        failure: BackupFailure,
        message: String?,
        occurredAtMillis: Long
    ) {
        recorded = BackupApplyError(failure, message, occurredAtMillis)
    }

    override suspend fun applyError(): BackupApplyError? = recorded

    override suspend fun clearApplyError() {
        recorded = null
    }
}

private class RecordingRepository : BackupRepository {
    var restoreCalls = 0
    var mergeCalls = 0
    var mergeKeysCalls = 0

    override suspend fun export(appVersion: String, exportedAtMillis: Long): BackupFile =
        throw UnsupportedOperationException()

    override suspend fun restore(file: BackupFile) {
        restoreCalls++
    }

    override suspend fun mergeKeys(): BackupMergeKeys {
        mergeKeysCalls++
        return BackupMergeKeys(
            sessionIds = emptySet(),
            bodyWeightDays = emptySet(),
            personalRecordExerciseIds = emptySet(),
            customExerciseIds = emptySet(),
            exerciseOverrideIds = emptySet(),
            exerciseNoteIds = emptySet(),
            exercisePreferenceIds = emptySet(),
            exerciseExclusionIds = emptySet()
        )
    }

    override suspend fun merge(selection: BackupMergeSelection) {
        mergeCalls++
    }
}
