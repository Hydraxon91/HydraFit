package com.hydrafit.app.core.domain.backup

/**
 * Resource safeguards for reading and writing a backup file. These are protective limits, not a
 * product guarantee; they are confirmed by boundary tests and enforced before a payload is decoded or
 * written.
 */
object BackupLimits {
    /** Total UTF-8 size accepted on read and produced on write. */
    const val MAX_BYTES: Long = 32L * 1024 * 1024

    /** Maximum JSON nesting depth. */
    const val MAX_DEPTH: Int = 32

    /** Maximum bytes in a single JSON string value. */
    const val MAX_STRING_LENGTH: Int = 64 * 1024

    /** Maximum total number of records across every section. */
    const val MAX_RECORDS: Int = 250_000
}
