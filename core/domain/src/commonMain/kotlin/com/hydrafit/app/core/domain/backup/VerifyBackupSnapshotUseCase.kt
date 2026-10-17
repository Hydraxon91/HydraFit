package com.hydrafit.app.core.domain.backup

/**
 * Checks that a stored snapshot's text is a complete backup this install can restore: strict decode
 * plus full validation, the same acceptance the import path applies before a restore is staged.
 * Used to reconcile a snapshot whose same-run read-back check did not complete before the process
 * ended. Returns false instead of throwing: an unusable snapshot stays recorded and unverified,
 * which never blocks a later run and never authorizes its deletion.
 */
class VerifyBackupSnapshotUseCase(private val validator: BackupValidator) {

    operator fun invoke(text: String): Boolean = try {
        validator.validate(BackupJson.decode(text))
        true
    } catch (_: BackupException) {
        false
    }
}
