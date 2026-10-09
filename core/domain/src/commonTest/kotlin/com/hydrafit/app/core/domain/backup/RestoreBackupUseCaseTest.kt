package com.hydrafit.app.core.domain.backup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

class RestoreBackupUseCaseTest {

    private val catalog = object : BackupCatalog {
        override fun seedExerciseIds(): Set<String> = emptySet()
        override fun builtInEquipmentIds(): Set<String> = emptySet()
    }

    @Test
    fun restoresTheValidatedPayload() = runTest {
        val repository = RecordingBackupRepository()
        val useCase =
            RestoreBackupUseCase(PreviewBackupUseCase(BackupValidator(catalog)), repository)
        val text = BackupJson.encode(
            BackupFile(
                appVersion = "t",
                exportedAtMillis = 1L,
                catalog = BackupCatalogManifest(emptyList())
            )
        )

        useCase(text)

        assertEquals(1, repository.restored.size)
    }

    @Test
    fun rejectsBeforeWritingWhenThePayloadIsInvalid() = runTest {
        val repository = RecordingBackupRepository()
        val useCase =
            RestoreBackupUseCase(PreviewBackupUseCase(BackupValidator(catalog)), repository)

        assertFailsWith<BackupException> { useCase("not json") }
        assertEquals(0, repository.restored.size)
    }
}

private class RecordingBackupRepository : BackupRepository {
    val restored = mutableListOf<BackupFile>()

    override suspend fun export(appVersion: String, exportedAtMillis: Long): BackupFile =
        throw UnsupportedOperationException()

    override suspend fun restore(file: BackupFile) {
        restored += file
    }
}
