package com.hydrafit.app.core.domain.storage

/**
 * Removes one scoped category's data in a single transaction. [DataCategory.BACKUP_FILES] is not
 * database data and is rejected here; the feature performs that removal through its own port.
 */
class ClearDataCategoryUseCase(private val repository: DataManagementRepository) {
    suspend operator fun invoke(category: DataCategory) {
        require(category != DataCategory.BACKUP_FILES) {
            "$category is removed outside the database"
        }
        repository.clear(category)
    }
}
