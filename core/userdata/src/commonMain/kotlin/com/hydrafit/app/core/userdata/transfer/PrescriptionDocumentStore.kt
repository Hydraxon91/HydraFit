package com.hydrafit.app.core.userdata.transfer

/**
 * Writes rendered prescription bytes against a user-chosen document handle. On Android the handle is
 * a `content://` Storage Access Framework URI chosen by the user, and the bytes are a rendered PDF;
 * platforms without a document surface report [isSupported] false and throw.
 */
interface PrescriptionDocumentStore {
    val isSupported: Boolean

    suspend fun write(handle: String, bytes: ByteArray)
}

/** Fallback binding for platforms with no prescription document surface (iOS in 0.9.x). */
class UnsupportedPrescriptionDocumentStore : PrescriptionDocumentStore {
    override val isSupported: Boolean = false

    override suspend fun write(handle: String, bytes: ByteArray) =
        throw UnsupportedOperationException(
            "Prescription documents are not supported on this platform"
        )
}
