package com.hydrafit.app.core.userdata.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.hydrafit.app.core.domain.backup.BackupException
import com.hydrafit.app.core.domain.backup.BackupFailure
import com.hydrafit.app.core.domain.backup.BackupFolderStore
import com.hydrafit.app.core.domain.backup.BackupLimits
import com.hydrafit.app.core.domain.backup.FolderCapabilities
import com.hydrafit.app.core.domain.backup.FolderUnavailableReason
import com.hydrafit.app.core.domain.backup.StoredBackupDocument
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android folder IO for automatic backups over a Storage Access Framework tree grant. The user
 * picks the folder with `ACTION_OPEN_DOCUMENT_TREE`; the persisted read/write grant is the
 * handle's authority, and the provider's returned document id is the snapshot identity.
 *
 * Capability checks cover what can be decided before writing: the persisted grant, the folder's
 * existence and the provider's child-creation support. Delete support is decided when pruning
 * runs; a failed delete keeps the file and is reported, never guessed away.
 */
class AndroidBackupFolderStore(context: Context) : BackupFolderStore {
    private val resolver = context.applicationContext.contentResolver

    override val isSupported: Boolean = true

    override suspend fun capabilities(handle: String): FolderCapabilities =
        withContext(Dispatchers.IO) {
            val tree = Uri.parse(handle)
            if (!hasPersistedGrant(tree)) {
                return@withContext FolderCapabilities.Unavailable(
                    FolderUnavailableReason.PERMISSION_LOST
                )
            }
            val flags = folderFlags(tree)
                ?: return@withContext FolderCapabilities.Unavailable(
                    FolderUnavailableReason.FOLDER_MISSING
                )
            if (flags and DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE == 0) {
                return@withContext FolderCapabilities.Unavailable(
                    FolderUnavailableReason.UNSUPPORTED_PROVIDER
                )
            }
            FolderCapabilities.Ready
        }

    override suspend fun create(
        handle: String,
        displayName: String,
        bytes: ByteArray
    ): StoredBackupDocument = withContext(Dispatchers.IO) {
        if (bytes.size.toLong() > BackupLimits.MAX_BYTES) {
            throw BackupException(BackupFailure.TOO_LARGE)
        }
        val document = try {
            DocumentsContract.createDocument(resolver, Uri.parse(handle), MIME_TYPE, displayName)
        } catch (e: Exception) {
            throw BackupException(BackupFailure.IO, e.message)
        } ?: throw BackupException(BackupFailure.IO, "The folder refused a new document")
        try {
            val stream = resolver.openOutputStream(document, "w")
                ?: throw BackupException(BackupFailure.IO, "The snapshot could not be opened")
            stream.use { it.write(bytes) }
        } catch (e: BackupException) {
            removeFailedWrite(document)
            throw e
        } catch (e: Exception) {
            removeFailedWrite(document)
            throw BackupException(BackupFailure.IO, e.message)
        }
        StoredBackupDocument(DocumentsContract.getDocumentId(document), displayName)
    }

    override suspend fun read(handle: String, documentId: String): ByteArray =
        withContext(Dispatchers.IO) {
            val stream = try {
                resolver.openInputStream(childUri(handle, documentId))
            } catch (e: Exception) {
                throw BackupException(BackupFailure.IO, e.message)
            } ?: throw BackupException(BackupFailure.IO, "The snapshot could not be opened")
            stream.use { input ->
                val output = ByteArrayOutputStream()
                val chunk = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val read = input.read(chunk)
                    if (read < 0) break
                    total += read
                    if (total > BackupLimits.MAX_BYTES) {
                        throw BackupException(BackupFailure.TOO_LARGE)
                    }
                    output.write(chunk, 0, read)
                }
                output.toByteArray()
            }
        }

    /**
     * Idempotent: an already-missing document counts as deleted, which repeated pruning relies on.
     */
    override suspend fun delete(handle: String, documentId: String): Unit =
        withContext(Dispatchers.IO) {
            val uri = childUri(handle, documentId)
            val deleted = try {
                DocumentsContract.deleteDocument(resolver, uri)
            } catch (_: FileNotFoundException) {
                true
            } catch (e: Exception) {
                throw BackupException(BackupFailure.IO, e.message)
            }
            if (!deleted && documentExists(uri)) {
                throw BackupException(BackupFailure.IO, "The snapshot could not be deleted")
            }
        }

    private fun hasPersistedGrant(tree: Uri): Boolean =
        resolver.persistedUriPermissions.any { permission ->
            permission.uri == tree && permission.isReadPermission && permission.isWritePermission
        }

    private fun folderFlags(tree: Uri): Int? = try {
        resolver.query(tree, arrayOf(DocumentsContract.Document.COLUMN_FLAGS), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else null }
    } catch (_: Exception) {
        null
    }

    private fun documentExists(uri: Uri): Boolean = try {
        resolver.query(
            uri,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
            null,
            null,
            null
        )?.use { cursor -> cursor.moveToFirst() } ?: false
    } catch (_: Exception) {
        false
    }

    private fun childUri(handle: String, documentId: String): Uri =
        DocumentsContract.buildDocumentUriUsingTree(Uri.parse(handle), documentId)

    /** Removes a just-created partial document so a failed write is not left behind as junk. */
    private fun removeFailedWrite(document: Uri) {
        runCatching { DocumentsContract.deleteDocument(resolver, document) }
    }

    private companion object {
        const val MIME_TYPE = "application/json"
    }
}
