package com.hydrafit.app.core.domain.storage

/**
 * Removes one scoped category's data in a single transaction. [DataCategory.BACKUP_FILES] is not
 * database data and is rejected here; the feature performs that removal through its own port.
 * [DataCategory.EXERCISE_DATA] throws [ExerciseDataClearException] and removes nothing while a
 * custom exercise is still referenced by history, records or authored planning.
 */
class ClearDataCategoryUseCase(private val repository: DataManagementRepository) {
    suspend operator fun invoke(category: DataCategory) {
        require(category != DataCategory.BACKUP_FILES) {
            "$category is removed outside the database"
        }
        repository.clear(category)
    }
}
