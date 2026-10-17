package com.hydrafit.app.core.domain.storage

/**
 * Removes one scoped category's data in a single transaction. [DataCategory.ON_DEVICE_MODEL] and
 * [DataCategory.BACKUP_FILES] are not database data and are rejected here; the feature performs
 * those removals through their own ports.
 */
class ClearDataCategoryUseCase(private val repository: DataManagementRepository) {
    suspend operator fun invoke(category: DataCategory) {
        require(category != DataCategory.ON_DEVICE_MODEL && category != DataCategory.BACKUP_FILES) {
            "$category is removed outside the database"
        }
        repository.clear(category)
    }
}
