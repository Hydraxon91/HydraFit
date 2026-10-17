package com.hydrafit.app.core.domain.backup

/** Outcome of an automatic-backup encryption settings request. */
sealed interface AutomaticBackupEncryptionResult {
    /** Encryption is on: the unattended material is stored and automatic runs seal their snapshots. */
    data object Enabled : AutomaticBackupEncryptionResult

    /** Encryption is off: the unattended material is cleared; existing files keep their password. */
    data object Disabled : AutomaticBackupEncryptionResult

    /** The password opened the newest encrypted snapshot and the material is stored again. */
    data object Recovered : AutomaticBackupEncryptionResult

    /** The platform cannot create or read encrypted backups (Android below 8.0). */
    data object Unsupported : AutomaticBackupEncryptionResult

    /** Recovery found no readable encrypted snapshot to authenticate the password against. */
    data object NoEncryptedSnapshot : AutomaticBackupEncryptionResult

    /** Recovery could not open the newest encrypted snapshot with that password. */
    data object WrongPassword : AutomaticBackupEncryptionResult
}
