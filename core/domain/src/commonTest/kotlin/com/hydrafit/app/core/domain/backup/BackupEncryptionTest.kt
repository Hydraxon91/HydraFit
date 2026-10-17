package com.hydrafit.app.core.domain.backup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

class BackupEncryptionTest {

    private val keyStore = FakeBackupEncryptionKeyStore()
    private val encryption = BackupEncryption(
        crypto = FakeBackupCrypto(),
        keyStore = keyStore,
        verifySnapshot = VerifyBackupSnapshotUseCase(
            BackupValidator(automaticBackupTestCatalog())
        ),
        dispatcher = UnconfinedTestDispatcher()
    )

    @Test
    fun sealsAndOpensWithThePassword() = runTest {
        val text = encryption.sealWithPassword(validAutomaticBackupText(), "pw".toCharArray())

        assertTrue(encryption.looksEncrypted(text))
        assertEquals(
            validAutomaticBackupText(),
            encryption.unsealWithPassword(text, "pw".toCharArray())
        )
    }

    @Test
    fun aWrongPasswordFailsAuthentication() = runTest {
        val text = encryption.sealWithPassword(validAutomaticBackupText(), "pw".toCharArray())

        val thrown = assertFailsWith<BackupException> {
            encryption.unsealWithPassword(text, "other".toCharArray())
        }

        assertEquals(BackupFailure.ENCRYPTION_FAILED, thrown.failure)
    }

    @Test
    fun unattendedSealingRequiresStoredMaterial() = runTest {
        val thrown = assertFailsWith<BackupException> {
            encryption.sealUnattended(validAutomaticBackupText())
        }

        assertEquals(BackupFailure.ENCRYPTION_KEY_UNAVAILABLE, thrown.failure)
    }

    @Test
    fun unattendedMaterialOpensTheSealedSnapshotWithThePassword() = runTest {
        keyStore.stored = encryption.createUnattendedKey("pw".toCharArray())

        val text = encryption.sealUnattended(validAutomaticBackupText())

        assertEquals(
            validAutomaticBackupText(),
            encryption.unsealWithPassword(text, "pw".toCharArray())
        )
        assertTrue(encryption.verifyStored(text))
    }

    @Test
    fun verifyStoredPassesPlaintextThroughAndRejectsUnreadableFiles() = runTest {
        assertTrue(encryption.verifyStored(validAutomaticBackupText()))
        assertFalse(encryption.verifyStored("{\"format\":\"broken\"}"))

        val text = encryption.sealWithPassword(validAutomaticBackupText(), "pw".toCharArray())

        // No stored material matches a file sealed for a person, so it stays unverified here.
        assertFalse(encryption.verifyStored(text))
    }

    @Test
    fun recoveryMaterialAuthenticatesBeforeItCanBeStored() = runTest {
        val text = encryption.sealWithPassword(validAutomaticBackupText(), "pw".toCharArray())

        val key = encryption.deriveKeyForStoredFile(text, "pw".toCharArray())

        assertNull(keyStore.stored)
        assertEquals(
            BackupEnvelopeJson.saltBytes(BackupEnvelopeJson.decode(text)).toList(),
            key.salt.toList()
        )
        assertFailsWith<BackupException> {
            encryption.deriveKeyForStoredFile(text, "other".toCharArray())
        }
    }
}
