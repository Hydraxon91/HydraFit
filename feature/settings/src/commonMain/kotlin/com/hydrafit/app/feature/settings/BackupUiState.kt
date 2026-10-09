package com.hydrafit.app.feature.settings

import com.hydrafit.app.core.domain.backup.BackupFailure

/** Counts shown before a restore; the raw text is kept so the confirmed restore reuses it. */
data class BackupPreviewUi(
    val appVersion: String,
    val workoutSets: Int,
    val customExercises: Int,
    val totalRecords: Int,
    val pendingText: String
)

sealed interface BackupStatus {
    data object Exported : BackupStatus

    /** A validated backup is staged and will replace the data on the next process start. */
    data object RestoreStaged : BackupStatus

    data class Failed(val failure: BackupFailure) : BackupStatus
}

data class BackupUiState(
    val isSupported: Boolean = true,
    val inProgress: Boolean = false,
    val preview: BackupPreviewUi? = null,
    val status: BackupStatus? = null
)
