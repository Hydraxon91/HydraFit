package com.hydrafit.app.core.domain.backup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Boundary tests for the resource limits enforced by [BackupJson]. */
class BackupJsonTest {

    @Test
    fun setupAndWeekdaySectionsAreRequiredFromFormatElevenOnward() {
        val file = emptyBackupFile()
        val text = BackupJson.encode(file)
        assertEquals(file, BackupJson.decode(text))
        assertFailure(BackupFailure.MALFORMED, text.replace(",\"preferredWeekdays\":null", ""))
        assertFailure(BackupFailure.MALFORMED, text.replace("\"version\":1,", ""))
        val withoutSetup = text.replace(
            ",\"setup\":{\"version\":1,\"disposition\":\"RETURNING\",\"step\":\"WELCOME\"}",
            ""
        ).replace(",\"preferredWeekdays\":null", "")
        assertFailure(BackupFailure.MALFORMED, withoutSetup)
        assertEquals(
            BackupSetupRecord(),
            BackupJson.decode(
                withoutSetup.replace(
                    "\"formatVersion\":$BACKUP_FORMAT_VERSION",
                    "\"formatVersion\":10"
                )
            ).setup
        )
    }

    @Test
    fun formatTwelveRequiresReminderPreferencesAndEarlierFormatsDefaultThemOff() {
        val file = emptyBackupFile().copy(
            workoutReminders = BackupWorkoutReminderRecord(
                remindersEnabled = true,
                reminderMinuteOfDay = 390,
                quietHoursEnabled = true,
                quietStartMinuteOfDay = 1320,
                quietEndMinuteOfDay = 360
            )
        )
        val encoded = BackupJson.encode(file)
        assertEquals(file, BackupJson.decode(encoded))
        val withoutReminders = encoded.replace(
            ",\"workoutReminders\":{\"remindersEnabled\":true,\"reminderMinuteOfDay\":390," +
                "\"quietHoursEnabled\":true,\"quietStartMinuteOfDay\":1320," +
                "\"quietEndMinuteOfDay\":360}",
            ""
        )
        // The current format must carry the section.
        assertFailure(BackupFailure.MALFORMED, withoutReminders)
        // A file from before format 12 predates it and restores with no preference recorded.
        assertNull(
            BackupJson.decode(
                withoutReminders.replace(
                    "\"formatVersion\":$BACKUP_FORMAT_VERSION",
                    "\"formatVersion\":11"
                )
            ).workoutReminders
        )
    }

    @Test
    fun formatThirteenRequiresBodyWeightSectionsAndEarlierFormatsDefaultThemOff() {
        val file = emptyBackupFile().copy(
            bodyWeightMeasurements = listOf(BackupBodyWeightRecord(20_000L, 82.5)),
            bodyWeightGoalKg = 78.0
        )
        val encoded = BackupJson.encode(file)
        assertEquals(file, BackupJson.decode(encoded))
        val withoutBodyWeight = encoded.replace(
            ",\"bodyWeightMeasurements\":[{\"localEpochDay\":20000,\"weightKg\":82.5}]",
            ""
        ).replace(",\"bodyWeightGoalKg\":78.0", "")
        // The current format must carry both sections.
        assertFailure(BackupFailure.MALFORMED, withoutBodyWeight)
        // A file from before format 13 predates them and restores with no recorded body weight.
        val legacy = BackupJson.decode(
            withoutBodyWeight.replace(
                "\"formatVersion\":$BACKUP_FORMAT_VERSION",
                "\"formatVersion\":12"
            )
        )
        assertTrue(legacy.bodyWeightMeasurements.isEmpty())
        assertNull(legacy.bodyWeightGoalKg)
    }

    @Test
    fun formatSixRequiresOptInsAndOlderFormatsDefaultThemOff() {
        val file = emptyBackupFile().copy(
            formatVersion = 6,
            trainingGuidance = BackupTrainingGuidanceRecord(true, true)
        )
        val encoded = BackupJson.encode(file)
        assertEquals(file, BackupJson.decode(encoded))
        val withoutGuidance = encoded.replace(
            ",\"trainingGuidance\":{\"restAlertsEnabled\":true,\"shortRestCoachingEnabled\":true," +
                "\"restCountdownEnabled\":false,\"restCompletionSignalEnabled\":false}",
            ""
        )
        assertFailure(BackupFailure.MALFORMED, withoutGuidance)
        assertEquals(
            BackupTrainingGuidanceRecord(false, false),
            BackupJson.decode(withoutGuidance.replace("\"formatVersion\":6", "\"formatVersion\":5"))
                .trainingGuidance
        )
    }

