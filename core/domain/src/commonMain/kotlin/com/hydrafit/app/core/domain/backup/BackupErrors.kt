package com.hydrafit.app.core.domain.backup

/** Why a backup could not be created or restored, mapped to a localized UI message upstream. */
enum class BackupFailure {
    /** The text is not valid backup JSON. */
    MALFORMED,

    /** A recognized but unsupported format or version. */
    UNSUPPORTED_VERSION,

    /** Above [BackupJson.MAX_BYTES]. */
    TOO_LARGE,

    /** A referenced exercise or equipment id is not a custom in the payload or a known seeded id. */
    UNKNOWN_CATALOG_ID,

    /** Two records share an identity that must be unique. */
    DUPLICATE_ID,

    /** A stored link points at a record the payload does not contain. */
    INVALID_REFERENCE,

    /** A value is not a legal enum, is non-finite, or otherwise cannot be restored. */
    INVALID_VALUE
}

/** Raised by the backup use cases; [failure] carries the typed reason for the UI. */
class BackupException(val failure: BackupFailure, message: String? = null) :
    Exception(message ?: failure.name)
