package com.hydrafit.app.core.userdata.transfer

import android.content.Context
import android.net.Uri
import com.hydrafit.app.core.domain.transfer.PrescriptionException
import com.hydrafit.app.core.domain.transfer.PrescriptionFailure
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Android document IO over the Storage Access Framework, resolved from a `content://` handle. */
class AndroidPrescriptionDocumentStore(context: Context) : PrescriptionDocumentStore {
    private val resolver = context.applicationContext.contentResolver

    override val isSupported: Boolean = true

    /**
     * Writes a rendered PDF, capped at [MAX_BYTES] so an oversized render is rejected before the
     * provider stream is opened. "wt" truncates an existing document so a shorter render cannot leave
     * trailing bytes.
     */
    override suspend fun write(handle: String, bytes: ByteArray) {
        withContext(Dispatchers.IO) {
            if (bytes.size.toLong() > MAX_BYTES) {
                throw PrescriptionException(PrescriptionFailure.TOO_LARGE)
            }
            val stream = resolver.openOutputStream(Uri.parse(handle), "wt")
                ?: throw IllegalStateException("The destination file could not be opened")
            stream.use { it.write(bytes) }
        }
    }

    private companion object {
        const val MAX_BYTES: Long = 4L * 1024 * 1024
    }
}
