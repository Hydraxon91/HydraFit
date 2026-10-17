package com.hydrafit.app.core.domain.backup

import kotlinx.coroutines.CompletableDeferred

/**
 * In-memory folder store for the automation tests: documents by id, injectable capabilities and
 * per-operation failures. Created documents get generated ids that never collide with seeded ids.
 */
internal class FakeBackupFolderStore : BackupFolderStore {
    override val isSupported: Boolean = true
    val documents = mutableMapOf<String, ByteArray>()
    val deletedIds = mutableListOf<String>()
    var capabilities: FolderCapabilities = FolderCapabilities.Ready
    var failCreates = false
    var failReads = false
    var failDeletes = false
    var readMutator: ((ByteArray) -> ByteArray)? = null
    var readGate: CompletableDeferred<Unit>? = null
    var createCalls = 0
    private var nextId = 1

    override suspend fun capabilities(handle: String): FolderCapabilities = capabilities

    override suspend fun create(
        handle: String,
        displayName: String,
        bytes: ByteArray
    ): StoredBackupDocument {
        createCalls++
        if (failCreates) throw BackupException(BackupFailure.IO)
        val document = StoredBackupDocument("created-${nextId++}", displayName)
        documents[document.documentId] = bytes
        return document
    }

    override suspend fun read(handle: String, documentId: String): ByteArray {
        readGate?.await()
        if (failReads) throw BackupException(BackupFailure.IO)
        val bytes = documents[documentId] ?: throw BackupException(BackupFailure.IO)
        return readMutator?.invoke(bytes) ?: bytes
    }

    override suspend fun delete(handle: String, documentId: String) {
        if (failDeletes) throw BackupException(BackupFailure.IO)
        deletedIds += documentId
        documents.remove(documentId)
    }
}

/** In-memory state store; counts mutations so tests can tell a no-op from a persisted update. */
internal class FakeAutomaticBackupStateStore(
    initial: AutomaticBackupState = AutomaticBackupState()
) : AutomaticBackupStateStore {
    var state = initial
    var saves = 0

    override suspend fun load(): AutomaticBackupState = state

    override suspend fun update(
        change: (AutomaticBackupState) -> AutomaticBackupState
    ): AutomaticBackupState {
        state = change(state)
        saves++
        return state
    }
}

/** Records scheduler calls; no timer or job actually runs. */
internal class FakeAutomaticBackupScheduler : AutomaticBackupScheduler {
    var scheduleCalls = 0
    var cancelCalls = 0

    override fun schedule() {
        scheduleCalls++
    }

    override fun cancel() {
        cancelCalls++
    }
}

/**
 * Deterministic, reversible test cipher: content bytes are XORed with the key and nonce and a
 * key-derived tag is appended, so a wrong password fails authentication exactly like the real AEAD
 * does — without pretending to be cryptography.
 */
internal class FakeBackupCrypto : BackupCrypto {
    override val isSupported: Boolean = true

    override fun randomBytes(size: Int): ByteArray = ByteArray(size) { (it + 1).toByte() }

    override fun deriveKey(
        password: CharArray,
        salt: ByteArray,
        iterations: Int,
        keyBytes: Int
    ): ByteArray = ByteArray(keyBytes) { index ->
        val passwordByte = password[index % password.size].code
        val saltByte = salt[index % salt.size].toInt()
        (passwordByte xor saltByte xor index).toByte()
    }

    override fun encrypt(key: ByteArray, nonce: ByteArray, plaintext: ByteArray): ByteArray =
        ByteArray(plaintext.size + 1) { index ->
            if (index == plaintext.size) {
                tag(key)
            } else {
                (
                    plaintext[index].toInt() xor key[index % key.size].toInt() xor
                        nonce[index % nonce.size].toInt()
                    ).toByte()
            }
        }

    override fun decrypt(key: ByteArray, nonce: ByteArray, ciphertext: ByteArray): ByteArray {
        if (ciphertext.isEmpty() || ciphertext.last() != tag(key)) {
            throw BackupException(BackupFailure.ENCRYPTION_FAILED)
        }
        val payload = ciphertext.copyOf(ciphertext.size - 1)
        return ByteArray(payload.size) { index ->
            (
                payload[index].toInt() xor key[index % key.size].toInt() xor
                    nonce[index % nonce.size].toInt()
                ).toByte()
        }
    }

    private fun tag(key: ByteArray): Byte =
        key.fold(7) { acc, byte -> (acc * 31 + byte) % 251 }.toByte()
}

/** In-memory unattended encryption material. */
internal class FakeBackupEncryptionKeyStore(var stored: StoredBackupKey? = null) :
    BackupEncryptionKeyStore {
    var storeCalls = 0
    var clearCalls = 0

    override suspend fun load(): StoredBackupKey? = stored

    override suspend fun store(key: StoredBackupKey) {
        stored = key
        storeCalls++
    }

    override suspend fun clear() {
        stored = null
        clearCalls++
    }
}

/** A repository that always exports the same file. */
internal class StaticBackupRepository(private val file: BackupFile) : BackupRepository {
    override suspend fun export(appVersion: String, exportedAtMillis: Long): BackupFile = file

    override suspend fun restore(file: BackupFile) = throw UnsupportedOperationException()
}

/** A receiver catalog that knows no seeds, matching the minimal `emptyBackupFile()` payload. */
internal fun automaticBackupTestCatalog(): BackupCatalog = object : BackupCatalog {
    override fun seedExerciseIds(): Set<String> = emptySet()

    override fun builtInEquipmentIds(): Set<String> = emptySet()

    override fun dedupeSeedKeys(): Set<String> = emptySet()

    override fun seedProfiles(): Map<String, String> = emptyMap()
}

/** The text of a minimal backup this install accepts; used as a complete stored snapshot. */
internal fun validAutomaticBackupText(): String = BackupJson.encode(emptyBackupFile())
