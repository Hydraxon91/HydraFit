package com.hydrafit.app.core.domain.storage

/**
 * Assembles what Settings reports for storage usage: database bytes, automatic-snapshot size and
 * count, and the per-category record counts, without mutating anything.
 */
class StorageUsageUseCase(
    private val repository: DataManagementRepository,
    private val usage: StorageUsagePort
) {
    suspend operator fun invoke(): StorageUsage {
        val counts = repository.counts()
        return StorageUsage(
            databaseBytes = usage.databaseBytes(),
            automaticBackupBytes = usage.automaticBackupBytes(),
            automaticBackupFileCount = usage.automaticBackupFileCount(),
            categories = COUNTED_CATEGORIES.map { category ->
                DataCategoryUsage(
                    category = category,
                    counts = KINDS[category].orEmpty().mapNotNull { kind ->
                        counts[kind]?.let { count -> DataCount(kind, count) }
                    }
                )
            }
        )
    }

    companion object {
        /** Categories carried by database counts; backup files and the model are reported separately. */
        val COUNTED_CATEGORIES: List<DataCategory> = listOf(
            DataCategory.WORKOUT_HISTORY,
            DataCategory.PLANNING,
            DataCategory.BODY_AND_RECORDS,
            DataCategory.TRAINING_SETUP,
            DataCategory.EXERCISE_DATA
        )

        /** Which counts each category reports, in display order. */
        val KINDS: Map<DataCategory, List<DataCountKind>> = mapOf(
            DataCategory.WORKOUT_HISTORY to listOf(
                DataCountKind.SESSIONS,
                DataCountKind.SETS,
                DataCountKind.SESSION_NOTES,
                DataCountKind.CARDIO_ACTIVITIES
            ),
            DataCategory.PLANNING to listOf(
                DataCountKind.ACCEPTED_PLANS,
                DataCountKind.ROUTINES,
                DataCountKind.ACTIVATIONS,
                DataCountKind.SCHEDULED_WORKOUTS
            ),
            DataCategory.BODY_AND_RECORDS to listOf(
                DataCountKind.BODY_WEIGHT_MEASUREMENTS,
                DataCountKind.PERSONAL_RECORDS
            ),
            DataCategory.TRAINING_SETUP to listOf(DataCountKind.EQUIPMENT_PROFILES),
            DataCategory.EXERCISE_DATA to listOf(DataCountKind.CUSTOM_EXERCISES)
        )
    }
}
