package com.hydrafit.app.core.domain.backup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Boundary tests for the resource limits enforced by [BackupJson]. */
class BackupJsonTest {

    @Test
    fun rejectsNestingDeeperThanTheLimit() {
        val brackets = BackupLimits.MAX_DEPTH + 1
        assertFailure(BackupFailure.OVER_LIMIT, "[".repeat(brackets) + "]".repeat(brackets))
    }

    @Test
    fun nestingAtTheLimitIsNotRejectedAsOverLimit() {
        val brackets = BackupLimits.MAX_DEPTH
        // A valid JSON value at the depth limit parses, then fails only as a non-object.
        assertFailure(BackupFailure.MALFORMED, "[".repeat(brackets) + "]".repeat(brackets))
    }

    @Test
    fun rejectsAStringLongerThanTheLimit() {
        val text = "\"" + "a".repeat(BackupLimits.MAX_STRING_LENGTH + 1) + "\""
        assertFailure(BackupFailure.OVER_LIMIT, text)
    }

    @Test
    fun acceptsAStringAtTheLengthLimit() {
        val file = emptyBackupFile().copy(
            appVersion = "A".repeat(BackupLimits.MAX_STRING_LENGTH)
        )

        assertEquals(file, BackupJson.decode(BackupJson.encode(file)))
    }

    @Test
    fun versionOneSettingsDefaultGuidedWorkoutToOff() {
        val legacy = emptyBackupFile().copy(
            formatVersion = 1,
            settings = BackupSettingsRecord(
                engineId = "DETERMINISTIC",
                daysPerWeek = 4,
                trainingGoal = "BALANCED",
                shareWorkoutData = false,
                weightUnit = "KG"
            )
        )
        val legacyJson = BackupJson.encode(legacy)
            .replace("\"formatVersion\":2", "\"formatVersion\":1")
            .replace(",\"guidedWorkoutEnabled\":false", "")

        val decoded = BackupJson.decode(legacyJson)

        assertEquals(false, decoded.settings?.guidedWorkoutEnabled)
        assertEquals(1, decoded.formatVersion)
    }

    @Test
    fun formatFourRequiresAllTimingFieldsButAcceptsExplicitNulls() {
        val file = emptyBackupFile().copy(workoutSets = listOf(workoutSet("LIVE", null, null)))
        val encoded = BackupJson.encode(file)
        val missing = encoded.replace(",\"startedAtElapsedMillis\":null", "")
        val missingCompletion = encoded.replace(",\"completedAtElapsedMillis\":null", "")

        assertFailure(BackupFailure.MALFORMED, missing)
        assertFailure(BackupFailure.MALFORMED, missingCompletion)
        assertEquals(file, BackupJson.decode(encoded))
    }

    @Test
    fun formatThreePreservesProvenanceAndDefaultsElapsedFields() {
        val encoded = BackupJson.encode(
            emptyBackupFile().copy(
                formatVersion = 3,
                workoutSets = listOf(workoutSet("LIVE", null, null))
            )
        ).replace(",\"startedAtElapsedMillis\":null", "")
            .replace(",\"completedAtElapsedMillis\":null", "")

        val decoded = BackupJson.decode(encoded)

        assertEquals("LIVE", decoded.workoutSets.single().timingProvenance)
        assertNull(decoded.workoutSets.single().startedAtElapsedMillis)
        assertNull(decoded.workoutSets.single().completedAtElapsedMillis)
    }

    @Test
    fun formatThreeRequiresItsExistingProvenanceField() {
        val encoded = BackupJson.encode(
            emptyBackupFile().copy(
                formatVersion = 3,
                workoutSets = listOf(workoutSet("LIVE", null, null))
            )
        ).replace(",\"startedAtElapsedMillis\":null", "")
            .replace(",\"completedAtElapsedMillis\":null", "")
            .replace(",\"timingProvenance\":\"LIVE\"", "")

        assertFailure(BackupFailure.MALFORMED, encoded)
    }

    @Test
    fun formatFiveRequiresRestPreferencesWhileFormatFourDefaultsThem() {
        val current = BackupJson.encode(emptyBackupFile())
        val missingCurrentPreferences = current.replace(
            ",\"restPreferences\":[{\"exerciseId\":null,\"durationSeconds\":120}]",
            ""
        )
        assertFailure(BackupFailure.MALFORMED, missingCurrentPreferences)

        val old = BackupJson.encode(emptyBackupFile().copy(formatVersion = 4))
            .replace(",\"restPreferences\":[{\"exerciseId\":null,\"durationSeconds\":120}]", "")
        assertEquals(emptyList(), BackupJson.decode(old).restPreferences)
    }

    @Test
    fun formatsOneAndTwoDefaultMissingTimingFields() {
        val baseFile = emptyBackupFile().copy(
            workoutSets = listOf(workoutSet("UNKNOWN", null, null))
        )
        val base = BackupJson.encode(baseFile)
            .replace(",\"timingProvenance\":\"UNKNOWN\"", "")
            .replace(",\"startedAtElapsedMillis\":null", "")
            .replace(",\"completedAtElapsedMillis\":null", "")

        listOf(1, 2).forEach { version ->
            val versioned = base.replace("\"formatVersion\":5", "\"formatVersion\":$version")
            val decoded = BackupJson.decode(versioned)
            assertEquals("UNKNOWN", decoded.workoutSets.single().timingProvenance)
            assertNull(decoded.workoutSets.single().startedAtElapsedMillis)
            assertNull(decoded.workoutSets.single().completedAtElapsedMillis)
        }
    }

    @Test
    fun rejectsMoreRecordsThanTheLimit() {
        val file = emptyBackupFile().copy(
            preferences = List(BackupLimits.MAX_RECORDS + 1) { index ->
                BackupPreferenceRecord("ex-$index", "NEUTRAL")
            }
        )

        val failure = assertFailsWith<BackupException> { BackupJson.encode(file) }
        assertEquals(BackupFailure.OVER_LIMIT, failure.failure)
    }

    private fun assertFailure(expected: BackupFailure, text: String) {
        val failure = assertFailsWith<BackupException> { BackupJson.decode(text) }
        assertEquals(expected, failure.failure)
    }

    private fun workoutSet(provenance: String, startedAt: Long?, completedAt: Long?) =
        BackupWorkoutSetRecord(
            id = 1L,
            exerciseId = "exercise",
            reps = 5,
            weightKg = 10.0,
            performedAtMillis = 1L,
            isWarmup = false,
            involvements = null,
            weekNumber = null,
            cycleNumber = null,
            dayIndex = null,
            rir = null,
            sessionId = "session",
            occurrenceId = null,
            occurrenceEntryId = null,
            loadKind = "EXTERNAL",
            timingProvenance = provenance,
            startedAtElapsedMillis = startedAt,
            completedAtElapsedMillis = completedAt
        )
}
