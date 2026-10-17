package com.hydrafit.app.core.domain.backup

import kotlinx.serialization.Serializable

/** Stable identifier of the device-local automatic-backup state document. */
const val AUTOMATIC_BACKUP_STATE_FORMAT: String = "hydrafit-automatic-backup-state"

/** The state-document version this build writes. A device-local format, never part of a backup. */
const val AUTOMATIC_BACKUP_STATE_FORMAT_VERSION: Int = 1

/**
 * Device-local automatic-backup configuration and ledger. It describes this install's folder grant,
 * snapshot history and last outcome, so it is deliberately never carried in a backup file or
 * restored onto another device: a folder grant and a snapshot ledger do not transfer.
 */
@Serializable
data class AutomaticBackupState(
    val format: String = AUTOMATIC_BACKUP_STATE_FORMAT,
    val formatVersion: Int = AUTOMATIC_BACKUP_STATE_FORMAT_VERSION,
    val enabled: Boolean = false,
    /** The chosen folder's document handle; null until the user selects one. */
    val folderHandle: String? = null,
    /** When the last snapshot's write verified, or null when none succeeded yet. */
    val lastSuccessMillis: Long? = null,
    /** The most recent failure, kept for display until the next verified success. */
    val lastFailure: AutomaticBackupFailureRecord? = null,
    /** Snapshots this app created in the chosen folder, in creation order. */
    val documents: List<RecordedBackupDocument> = emptyList()
)

/**
 * One snapshot this app created in the folder. Only recorded identities may be pruned, so a file
 * this app did not record — a manual export or a foreign file — is never deleted automatically.
 */
@Serializable
data class RecordedBackupDocument(
    val documentId: String,
    val displayName: String,
    val createdAtMillis: Long,
    /** False until the write read back intact; an unverified document is never pruned. */
    val verified: Boolean
)

/** The last automation failure and when it occurred. */
@Serializable
data class AutomaticBackupFailureRecord(
    val kind: AutomaticBackupFailureKind,
    val occurredAtMillis: Long
)

/**
 * Why an automatic run did not produce a verified snapshot, mapped to a localized message upstream.
 * [retryable] marks a failure the scheduler should retry with backoff instead of waiting for the next
 * daily opportunity; it is not a promise that the retry succeeds.
 */
enum class AutomaticBackupFailureKind(val retryable: Boolean) {
    /** No folder was selected yet. */
    FOLDER_NOT_CONFIGURED(false),

    /** The chosen folder's permission was revoked or the folder no longer exists. */
    FOLDER_UNAVAILABLE(false),

    /** The provider cannot support the read/create/delete operations retention requires. */
    FOLDER_UNSUPPORTED(false),

    /** The snapshot could not be produced from stored data. */
    EXPORT_FAILED(false),

    /** More data than one snapshot file can carry. */
    TOO_LARGE(false),

    /** Writing the snapshot to the folder failed; nothing usable was recorded. */
    WRITE_FAILED(true),

    /** The written snapshot could not be read back intact; it stays unverified and unpruned. */
    WRITE_UNVERIFIED(false),

    /** An unexpected failure the classification does not cover. */
    UNKNOWN(true)
}

/** The result of one automatic-backup attempt. */
sealed interface AutomaticBackupOutcome {
    /** Automatic backups are turned off. */
    data object Disabled : AutomaticBackupOutcome

    /** The newest verified snapshot is younger than the policy interval; nothing was written. */
    data object NotDue : AutomaticBackupOutcome

    /** A snapshot was written and verified. [prunePendingCount] documents remain beyond retention. */
    data class Created(val displayName: String, val prunedCount: Int, val prunePendingCount: Int) :
        AutomaticBackupOutcome

    /** No snapshot was produced; [kind] is recorded and surfaced. */
    data class Failed(val kind: AutomaticBackupFailureKind) : AutomaticBackupOutcome
}

/** The outcome of an enable/disable request; a refusal leaves the stored state unchanged. */
sealed interface AutomaticBackupEnableResult {
    /** Automatic backups are on (or were already on) with a usable folder. */
    data object Enabled : AutomaticBackupEnableResult

    /** Automatic backups were turned off; existing snapshots were kept. */
    data object Disabled : AutomaticBackupEnableResult

    /** No folder was chosen yet. */
    data object FolderNotConfigured : AutomaticBackupEnableResult

    /** The chosen folder is not currently usable. */
    data class FolderUnavailable(val reason: FolderUnavailableReason) : AutomaticBackupEnableResult
}
