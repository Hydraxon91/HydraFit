package com.hydrafit.app.core.domain.backup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class RestoreBackupUseCaseTest {

    private val catalog = object : BackupCatalog {
        override fun seedExerciseIds(): Set<String> = emptySet()

        override fun builtInEquipmentIds(): Set<String> = emptySet()
    }

    private val preview = PreviewBackupUseCase(BackupValidator(catalog))

    @Test
    fun stagesTheValidatedPayload() = runTest {
        val staging = RecordingBackupStagingRepository()
        val useCase = RestoreBackupUseCase(preview, staging)
        val text = BackupJson.encode(emptyBackupFile())

        useCase(text, appVersion = "t", stagedAtMillis = 1L)

        assertEquals(1, staging.staged.size)
        assertEquals("t", staging.staged.single().appVersion)
    }

    @Test
    fun rejectsBeforeStagingWhenThePayloadIsInvalid() = runTest {
        val staging = RecordingBackupStagingRepository()
        val useCase = RestoreBackupUseCase(preview, staging)

        assertFailsWith<BackupException> { useCase("not json", "t", 1L) }

        assertEquals(0, staging.staged.size)
    }

    @Test
    fun consumesAndClearsAPendingApplyError() = runTest {
        val staging = RecordingBackupStagingRepository()
        staging.applyError = BackupApplyError(BackupFailure.STARTUP_UNSTABLE, null, 1L)
        val useCase = RestoreBackupUseCase(preview, staging)

        assertEquals(BackupFailure.STARTUP_UNSTABLE, useCase.consumePendingApplyError())
        assertNull(useCase.consumePendingApplyError())
    }
}

private class RecordingBackupStagingRepository : BackupStagingRepository {
    val staged = mutableListOf<PendingBackup>()
    var applyError: BackupApplyError? = null

    override suspend fun stage(payload: String, appVersion: String, stagedAtMillis: Long) {
        staged += PendingBackup(payload, appVersion, stagedAtMillis)
    }

    override suspend fun staged(): PendingBackup? = staged.lastOrNull()

    override suspend fun clearStaged() {
        staged.clear()
    }

    override suspend fun recordApplyError(
        failure: BackupFailure,
        message: String?,
        occurredAtMillis: Long
    ) {
        applyError = BackupApplyError(failure, message, occurredAtMillis)
    }

    override suspend fun applyError(): BackupApplyError? = applyError

    override suspend fun clearApplyError() {
        applyError = null
    }
}
