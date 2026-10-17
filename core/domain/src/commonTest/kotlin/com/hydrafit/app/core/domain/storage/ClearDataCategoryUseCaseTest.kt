package com.hydrafit.app.core.domain.storage

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

class ClearDataCategoryUseCaseTest {

    @Test
    fun clearsTheRequestedDatabaseCategory() = runTest {
        val repository = ClearRepository()

        ClearDataCategoryUseCase(repository)(DataCategory.WORKOUT_HISTORY)

        assertEquals(listOf(DataCategory.WORKOUT_HISTORY), repository.cleared)
    }

    @Test
    fun clearsAllDataThroughTheRepository() = runTest {
        val repository = ClearRepository()

        ClearDataCategoryUseCase(repository)(DataCategory.ALL_DATA)

        assertEquals(listOf(DataCategory.ALL_DATA), repository.cleared)
    }

    @Test
    fun rejectsCategoriesRemovedOutsideTheDatabase() = runTest {
        val repository = ClearRepository()

        assertFailsWith<IllegalArgumentException> {
            ClearDataCategoryUseCase(repository)(DataCategory.ON_DEVICE_MODEL)
        }
        assertFailsWith<IllegalArgumentException> {
            ClearDataCategoryUseCase(repository)(DataCategory.BACKUP_FILES)
        }
        assertEquals(emptyList(), repository.cleared)
    }
}

private class ClearRepository : DataManagementRepository {
    val cleared = mutableListOf<DataCategory>()

    override suspend fun counts(): Map<DataCountKind, Int> = emptyMap()

    override suspend fun clear(category: DataCategory) {
        cleared += category
    }
}
