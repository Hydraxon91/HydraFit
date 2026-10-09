package com.hydrafit.app.core.userdata.backup

import android.content.Context
import android.net.Uri
import com.hydrafit.app.core.domain.backup.BackupException
import com.hydrafit.app.core.domain.backup.BackupFailure
import com.hydrafit.app.core.domain.backup.BackupLimits
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Android document IO over the Storage Access Framework, resolved from a `content://` handle. */
class AndroidBackupFileStore(context: Context) : BackupFileStore {
    private val resolver = context.applicationContext.contentResolver

    override val isSupported: Boolean = true

    /**
     * Reads at most [BackupLimits.MAX_BYTES] before allocating the whole payload, so an oversized
     * provider stream is rejected rather than loaded into memory.
     */
    override suspend fun read(handle: String): String = withContext(Dispatchers.IO) {
        val stream = resolver.openInputStream(Uri.parse(handle))
            ?: throw IllegalStateException("The selected file could not be opened")
        stream.use { input ->
            val output = ByteArrayOutputStream()
            val chunk = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val read = input.read(chunk)
                if (read < 0) break
                total += read
                if (total > BackupLimits.MAX_BYTES) throw BackupException(BackupFailure.TOO_LARGE)
                output.write(chunk, 0, read)
            }
            output.toByteArray().decodeToString()
        }
    }

    override suspend fun write(handle: String, text: String) {
        withContext(Dispatchers.IO) {
            val bytes = text.encodeToByteArray()
            if (bytes.size.toLong() > BackupLimits.MAX_BYTES) {
                throw BackupException(BackupFailure.TOO_LARGE)
            }
            // "wt" truncates an existing document so a shorter backup cannot leave trailing bytes.
            val stream = resolver.openOutputStream(Uri.parse(handle), "wt")
                ?: throw IllegalStateException("The destination file could not be opened")
            stream.use { it.write(bytes) }
        }
    }
}
