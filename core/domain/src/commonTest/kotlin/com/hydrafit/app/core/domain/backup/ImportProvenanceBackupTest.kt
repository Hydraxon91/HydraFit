package com.hydrafit.app.core.domain.backup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Format 15 carries local workout-import provenance. The section is gated in both directions, and a
 * record may name an imported session the file no longer contains — that is the deleted-history guard
 * rather than a dangling reference.
 */
class ImportProvenanceBackupTest {

    private val catalog = object : BackupCatalog {
        override fun seedExerciseIds(): Set<String> = emptySet()

        override fun builtInEquipmentIds(): Set<String> = emptySet()
    }

    private val validator = BackupValidator(catalog)

    private fun record(sessionId: String = "s1") = BackupImportProvenanceRecord(
        source = "FITNOTES",
        candidateKey = "10:FITNOTES13:2024-01-02",
        content = "11:Bench Press",
        importedSessionId = sessionId
    )

    @Test
    fun acceptsRecordsAtTheIntroducedFormat() {
        validator.validate(emptyBackupFile().copy(importProvenance = listOf(record())))
    }

    @Test
    fun acceptsARecordWhoseImportedSessionIsNoLongerPresent() {
        validator.validate(
            emptyBackupFile().copy(
                importProvenance = listOf(record(sessionId = "deleted-locally"))
            )
        )
    }

    @Test
    fun rejectsTheSectionInAPreFifteenFile() {
        val failure = assertFailsWith<BackupException> {
            validator.validate(
                emptyBackupFile().copy(
                    formatVersion = IMPORT_PROVENANCE_FORMAT_VERSION - 1,
                    importProvenance = listOf(record())
                )
            )
        }
        assertEquals(BackupFailure.INVALID_VALUE, failure.failure)
    }

    @Test
    fun rejectsAnUnknownSourceAnEmptyFieldAndADuplicateRecord() {
        listOf(
            record().copy(source = "MYSTERY_APP"),
            record().copy(candidateKey = ""),
            record().copy(content = ""),
            record().copy(importedSessionId = "")
        ).forEach { invalid ->
            val failure = assertFailsWith<BackupException> {
                validator.validate(emptyBackupFile().copy(importProvenance = listOf(invalid)))
            }
            assertEquals(BackupFailure.INVALID_VALUE, failure.failure)
        }
        assertFailsWith<BackupException> {
            validator.validate(
                emptyBackupFile().copy(importProvenance = listOf(record(), record()))
            )
        }
    }

    @Test
    fun jsonRequiresTheSectionAtFormatFifteenAndRejectsItInALegacyEnvelope() {
        val current = emptyBackupFile().copy(importProvenance = listOf(record()))
        assertEquals(current, BackupJson.decode(BackupJson.encode(current)))

        val withEmptySection = BackupJson.encode(emptyBackupFile())
        val missing = withEmptySection.replace(",\"importProvenance\":[]", "")
        assertFailsWith<BackupException> { BackupJson.decode(missing) }

        val legacy = emptyBackupFile().copy(formatVersion = 14)
        assertEquals(legacy, BackupJson.decode(BackupJson.encode(legacy)))
    }
}
