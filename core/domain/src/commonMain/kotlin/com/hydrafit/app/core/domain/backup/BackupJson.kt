package com.hydrafit.app.core.domain.backup

import kotlinx.serialization.json.Json

/**
 * The JSON codec for the logical backup format. Defaults are encoded so every field (including an
 * explicit `null`) is present, and unknown fields are rejected so a newer file is not silently
 * read as if it were v1.
 */
object BackupJson {
    /** Resource safeguard, not a product guarantee; both encode and decode reject above this size. */
    const val MAX_BYTES: Long = 32L * 1024 * 1024

    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    /** Decodes the payload, mapping a parse failure to [BackupFailure.MALFORMED]. */
    fun decode(text: String): BackupFile = try {
        json.decodeFromString(BackupFile.serializer(), text)
    } catch (e: Exception) {
        throw BackupException(BackupFailure.MALFORMED, e.message)
    }
}
