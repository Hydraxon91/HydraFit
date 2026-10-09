package com.hydrafit.app.core.domain.backup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class ExportBackupUseCaseTest {

    @Test
    fun encodesTheRepositorySnapshotToJson() = runTest {
        val file = emptyBackupFile().copy(appVersion = "9.9.9")
        val useCase = ExportBackupUseCase(FakeBackupRepository(file))

        val json = useCase(appVersion = "9.9.9", exportedAtMillis = 42L)

        assertTrue(json.contains("\"format\":\"$BACKUP_FORMAT\""))
        assertTrue(json.contains("\"formatVersion\":$BACKUP_FORMAT_VERSION"))
        assertEquals(file, BackupJson.decode(json))
    }

    @Test
    fun rejectsAPayloadAboveTheSizeLimit() = runTest {
        val huge = emptyBackupFile().copy(
            customExercises = listOf(
                BackupExerciseRecord(
                    id = "user-x",
                    name = "X",
                    requiredEquipment = "",
                    movementPattern = "CORE",
                    isUnilateral = false,
                    loadCapability = "EXTERNAL",
                    involvements = "A".repeat((BackupJson.MAX_BYTES + 1).toInt())
                )
            )
        )
        val useCase = ExportBackupUseCase(FakeBackupRepository(huge))

        val failure = assertFailsWith<BackupException> {
            useCase(appVersion = "9.9.9", exportedAtMillis = 1L)
        }
        assertEquals(BackupFailure.TOO_LARGE, failure.failure)
    }
}

private class FakeBackupRepository(private val file: BackupFile) : BackupRepository {
    override suspend fun export(appVersion: String, exportedAtMillis: Long): BackupFile = file

    override suspend fun restore(file: BackupFile) = throw UnsupportedOperationException()
}

internal fun emptyBackupFile(): BackupFile = BackupFile(
    appVersion = "test",
    exportedAtMillis = 0L,
    catalog = BackupCatalogManifest(emptyList())
)
