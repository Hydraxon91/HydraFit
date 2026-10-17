package com.hydrafit.app.feature.settings

import com.hydrafit.app.core.domain.backup.BackupFailure
import com.hydrafit.app.core.domain.backup.BackupMergeExcludedCount
import com.hydrafit.app.core.domain.backup.BackupMergeSelection

/** Counts shown before a restore; the raw text is kept so the confirmed restore reuses it. */
data class BackupPreviewUi(
    val appVersion: String,
    val workoutSets: Int,
    val customExercises: Int,
    val totalRecords: Int,
    val pendingText: String
)

/**
 * The exact add-only outcome of merging the previewed file, kept beside the replace preview so the
 * dialog can offer both actions without recomputing. Nothing here replaces or deletes local data.
 */
data class BackupMergePreviewUi(
    val sessions: Int,
    val sets: Int,
    val insertCount: Int,
    val keptLocalSessions: Int,
    val skippedOpenSessions: Int,
    val skippedUnattachedSets: Int,
    val droppedOccurrenceLinks: Int,
    val excluded: List<BackupMergeExcludedCount>,
    val isEmpty: Boolean
)

internal fun BackupMergeSelection.toPreviewUi(): BackupMergePreviewUi = BackupMergePreviewUi(
    sessions = sessionsToInsert.size,
    sets = setsToInsert.size,
    insertCount = insertCount,
    keptLocalSessions = keptLocalSessions,
    skippedOpenSessions = skippedOpenSessions,
    skippedUnattachedSets = skippedUnattachedSets,
    droppedOccurrenceLinks = setsWithDroppedOccurrenceLinks,
    excluded = excluded,
    isEmpty = isEmpty
)

sealed interface BackupStatus {
    data object Exported : BackupStatus

    /**
     * The recorded history was written as an analysis CSV. Deliberately distinct from [Exported] so
     * the user is not told a restorable backup was created.
     */
    data object HistoryCsvExported : BackupStatus

    /** A validated backup is staged and will replace the data on the next process start. */
    data object RestoreStaged : BackupStatus

    /** A validated backup is staged and will add its records on the next process start. */
    data object MergeStaged : BackupStatus

    data class Failed(val failure: BackupFailure) : BackupStatus
}

data class BackupUiState(
    val isSupported: Boolean = true,
    val inProgress: Boolean = false,
    val preview: BackupPreviewUi? = null,
    val mergePreview: BackupMergePreviewUi? = null,
    val status: BackupStatus? = null,
    /** True while an imported file is waiting for the password that opens it. */
    val unlocking: Boolean = false
)

/** Stable identities for the data-transfer controls; technical metadata, never spoken labels. */
internal object BackupTestTags {
    const val EXPORT_CSV = "settings_backup_export_history_csv"
    const val MERGE_ACTION = "settings_backup_merge_action"
    const val MERGE_SUMMARY = "settings_backup_merge_summary"
}
