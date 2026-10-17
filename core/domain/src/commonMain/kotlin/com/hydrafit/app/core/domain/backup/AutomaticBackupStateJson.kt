package com.hydrafit.app.core.domain.backup

import kotlinx.serialization.json.Json

/**
 * The JSON codec for the device-local automation state. Defaults are encoded and unknown fields are
 * rejected, mirroring [BackupJson]; an unreadable or unknown-version document decodes to null and
 * the store falls back to the disabled default. That fallback is deliberately fail-safe: it never
 * guesses at a folder grant, and it never deletes a file the ledger cannot account for.
 */
object AutomaticBackupStateJson {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
    }

    fun encode(state: AutomaticBackupState): String =
        json.encodeToString(AutomaticBackupState.serializer(), state)

    /** Decodes a stored state document, or null when it is not a readable current-version state. */
    fun decodeOrNull(text: String): AutomaticBackupState? = try {
        val state = json.decodeFromString(AutomaticBackupState.serializer(), text)
        state.takeIf {
            it.format == AUTOMATIC_BACKUP_STATE_FORMAT &&
                it.formatVersion == AUTOMATIC_BACKUP_STATE_FORMAT_VERSION
        }
    } catch (_: Exception) {
        // A corrupt or foreign state document reads as "not stored"; see the class KDoc.
        null
    }
}
