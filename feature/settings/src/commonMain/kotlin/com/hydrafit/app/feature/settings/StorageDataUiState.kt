package com.hydrafit.app.feature.settings

import com.hydrafit.app.core.domain.backup.AutomaticBackupFailureKind
import com.hydrafit.app.core.domain.backup.BackupFailure
import com.hydrafit.app.core.domain.storage.DataCategory
import com.hydrafit.app.core.domain.storage.StorageUsage

/** The outcome of the last removal, shown as one line under the storage section. */
sealed interface StorageDataStatus {
    /** A scoped database category was cleared. */
    data class CategoryRemoved(val category: DataCategory) : StorageDataStatus

    /** The recorded automatic snapshots were deleted; [count] were removed. */
    data class BackupFilesRemoved(val count: Int) : StorageDataStatus

    /** The imported on-device model file was removed. */
    data object ModelRemoved : StorageDataStatus

    /** The model manager refused the removal, so the model file is still there. */
    data object ModelRemovalFailed : StorageDataStatus

    /** A removal failed; [failure] is the typed reason when the throw was a BackupException. */
    data class Failed(val failure: BackupFailure?) : StorageDataStatus
}

/** The outcome of a "Back up now" request made from a removal dialog. */
sealed interface StorageBackupStatus {
    /** A new snapshot was written and verified. */
    data object Succeeded : StorageBackupStatus

    /** Automatic backups are off, so no snapshot was written. */
    data object Disabled : StorageBackupStatus

    /** No snapshot was produced; the kind is the automation's own classification. */
    data class Failed(val kind: AutomaticBackupFailureKind) : StorageBackupStatus
}

/**
 * What the storage section shows: the last loaded usage, whether the on-device model file is
 * present, the category awaiting confirmation and the outcome of the last action. [pending] is the
 * confirmation request itself, so a tap never removes anything on its own.
 */
data class StorageDataUiState(
    val usage: StorageUsage? = null,
    val modelInstalled: Boolean = false,
    val pending: DataCategory? = null,
    /** The text typed into the ALL_DATA confirmation field. */
    val typedConfirmation: String = "",
    val inProgress: Boolean = false,
    val status: StorageDataStatus? = null,
    /** The last "Back up now" outcome, shown inside the open removal dialog. */
    val backupStatus: StorageBackupStatus? = null
)

/** Stable identities for the storage controls; technical metadata, never spoken labels. */
internal object StorageDataTestTags {
    const val MODEL_ROW = "settings_storage_model_row"
    const val CONFIRM_ACTION = "settings_storage_confirm_action"
    const val TYPED_CONFIRMATION = "settings_storage_typed_confirmation"

    /** One tag per category row, derived from the category so the id stays stable. */
    fun categoryRow(category: DataCategory): String =
        "settings_storage_category_${category.name.lowercase()}"
}
