package com.hydrafit.app.core.domain.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class StorageUsageUseCaseTest {

    @Test
    fun reportsSizesAndCountsPerCategory() = runTest {
        val repository = UsageRepository(
            counts = mapOf(
                DataCountKind.SESSIONS to 8,
                DataCountKind.SETS to 25,
                DataCountKind.SESSION_NOTES to 1,
                DataCountKind.ACCEPTED_PLANS to 3,
                DataCountKind.CUSTOM_EXERCISES to 2
            )
        )
        val useCase = StorageUsageUseCase(
            repository,
            UsagePort(databaseBytes = 4096L, backupBytes = 512L, backupFiles = 2)
        )

        val usage = useCase()

        assertEquals(4096L, usage.databaseBytes)
        assertEquals(512L, usage.automaticBackupBytes)
        assertEquals(2, usage.automaticBackupFileCount)
        assertEquals(
            listOf(
                DataCount(DataCountKind.SESSIONS, 8),
                DataCount(DataCountKind.SETS, 25),
                DataCount(DataCountKind.SESSION_NOTES, 1)
            ),
            usage.categories.single { it.category == DataCategory.WORKOUT_HISTORY }.counts
        )
        assertEquals(
            listOf(DataCount(DataCountKind.CUSTOM_EXERCISES, 2)),
            usage.categories.single { it.category == DataCategory.EXERCISE_DATA }.counts
        )
        assertEquals(1, repository.countsCalls)
        // Categories without records still appear, so the screen can explain them.
        assertEquals(5, usage.categories.size)
        assertEquals(
            emptyList(),
            usage.categories.single { it.category == DataCategory.BODY_AND_RECORDS }.counts
        )
    }

    @Test
    fun passesThroughUnknownFolderUsage() = runTest {
        val useCase = StorageUsageUseCase(
            UsageRepository(emptyMap()),
            UsagePort(databaseBytes = 0L, backupBytes = null, backupFiles = null)
        )

        val usage = useCase()

        assertEquals(0L, usage.databaseBytes)
        assertNull(usage.automaticBackupBytes)
        assertNull(usage.automaticBackupFileCount)
    }
}

private class UsagePort(
    private val databaseBytes: Long,
    private val backupBytes: Long?,
    private val backupFiles: Int?
) : StorageUsagePort {
    override suspend fun databaseBytes(): Long = databaseBytes

    override suspend fun automaticBackupBytes(): Long? = backupBytes

    override suspend fun automaticBackupFileCount(): Int? = backupFiles
}

private class UsageRepository(private val counts: Map<DataCountKind, Int>) :
    DataManagementRepository {
    var countsCalls = 0

    override suspend fun counts(): Map<DataCountKind, Int> {
        countsCalls++
        return counts
    }

    override suspend fun clear(category: DataCategory) = Unit
}