    @Test
    fun legacyGuidanceMissingRestOptInsDefaultsThemOff() {
        val file = emptyBackupFile().copy(
            formatVersion = 6,
            trainingGuidance = BackupTrainingGuidanceRecord(true, true)
        )
        // A v6 file predates the rest booleans, so its guidance object may omit them.
        val encoded = BackupJson.encode(file).replace(
            ",\"restCountdownEnabled\":false,\"restCompletionSignalEnabled\":false",
            ""
        )

        val decoded = BackupJson.decode(encoded)

        assertEquals(6, decoded.formatVersion)
        assertEquals(BackupTrainingGuidanceRecord(true, true), decoded.trainingGuidance)
    }

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
    fun formatsTwoThroughSixRequireGuidedWorkoutSettingWhenSettingsArePresent() {
        val file = emptyBackupFile().copy(
            settings = BackupSettingsRecord(
                engineId = "DETERMINISTIC",
                daysPerWeek = 4,
                trainingGoal = "BALANCED",
                shareWorkoutData = false,
                weightUnit = "KG"
            )
        )
        val complete = BackupJson.encode(file)
        val incomplete = complete.replace(",\"guidedWorkoutEnabled\":false", "")

        listOf(2, 3, 4, 5, 6).forEach { version ->
            val versioned = complete.replace(
                "\"formatVersion\":$BACKUP_FORMAT_VERSION",
                "\"formatVersion\":$version"
            )
            val missing = incomplete.replace(
                "\"formatVersion\":$BACKUP_FORMAT_VERSION",
                "\"formatVersion\":$version"
            )
            assertEquals(file.copy(formatVersion = version), BackupJson.decode(versioned))
            assertFailure(BackupFailure.MALFORMED, missing)
        }
    }

    @Test
    fun formatSevenRequiresTargetExplanationSectionsAndEarlierFormatsDefaultThem() {
        val file = emptyBackupFile()
        val encoded = BackupJson.encode(file)
        val withoutExplanations = encoded
            .replace(",\"planTargetExplanations\":[]", "")
            .replace(",\"planTargetExplanationStates\":[]", "")

        // A v6 file predates the sections and restores as "not recorded" rather than as empty ones.
        assertEquals(
            file.copy(formatVersion = 6),
            BackupJson.decode(
                withoutExplanations.replace(
                    "\"formatVersion\":$BACKUP_FORMAT_VERSION",
                    "\"formatVersion\":6"
                )
            )
        )
        // The current format must carry them.
        assertFailure(BackupFailure.MALFORMED, withoutExplanations)
    }

    @Test
    fun formatNineRequiresExerciseNotesAndEarlierFormatsDefaultThem() {
        val file = emptyBackupFile()
        val encoded = BackupJson.encode(file)
        val withoutNotes = encoded.replace(",\"exerciseNotes\":[]", "")

        // A v8 file predates the notes section and restores as "no note recorded".
        assertEquals(
            file.copy(formatVersion = 8),
            BackupJson.decode(
                withoutNotes.replace(
                    "\"formatVersion\":$BACKUP_FORMAT_VERSION",
                    "\"formatVersion\":8"
                )
            )
        )
        // The current format must carry it.
        assertFailure(BackupFailure.MALFORMED, withoutNotes)
    }

    @Test
    fun formatTenRequiresSessionNotesAndFormatNineDefaultsThem() {
        val file = emptyBackupFile()
        val encoded = BackupJson.encode(file)
        val withoutSessionNotes = encoded.replace(",\"sessionNotes\":[]", "")
        assertEquals(
            file.copy(formatVersion = 9),
            BackupJson.decode(
                withoutSessionNotes.replace(
                    "\"formatVersion\":$BACKUP_FORMAT_VERSION",
                    "\"formatVersion\":9"
                )
            )
        )
        assertFailure(BackupFailure.MALFORMED, withoutSessionNotes)
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
            val versioned = base.replace(
                "\"formatVersion\":$BACKUP_FORMAT_VERSION",
                "\"formatVersion\":$version"
            )
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
