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
    CUSTOM_EXERCISES,
    CARDIO_ACTIVITIES
}

/** Why a [DataCategory.EXERCISE_DATA] removal was refused. The display copy belongs to the UI. */
enum class ExerciseDataClearFailure {
    /** At least one custom exercise is still named by history, records or authored planning. */
    CUSTOM_EXERCISES_REFERENCED
}

/**
 * Raised when removing exercise data would orphan a reference to a custom exercise. Nothing is
 * removed when this is thrown; the user must delete the referring data first.
 */
class ExerciseDataClearException(val failure: ExerciseDataClearFailure) :
    Exception("Exercise data removal refused: $failure")

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
