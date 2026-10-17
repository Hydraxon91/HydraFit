package com.hydrafit.app.core.domain.backup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest

class AutomaticBackupEncryptionSettingsTest {

    private val keyStore = FakeBackupEncryptionKeyStore()
    private val stateStore = FakeAutomaticBackupStateStore()
    private val encryption = BackupEncryption(
        crypto = FakeBackupCrypto(),
        keyStore = keyStore,
        verifySnapshot = VerifyBackupSnapshotUseCase(
            BackupValidator(automaticBackupTestCatalog())
        ),
        dispatcher = UnconfinedTestDispatcher()
    )

    @Test
    fun enablingDerivesAndStoresFreshMaterialAndFlipsTheFlag() = runTest {
        val enable = EnableAutomaticBackupEncryptionUseCase(encryption, keyStore, stateStore)

        assertEquals(AutomaticBackupEncryptionResult.Enabled, enable("pw".toCharArray()))

        assertNotNull(keyStore.stored)
        assertEquals(1, keyStore.storeCalls)
        assertEquals(true, stateStore.state.encryptionEnabled)
    }

    @Test
    fun anUnsupportedPlatformRefusesAndStoresNothing() = runTest {
        val unsupported = BackupEncryption(
            crypto = UnsupportedBackupCrypto(),
            keyStore = keyStore,
            verifySnapshot = VerifyBackupSnapshotUseCase(
                BackupValidator(automaticBackupTestCatalog())
            ),
            dispatcher = UnconfinedTestDispatcher()
        )
        val enable = EnableAutomaticBackupEncryptionUseCase(unsupported, keyStore, stateStore)

        assertEquals(AutomaticBackupEncryptionResult.Unsupported, enable("pw".toCharArray()))

        assertNull(keyStore.stored)
        assertEquals(false, stateStore.state.encryptionEnabled)
    }

    @Test
    fun disablingClearsTheMaterialAndTheFlag() = runTest {
        keyStore.stored = encryption.createUnattendedKey("pw".toCharArray())
        stateStore.update { it.copy(encryptionEnabled = true) }
        val disable = DisableAutomaticBackupEncryptionUseCase(keyStore, stateStore)

        assertEquals(AutomaticBackupEncryptionResult.Disabled, disable())

        assertNull(keyStore.stored)
        assertEquals(false, stateStore.state.encryptionEnabled)
    }

    @Test
    fun recoveryReStoresMaterialFromTheNewestEncryptedSnapshot() = runTest {
        val envelope = encryption.sealWithPassword(validAutomaticBackupText(), "pw".toCharArray())
        val folder = FakeBackupFolderStore()
        folder.documents["doc-old"] = envelope.encodeToByteArray()
        stateStore.update {
            it.copy(
                folderHandle = "tree://folder",
                documents = listOf(RecordedBackupDocument("doc-old", "a.json", 1L, verified = true))
            )
        }
        val recover = RecoverAutomaticBackupEncryptionUseCase(
            encryption,
            keyStore,
            stateStore,
            folder
        )

        assertEquals(AutomaticBackupEncryptionResult.Recovered, recover("pw".toCharArray()))

        assertNotNull(keyStore.stored)
        assertEquals(true, stateStore.state.encryptionEnabled)
    }

    @Test
    fun recoveryRejectsAWrongPasswordAndStoresNothing() = runTest {
        val envelope = encryption.sealWithPassword(validAutomaticBackupText(), "pw".toCharArray())
        val folder = FakeBackupFolderStore()
        folder.documents["doc-old"] = envelope.encodeToByteArray()
        stateStore.update {
            it.copy(
                folderHandle = "tree://folder",
                documents = listOf(RecordedBackupDocument("doc-old", "a.json", 1L, verified = true))
            )
        }
        val recover = RecoverAutomaticBackupEncryptionUseCase(
            encryption,
            keyStore,
            stateStore,
            folder
        )

        assertEquals(AutomaticBackupEncryptionResult.WrongPassword, recover("other".toCharArray()))

        assertNull(keyStore.stored)
        assertFalse(stateStore.state.encryptionEnabled)
    }

    @Test
    fun recoveryContinuesPastADamagedSnapshotToAnOlderIntactOne() = runTest {
        val password = "pw".toCharArray()
        val good = encryption.sealWithPassword(validAutomaticBackupText(), password)
        val decoded = BackupEnvelopeJson.decode(good)
        val tampered = BackupEnvelopeJson.ciphertextBytes(decoded)
            .also { it[it.size - 1] = (it.last().toInt() xor 1).toByte() }
        val damaged = good.replace(
            decoded.ciphertext,
            BackupEnvelopeJson.toBase64(tampered)
        )
        val folder = FakeBackupFolderStore()
        folder.documents["doc-new"] = damaged.encodeToByteArray()
        folder.documents["doc-old"] = good.encodeToByteArray()
        stateStore.update {
            it.copy(
                folderHandle = "tree://folder",
                documents = listOf(
                    RecordedBackupDocument("doc-new", "new.json", 2L, verified = true),
                    RecordedBackupDocument("doc-old", "old.json", 1L, verified = true)
                )
            )
        }
        val recover = RecoverAutomaticBackupEncryptionUseCase(
            encryption,
            keyStore,
            stateStore,
            folder
        )

        assertEquals(AutomaticBackupEncryptionResult.Recovered, recover(password))

        assertNotNull(keyStore.stored)
        assertEquals(true, stateStore.state.encryptionEnabled)
    }

    @Test
    fun recoveryWithoutAnEncryptedSnapshotReportsIt() = runTest {
        val folder = FakeBackupFolderStore()
        val recover = RecoverAutomaticBackupEncryptionUseCase(
            encryption,
            keyStore,
            stateStore,
            folder
        )

        assertEquals(
            AutomaticBackupEncryptionResult.NoEncryptedSnapshot,
            recover("pw".toCharArray())
        )
    }
}
