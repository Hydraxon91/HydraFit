package com.hydrafit.app.core.domain.storage

/** A scoped group of user-owned data that Settings can report and remove on its own. */
enum class DataCategory {
    WORKOUT_HISTORY,
    PLANNING,
    BODY_AND_RECORDS,
    TRAINING_SETUP,
    EXERCISE_DATA,
    BACKUP_FILES,
    ALL_DATA
}

/** One countable record kind inside a category; display labels live in the UI. */
enum class DataCountKind {
    SESSIONS,
    SETS,
    SESSION_NOTES,
    ACCEPTED_PLANS,
    ROUTINES,
    ACTIVATIONS,
    SCHEDULED_WORKOUTS,
    BODY_WEIGHT_MEASUREMENTS,
    PERSONAL_RECORDS,
    EQUIPMENT_PROFILES,
    CUSTOM_EXERCISES
}

/** One record kind's current count. */
data class DataCount(val kind: DataCountKind, val count: Int)

/** A category's counts, in display order. */
data class DataCategoryUsage(val category: DataCategory, val counts: List<DataCount>)

/**
 * What Settings reports before a removal: the database file size, the automatic-snapshot folder
 * usage (null when the provider cannot report it) and the per-category record counts. It never
 * mutates anything.
 */
data class StorageUsage(
    val databaseBytes: Long,
    val automaticBackupBytes: Long?,
    val automaticBackupFileCount: Int?,
    val categories: List<DataCategoryUsage>
)
