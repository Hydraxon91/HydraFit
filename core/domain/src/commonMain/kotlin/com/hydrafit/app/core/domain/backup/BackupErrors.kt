package com.hydrafit.app.core.domain.backup

/** Why a backup could not be created or restored, mapped to a localized UI message upstream. */
enum class BackupFailure {
    /** The text is not valid backup JSON. */
    MALFORMED,

    /** A recognized but unsupported format or version. */
    UNSUPPORTED_VERSION,

    /** Above [BackupJson.MAX_BYTES]. */
    TOO_LARGE,

    /** Above a resource limit: nesting depth, a single string, or the total record count. */
    OVER_LIMIT,

    /**
     * The payload is valid but not stable under startup maintenance: a non-CAT-P7 custom would be
     * merged by dedupe, or a legacy set with no session id would be re-segmented by the backfill.
     */
    STARTUP_UNSTABLE,

    /**
     * A seed the backup relies on is unknown to this install, or this install defines that seed with a
     * different profile than the exporting install did.
     */
    CATALOG_MISMATCH,

    /** A referenced exercise or equipment id is not a custom in the payload or a known seeded id. */
    UNKNOWN_CATALOG_ID,

    /** Two records share an identity that must be unique. */
    DUPLICATE_ID,

    /** A stored link points at a record the payload does not contain. */
    INVALID_REFERENCE,

    /** A value is not a legal enum, is non-finite, or otherwise cannot be restored. */
    INVALID_VALUE,

    /** The file could not be read from or written to the chosen location. */
    IO
}

/** Raised by the backup use cases; [failure] carries the typed reason for the UI. */
class BackupException(val failure: BackupFailure, message: String? = null) :
    Exception(message ?: failure.name)
