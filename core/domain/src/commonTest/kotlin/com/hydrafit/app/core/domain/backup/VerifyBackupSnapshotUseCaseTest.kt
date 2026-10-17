package com.hydrafit.app.core.domain.backup

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VerifyBackupSnapshotUseCaseTest {

    private val verify = VerifyBackupSnapshotUseCase(BackupValidator(automaticBackupTestCatalog()))

    @Test
    fun acceptsAProducedBackup() {
        assertTrue(verify(validAutomaticBackupText()))
    }

    @Test
    fun rejectsAnUnsupportedFormatVersion() {
        val tampered = validAutomaticBackupText().replace(
            "\"formatVersion\":$BACKUP_FORMAT_VERSION",
            "\"formatVersion\":${BACKUP_FORMAT_VERSION + 1}"
        )

        assertFalse(verify(tampered))
    }

    @Test
    fun rejectsAMissingRequiredSection() {
        val tampered = validAutomaticBackupText()
            .replace(Regex("\"workoutSets\":\\[[^]]*\\],?"), "")

        assertFalse(verify(tampered))
    }

    @Test
    fun rejectsATruncatedOrNonJsonPayload() {
        val text = validAutomaticBackupText()

        assertFalse(verify(text.take(text.length / 2)))
        assertFalse(verify("{}"))
    }
}
