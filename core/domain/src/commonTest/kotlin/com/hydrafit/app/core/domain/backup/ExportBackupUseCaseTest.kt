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
    fun rejectsAFileThatOmitsARequiredField() = runTest {
        val useCase = ExportBackupUseCase(FakeBackupRepository(emptyBackupFile()))
        val complete = useCase(appVersion = "9.9.9", exportedAtMillis = 1L)
        val withoutWorkoutSets = complete.replace(Regex("\"workoutSets\":\\[[^]]*\\],?"), "")

        val failure = assertFailsWith<BackupException> { BackupJson.decode(withoutWorkoutSets) }
        assertEquals(BackupFailure.MALFORMED, failure.failure)
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
    format = BACKUP_FORMAT,
    formatVersion = BACKUP_FORMAT_VERSION,
    appVersion = "test",
    exportedAtMillis = 0L,
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
    exclusions = emptyList()
)
