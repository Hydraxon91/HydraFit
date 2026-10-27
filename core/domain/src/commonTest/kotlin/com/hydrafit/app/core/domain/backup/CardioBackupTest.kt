package com.hydrafit.app.core.domain.backup

import com.hydrafit.app.core.domain.cardio.CardioActivity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Manual cardio activities travel in the backup as their own format-29 section. They are whole-
 * replaced on restore and added by identity on merge, and a file that predates the section is
 * rejected rather than read with the activity silently dropped.
 */
class CardioBackupTest {

    private val catalog = object : BackupCatalog {
        override fun seedExerciseIds(): Set<String> = emptySet()

        override fun builtInEquipmentIds(): Set<String> = emptySet()

        override fun dedupeSeedKeys(): Set<String> = emptySet()

        override fun seedProfiles(): Map<String, String> = emptyMap()
    }

    private val validator = BackupValidator(catalog)

    @Test
    fun aFormat29FileRoundTripsEveryActivityAndItsIdentity() {
        val file = cardioFile(
            activity(
                identity = "c-run",
                type = "RUNNING",
                startedAtMillis = 200L,
                durationSeconds = 1800,
                distanceMeters = 5000,
                note = "easy"
            ),
            activity(
                identity = "c-walk",
                type = "WALKING",
                startedAtMillis = 100L,
                durationSeconds = 600
            )
        )
        validator.validate(file)

        val decoded = BackupJson.decode(BackupJson.encode(file))
        validator.validate(decoded)

        assertEquals(listOf("c-run", "c-walk"), decoded.cardioActivities.map { it.identity })
        val run = decoded.cardioActivities.first()
        assertEquals("RUNNING", run.type)
        assertEquals(200L, run.startedAtMillis)
        assertEquals(1800, run.durationSeconds)
        assertEquals(5000, run.distanceMeters)
        assertEquals("easy", run.note)
        val walk = decoded.cardioActivities.last()
        assertEquals("WALKING", walk.type)
        assertNull(walk.distanceMeters)
        assertNull(walk.note)
    }

    @Test
    fun aPre29FileCarryingCardioActivitiesIsRejected() {
        val file = cardioFile(activity("c-run", "RUNNING"))
        validator.validate(file)

        assertFailure(BackupFailure.INVALID_VALUE) {
            file.copy(formatVersion = CARDIO_FORMAT_VERSION - 1)
        }
        // The same format-28 file without the section stays valid, so only the section is at stake.
        validator.validate(
            file.copy(formatVersion = CARDIO_FORMAT_VERSION - 1, cardioActivities = emptyList())
        )
    }

    @Test
    fun aDuplicateIdentityInsideTheFileIsRejected() {
        assertFailure(BackupFailure.DUPLICATE_ID) {
            cardioFile(activity("same", "RUNNING"), activity("same", "CYCLING"))
        }
    }

    @Test
    fun anUnknownActivityTypeIsRejected() {
        assertFailure(BackupFailure.INVALID_VALUE) {
            cardioFile(activity("c-1", "SWIMMING"))
        }
    }

    @Test
    fun aBlankOrOverlongIdentityAndAnOutOfRangeValueAreRejected() {
        val overlongIdentity =
            activity("x".repeat(CardioActivity.MAX_IDENTITY_LENGTH + 1), "RUNNING")
        val tooLongNote =
            activity("c-1", "RUNNING").copy(note = "x".repeat(CardioActivity.MAX_NOTE_LENGTH + 1))
        listOf(
            activity("", "RUNNING"),
            activity("   ", "RUNNING"),
            overlongIdentity,
            activity("c-1", "RUNNING").copy(startedAtMillis = 0L),
            activity("c-1", "RUNNING").copy(durationSeconds = 0),
            activity("c-1", "RUNNING").copy(durationSeconds = 86_401),
            activity("c-1", "RUNNING").copy(distanceMeters = 0),
            activity(
                "c-1",
                "RUNNING"
            ).copy(distanceMeters = CardioActivity.MAX_DISTANCE_METERS + 1),
            tooLongNote
        ).forEach { record ->
            assertFailure(BackupFailure.INVALID_VALUE) { cardioFile(record) }
        }
    }

    @Test
    fun mergeInsertsOnlyIdentitiesTheInstallDoesNotHold() {
        val file = cardioFile(
            activity("c-held", "RUNNING"),
            activity("c-new", "CYCLING")
        )

        val selection = BackupMergePolicy.select(file, localKeys("c-held"))

        assertEquals(
            listOf("c-new"),
            selection.cardioActivitiesToInsert.map { it.identity }
        )
        assertEquals("CYCLING", selection.cardioActivitiesToInsert.single().type)
        assertEquals(1, selection.keptLocalRows)
        assertEquals(1, selection.insertCount)
        assertTrue(selection.sessionsToInsert.isEmpty())
        assertTrue(selection.setsToInsert.isEmpty())
    }

    @Test
    fun remergingTheSameFileAddsNothing() {
        val file = cardioFile(activity("c-one", "RUNNING"), activity("c-two", "WALKING"))

        val selection = BackupMergePolicy.select(file, localKeys("c-one", "c-two"))

        assertTrue(selection.cardioActivitiesToInsert.isEmpty())
        assertEquals(2, selection.keptLocalRows)
        assertEquals(0, selection.insertCount)
        assertTrue(selection.isEmpty)
    }

    private fun assertFailure(expected: BackupFailure, mutate: () -> BackupFile) {
        val failure = assertFailsWith<BackupException> { validator.validate(mutate()) }
        assertEquals(expected, failure.failure)
    }

    private fun cardioFile(vararg activities: BackupCardioActivityRecord) =
        emptyBackupFile().copy(cardioActivities = activities.toList())

    private fun activity(
        identity: String,
        type: String,
        startedAtMillis: Long = 1L,
        durationSeconds: Int = 600,
        distanceMeters: Int? = null,
        note: String? = null
    ) = BackupCardioActivityRecord(
        identity = identity,
        type = type,
        startedAtMillis = startedAtMillis,
        durationSeconds = durationSeconds,
        distanceMeters = distanceMeters,
        note = note
    )

    private fun localKeys(vararg cardioIdentities: String) = BackupMergeKeys(
        sessionIds = emptySet(),
        bodyWeightDays = emptySet(),
        personalRecordExerciseIds = emptySet(),
        customExerciseIds = emptySet(),
        exerciseOverrideIds = emptySet(),
        exerciseNoteIds = emptySet(),
        exercisePreferenceIds = emptySet(),
        exerciseExclusionIds = emptySet(),
        cardioIdentities = cardioIdentities.toSet()
    )
}
