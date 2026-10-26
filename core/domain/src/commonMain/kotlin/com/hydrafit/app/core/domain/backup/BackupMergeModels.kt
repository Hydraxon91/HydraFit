package com.hydrafit.app.core.domain.backup

/**
 * Which apply path a staged payload takes at the next process start. [REPLACE] is the existing
 * whole-file restore; [MERGE] adds a [BackupMergeSelection]'s records to the live install instead of
 * replacing it.
 */
enum class BackupApplyMode {
    REPLACE,
    MERGE
}

/**
 * The identities already present in the live install, read before merge selection runs. Each set
 * drives insert-if-absent filtering for its collection; [sessionIds] additionally gates the
 * session-granular skip that makes a repeated merge of the same file idempotent.
 *
 * All fields are required on purpose: an omitted key set would silently treat local rows as new.
 */
data class BackupMergeKeys(
    val sessionIds: Set<String>,
    val bodyWeightDays: Set<Long>,
    val personalRecordExerciseIds: Set<String>,
    /**
     * Every local exercise id, seeded and custom. A file custom row colliding with any local row
     * keeps the local row instead of aborting the merge on a primary-key conflict.
     */
    val customExerciseIds: Set<String>,
    val exerciseOverrideIds: Set<String>,
    val exerciseNoteIds: Set<String>,
    val exercisePreferenceIds: Set<String>,
    val exerciseExclusionIds: Set<String>
)

/**
 * One workout set a merge inserts. The persisted row id is deliberately absent: merged rows get
 * fresh local ids, and the occurrence links are dropped because a v1 merge does not carry
 * occurrences. A merge insertion therefore never depends on foreign row identities it does not own.
 */
data class BackupMergedSet(
    val exerciseId: String,
    val reps: Int?,
    val weightKg: Double?,
    val performedAtMillis: Long,
    val isWarmup: Boolean,
    val involvements: String?,
    val weekNumber: Int?,
    val cycleNumber: Int?,
    val dayIndex: Int?,
    val rir: Int?,
    val sessionId: String,
    val loadKind: String,
    val timingProvenance: String,
    val startedAtElapsedMillis: Long?,
    val completedAtElapsedMillis: Long?,
    val durationSeconds: Int?,
    /** The recorded target, carried verbatim so a merge never reinterprets it. */
    val prescriptionSnapshot: String?,
    /**
     * Frozen station label and basis, or null. Equipment profiles are outside merge, so the local
     * station id is deliberately not carried; the label remains displayable with id zero.
     */
    val stationName: String? = null,
    val stationLoadBasis: String? = null,
    val distanceMeters: Int? = null,
    /** Measured force in canonical newtons, carried verbatim; null for every other metric. */
    val forceNewtons: Double? = null,
    /** Descriptive device identity and stated rating, carried verbatim; never converted to force. */
    val deviceName: String? = null,
    val deviceRatingKg: Double? = null,
    /**
     * The file's own set id, used only to attach this parent's inserted cluster segments to the new
     * local row. It is not restored as an identity.
     */
    val sourceSetId: Long? = null,
    /** Cluster identity, carried verbatim so a merge never recomputes the performed segments. */
    val clusterKind: String? = null,
    val clusterPlannedSegments: Int? = null,
    val clusterPerformedSegments: Int? = null
)

/** Collections a v1 merge deliberately leaves with the live install; counts are disclosed in the preview. */
enum class BackupMergeExcludedCategory {
    ACCEPTED_PLANS,
    ROUTINES,
    ACTIVATIONS,
    OCCURRENCES,
    EQUIPMENT_PROFILES,
    SETTINGS,
    IMPORT_LEDGER
}

/** How many rows of an excluded collection the file carries; zero-count categories are not listed. */
data class BackupMergeExcludedCount(val category: BackupMergeExcludedCategory, val count: Int)

/**
 * The add-only record selection a merge inserts — not a workout plan. Nothing here replaces or
 * deletes existing data: rows whose identity already exists locally are skipped and counted in
 * [keptLocalSessions]/[keptLocalRows], in-progress sessions (`endedAtMillis == null`, possible in a
 * best-effort snapshot) are skipped and counted in [skippedOpenSessions], and [excluded] lists the
 * collections the merge does not carry. The database layer applies this selection in one
 * transaction.
 */
data class BackupMergeSelection(
    val sessionsToInsert: List<BackupWorkoutSessionRecord>,
    val setsToInsert: List<BackupMergedSet>,
    /**
     * Segment rows belonging to the inserted cluster parents only. Their [BackupClusterSegmentRecord.setId]
     * is the file's set id and is remapped to the fresh local id when the selection is applied.
     */
    val clusterSegmentsToInsert: List<BackupClusterSegmentRecord> = emptyList(),
    val sessionNotesToInsert: List<BackupSessionNoteRecord>,
    val bodyWeightToInsert: List<BackupBodyWeightRecord>,
    val personalRecordsToInsert: List<BackupPersonalRecordRecord>,
    val preferencesToInsert: List<BackupPreferenceRecord>,
    val exclusionsToInsert: List<BackupExclusionRecord>,
    val exerciseNotesToInsert: List<BackupExerciseNoteRecord>,
    val overridesToInsert: List<BackupExerciseOverrideRecord>,
    val customExercisesToInsert: List<BackupExerciseRecord>,
    val keptLocalSessions: Int,
    val keptLocalRows: Int,
    val skippedOpenSessions: Int,
    val skippedUnattachedSets: Int,
    val setsWithDroppedOccurrenceLinks: Int,
    val excluded: List<BackupMergeExcludedCount>
) {
    /** Rows this selection inserts in total, across every collection. */
    val insertCount: Int
        get() = sessionsToInsert.size + setsToInsert.size + clusterSegmentsToInsert.size +
            sessionNotesToInsert.size +
            bodyWeightToInsert.size + personalRecordsToInsert.size + preferencesToInsert.size +
            exclusionsToInsert.size + exerciseNotesToInsert.size + overridesToInsert.size +
            customExercisesToInsert.size

    /** Whether the merge would add nothing because every supported record already exists locally. */
    val isEmpty: Boolean get() = insertCount == 0
}
