package com.hydrafit.app.feature.settings

import com.hydrafit.app.core.domain.backup.AutomaticBackupFailureKind
import com.hydrafit.app.core.domain.backup.FolderUnavailableReason

/** The outcome of the last automatic-backup action, shown as one line of status text. */
sealed interface AutomaticBackupStatus {
    /** Automatic backups are on with a usable folder. */
    data object Enabled : AutomaticBackupStatus

    /** Automatic backups are off; existing snapshots were kept. */
    data object Disabled : AutomaticBackupStatus

    /** Turning automatic backups on was refused because no folder is chosen yet. */
    data object FolderNotConfigured : AutomaticBackupStatus

    /** The chosen folder is not usable right now, so enabling was refused. */
    data class FolderUnavailable(val reason: FolderUnavailableReason) : AutomaticBackupStatus

    /** An explicit "Back up now" wrote and verified a new snapshot. */
    data object RunSucceeded : AutomaticBackupStatus

    /** An automatic run ended without a verified snapshot. */
    data class RunFailed(val kind: AutomaticBackupFailureKind) : AutomaticBackupStatus

    /** Automatic snapshots are sealed with the stored unattended key. */
    data object EncryptionOn : AutomaticBackupStatus

    /** Automatic snapshots are plain again; existing encrypted files keep their password. */
    data object EncryptionOff : AutomaticBackupStatus

    /** This platform cannot create or open encrypted snapshots (Android below 8.0). */
    data object EncryptionUnsupported : AutomaticBackupStatus

    /** The recovery password did not open the newest encrypted snapshot. */
    data object EncryptionWrongPassword : AutomaticBackupStatus

    /** Recovery had no readable encrypted snapshot to check the password against. */
    data object EncryptionNoSnapshot : AutomaticBackupStatus

    /** The unattended key was re-derived from the password and stored again. */
    data object EncryptionRecovered : AutomaticBackupStatus
}

/**
 * What the automatic-backup settings surface shows. [keptDocuments] counts only verified snapshots:
 * an unverified write is still unknown, is never pruned, and is not a usable backup yet.
 * [encryptionKeyMissing] marks automation that is encrypted on paper while its unattended key is
 * gone, which is what the re-enter-password recovery exists for.
 */
data class AutomaticBackupUiState(
    val isSupported: Boolean = true,
    val isEncryptionSupported: Boolean = true,
    val enabled: Boolean = false,
    val encryptionEnabled: Boolean = false,
    val encryptionKeyMissing: Boolean = false,
    val folderConfigured: Boolean = false,
    val hasSucceededBefore: Boolean = false,
    val lastFailure: AutomaticBackupFailureKind? = null,
    val keptDocuments: Int = 0,
    val busy: Boolean = false,
    val status: AutomaticBackupStatus? = null
)
