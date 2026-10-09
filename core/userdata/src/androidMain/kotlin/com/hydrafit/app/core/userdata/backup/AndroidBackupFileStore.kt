package com.hydrafit.app.core.userdata.backup

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Android document IO over the Storage Access Framework, resolved from a `content://` handle. */
class AndroidBackupFileStore(context: Context) : BackupFileStore {
    private val resolver = context.applicationContext.contentResolver

    override val isSupported: Boolean = true

    override suspend fun read(handle: String): String = withContext(Dispatchers.IO) {
        val stream = resolver.openInputStream(Uri.parse(handle))
            ?: throw IllegalStateException("The selected file could not be opened")
        stream.use { it.readBytes().decodeToString() }
    }

    override suspend fun write(handle: String, text: String) = withContext(Dispatchers.IO) {
        // "wt" truncates an existing document so a shorter backup cannot leave trailing bytes.
        val stream = resolver.openOutputStream(Uri.parse(handle), "wt")
            ?: throw IllegalStateException("The destination file could not be opened")
        stream.use { it.write(text.encodeToByteArray()) }
    }
}
