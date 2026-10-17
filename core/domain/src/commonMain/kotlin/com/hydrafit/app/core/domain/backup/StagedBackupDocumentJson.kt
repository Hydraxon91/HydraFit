package com.hydrafit.app.core.domain.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Stable identifier of the staged-apply wrapper document. */
const val STAGED_APPLY_FORMAT: String = "hydrafit-staged-apply"

/** The wrapper version this build writes and accepts. */
const val STAGED_APPLY_VERSION: Int = 1

/**
 * The staged payload as stored between the Settings confirmation and the next process start. The
 * apply mode travels in this wrapper because the single-row staging table predates merge and a
 * schema change is not part of this release; [payload] is the validation-passed text (plain or
 * encrypted backup JSON) carried verbatim.
 */
@Serializable
data class StagedBackupDocument(
    val format: String,
    val version: Int,
    val mode: BackupApplyMode,
    val payload: String
)

/**
 * The JSON codec for [StagedBackupDocument]. Wrapper documents decode strictly (every field
 * required, unknown fields rejected, unknown mode rejected); text that is not a wrapper is a
 * payload staged by an older version and applies as [BackupApplyMode.REPLACE]. The wrapper itself is
 * not size-checked — the inner payload was already bounded when it was staged.
 */
object StagedBackupDocumentJson {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    fun encode(mode: BackupApplyMode, payload: String): String = json.encodeToString(
        StagedBackupDocument.serializer(),
        StagedBackupDocument(STAGED_APPLY_FORMAT, STAGED_APPLY_VERSION, mode, payload)
    )

    /** Cheap content detection used by the apply path; never throws. */
    fun isDocument(text: String): Boolean = text.startsWith("{\"format\":\"$STAGED_APPLY_FORMAT\"")

    /** Decodes a wrapper document, or wraps legacy raw text as a replace-mode document. */
    fun decodeOrLegacy(text: String): StagedBackupDocument {
        if (!isDocument(text)) {
            return StagedBackupDocument(
                format = STAGED_APPLY_FORMAT,
                version = STAGED_APPLY_VERSION,
                mode = BackupApplyMode.REPLACE,
                payload = text
            )
        }
        val document = try {
            json.decodeFromString(StagedBackupDocument.serializer(), text)
        } catch (e: Exception) {
            throw BackupException(BackupFailure.MALFORMED, e.message)
        }
        if (document.version != STAGED_APPLY_VERSION) {
            throw BackupException(BackupFailure.UNSUPPORTED_VERSION)
        }
        return document
    }
}
