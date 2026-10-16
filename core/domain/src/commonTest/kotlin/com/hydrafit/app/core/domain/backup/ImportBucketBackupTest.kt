package com.hydrafit.app.core.domain.backup

import com.hydrafit.app.core.domain.transfer.WorkoutImportPartition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Format 16 carries the bucket/partition ledger. The section is gated in both directions, and its
 * stored manifest must be readable and agree with the links restored beside it, so a corrupt or
 * hand-edited record cannot be reused as if it were the approved partition.
 */
class ImportBucketBackupTest {

    private val catalog = object : BackupCatalog {
        override fun seedExerciseIds(): Set<String> = emptySet()

        override fun builtInEquipmentIds(): Set<String> = emptySet()
    }

    private val validator = BackupValidator(catalog)

    private fun record(
        partition: String = WorkoutImportPartition(2, listOf(listOf(0, 1)), emptySet()).canonical(),
        sessionIds: List<String> = listOf("s1")
    ) = BackupImportBucketRecord(
        source = "FITNOTES",
        candidateKey = "10:FITNOTES13:2024-01-02",
        content = "11:Bench Press",
        partition = partition,
        importedSessionIds = sessionIds
    )

    @Test
    fun acceptsAReadableManifestWhoseSessionsMatchItsLinks() {
        validator.validate(emptyBackupFile().copy(importBuckets = listOf(record())))
    }

    @Test
    fun acceptsASplitBucketWithOneLinkPerSession() {
        validator.validate(
            emptyBackupFile().copy(
                importBuckets = listOf(
                    record(
                        partition = WorkoutImportPartition(
                            2,
                            listOf(listOf(0), listOf(1)),
                            emptySet()
                        ).canonical(),
                        sessionIds = listOf("first", "second")
                    )
                )
            )
        )
    }

    @Test
    fun rejectsAManifestThatCannotBeReadBackOrDisagreesWithItsLinks() {
        listOf(
            // Not a manifest this build wrote, so its partition can never be matched again.
            record(partition = "not-a-partition"),
            // Two recorded sessions for a one-session partition.
            record(sessionIds = listOf("first", "second")),
            // A repeated link is not a second session.
            record(sessionIds = listOf("same", "same"))
        ).forEach { invalid ->
            val failure = assertFailsWith<BackupException> {
                validator.validate(emptyBackupFile().copy(importBuckets = listOf(invalid)))
            }
            assertEquals(BackupFailure.INVALID_VALUE, failure.failure)
        }
    }

    @Test
    fun rejectsTheSectionInAPreSixteenFile() {
        val failure = assertFailsWith<BackupException> {
            validator.validate(
                emptyBackupFile().copy(
                    formatVersion = IMPORT_BUCKET_FORMAT_VERSION - 1,
                    importBuckets = listOf(record())
                )
            )
        }
        assertEquals(BackupFailure.INVALID_VALUE, failure.failure)
    }
}
