package com.hydrafit.app.core.domain.backup

import com.hydrafit.app.core.domain.engine.SetTarget
import com.hydrafit.app.core.domain.engine.SlotProgressionPolicy
import com.hydrafit.app.core.domain.engine.SlotProgressionPolicyJson
import com.hydrafit.app.core.domain.engine.SlotSetTargets
import com.hydrafit.app.core.domain.engine.SlotSetTargetsJson
import com.hydrafit.app.core.domain.equipment.LoadingInventory
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.NOTE_MAX_LENGTH
import com.hydrafit.app.core.domain.workout.WorkoutPrescriptionSnapshot
import com.hydrafit.app.core.domain.workout.WorkoutPrescriptionSnapshotJson
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BackupValidatorTest {

    private val receiverProfile = "back-squat|BARBELL|SQUAT|false|EXTERNAL|"

    private val catalog = object : BackupCatalog {
        override fun seedExerciseIds(): Set<String> = setOf("back-squat")

        override fun builtInEquipmentIds(): Set<String> = setOf("BARBELL")

        override fun dedupeSeedKeys(): Set<String> = setOf("back squat")

        override fun seedProfiles(): Map<String, String> = mapOf("back-squat" to receiverProfile)
    }

    private val validator = BackupValidator(catalog)

    @Test
    fun rejectsUnsupportedSetupAndMalformedOrLegacyWeekdayPreferences() {
        listOf(
            BackupSetupRecord(version = 2),
            BackupSetupRecord(disposition = "UNKNOWN"),
            BackupSetupRecord(step = "UNKNOWN")
        ).forEach { setup ->
            assertFailure(BackupFailure.INVALID_VALUE) { validFile().copy(setup = setup) }
        }
        listOf(
            emptyList(),
            listOf("MONDAY"),
            listOf("MONDAY", "MONDAY"),
            listOf("MONDAY", "UNKNOWN"),
            com.hydrafit.app.core.domain.time.DayOfWeek.entries.map { it.name }
        ).forEach { weekdays ->
            assertFailure(BackupFailure.INVALID_VALUE) {
                validFile().copy(preferredWeekdays = weekdays)
            }
        }
        assertFailure(BackupFailure.INVALID_VALUE) {
            validFile().copy(formatVersion = 10, setup = BackupSetupRecord(disposition = "PENDING"))
        }
        assertFailure(BackupFailure.INVALID_VALUE) {
            validFile().copy(formatVersion = 10, preferredWeekdays = listOf("MONDAY", "FRIDAY"))
        }
    }

    @Test
    fun acceptsAValidMinimalFile() {
        validator.validate(validFile())
    }

    @Test
    fun acceptsVersionOneBackups() {
        validator.validate(validFile().copy(formatVersion = 1))
    }

    @Test
    fun rejectsAnUnsupportedVersion() {
        assertFailure(BackupFailure.UNSUPPORTED_VERSION) {
            validFile().copy(formatVersion = BACKUP_FORMAT_VERSION + 1)
        }
    }

    @Test
    fun rejectsAPrescriptionSnapshotInAPre21File() {
        assertFailure(BackupFailure.INVALID_VALUE) {
            profiledFile(PRESCRIPTION_SNAPSHOT_FORMAT_VERSION - 1).copy(
                workoutSets = listOf(set(prescriptionSnapshot = snapshot()))
            )
        }
    }

    @Test
    fun rejectsAPrescriptionSnapshotThatContradictsItsSet() {
        assertFailure(BackupFailure.INVALID_REFERENCE) {
            profiledFile(BACKUP_FORMAT_VERSION).copy(
                workoutSets = listOf(
                    set(prescriptionSnapshot = snapshot(exerciseId = "front-squat"))
                )
            )
        }
        assertFailure(BackupFailure.INVALID_VALUE) {
            profiledFile(BACKUP_FORMAT_VERSION).copy(
                workoutSets = listOf(
                    set(prescriptionSnapshot = snapshot(loadKind = "BODYWEIGHT", weightKg = null))
                )
            )
        }
        assertFailure(BackupFailure.INVALID_VALUE) {
            profiledFile(BACKUP_FORMAT_VERSION).copy(
                workoutSets = listOf(
                    set(
                        reps = null,
                        durationSeconds = 30,
                        weightKg = null,
                        prescriptionSnapshot = snapshot()
                    )
                )
            )
        }
    }

    @Test
    fun acceptsAWellFormedPrescriptionSnapshot() {
        validator.validate(
            profiledFile(BACKUP_FORMAT_VERSION).copy(
                workoutSets = listOf(set(prescriptionSnapshot = snapshot()))
            )
        )
    }

    @Test
    fun aPolicyInsideARecordedTargetIsRejectedInAPreFormat22File() {
        assertFailure(BackupFailure.INVALID_VALUE) {
            profiledFile(PROGRESSION_POLICY_FORMAT_VERSION - 1).copy(
                workoutSets = listOf(
                    set(prescriptionSnapshot = snapshot(policy = policy()))
                )
            )
        }
    }

    @Test
    fun aPolicyInsideARecordedTargetIsAcceptedAtItsOwnFormat() {
        validator.validate(
            profiledFile(PROGRESSION_POLICY_FORMAT_VERSION).copy(
                workoutSets = listOf(
                    set(prescriptionSnapshot = snapshot(policy = policy()))
                )
            )
        )
    }

    @Test
    fun rejectsAnUnknownExerciseReference() {
        assertFailure(BackupFailure.UNKNOWN_CATALOG_ID) {
            validFile().copy(
                workoutSets = listOf(set(exerciseId = "not-a-known-exercise"))
            )
        }
    }

    @Test
    fun acceptsGlobalAndExerciseRestPreferences() {
        validator.validate(
            validFile().copy(
                restPreferences = listOf(
                    BackupRestPreferenceRecord(null, 180L),
                    BackupRestPreferenceRecord("back-squat", 240L)
                )
            )
        )
    }

    @Test
    fun rejectsInvalidRestPreferenceValuesAndReferences() {
        assertFailure(BackupFailure.INVALID_VALUE) {
            validFile().copy(restPreferences = listOf(BackupRestPreferenceRecord(null, 0L)))
        }
        assertFailure(BackupFailure.UNKNOWN_CATALOG_ID) {
            validFile().copy(
                restPreferences = listOf(
                    BackupRestPreferenceRecord(null, 120L),
                    BackupRestPreferenceRecord("missing-exercise", 120L)
                )
            )
        }
    }

    @Test
    fun currentFormatRequiresExactlyOneGlobalRestPreference() {
        assertFailure(BackupFailure.INVALID_VALUE) {
            validFile().copy(restPreferences = emptyList())
        }
        assertFailure(BackupFailure.DUPLICATE_ID) {
            validFile().copy(
                restPreferences = listOf(
                    BackupRestPreferenceRecord(null, 120L),
                    BackupRestPreferenceRecord(null, 180L)
                )
            )
        }
    }

    @Test
    fun rejectsACustomIdThatOccupiesASeedIdentity() {
        assertFailure(BackupFailure.UNKNOWN_CATALOG_ID) {
            validFile().copy(
                customExercises = listOf(custom(id = "back-squat"))
            )
        }
    }

    @Test
    fun rejectsUnknownEquipment() {
        assertFailure(BackupFailure.UNKNOWN_CATALOG_ID) {
            validFile().copy(
                customExercises = listOf(custom(requiredEquipment = "NOT_REAL"))
            )
        }
    }

    @Test
    fun rejectsDuplicateIds() {
        assertFailure(BackupFailure.DUPLICATE_ID) {
            validFile().copy(workoutSets = listOf(set(id = 1), set(id = 1)))
        }
    }

    @Test
    fun rejectsDanglingReferences() {
        assertFailure(BackupFailure.INVALID_REFERENCE) {
            validFile().copy(
                planEntries = listOf(
                    BackupPlanEntryRecord(
                        id = 1,
                        dayId = 999,
                        position = 0,
                        exerciseId = "back-squat",
                        sets = 3,
                        reps = 5,
                        exerciseName = "Back Squat",
                        movementPattern = "SQUAT",
                        suggestedWeightKg = 100.0,
                        loadCapability = "EXTERNAL",
                        loadKind = "EXTERNAL"
                    )
                )
            )
        }
    }

    @Test
    fun acceptsATimedPlanEntryAtTheCurrentFormat() {
        validator.validate(
            planEntryFile(BACKUP_FORMAT_VERSION, reps = null, durationSeconds = 45)
        )
    }

    @Test
    fun acceptsALegacyPlanEntryWithoutADuration() {
        validator.validate(
            planEntryFile(
                formatVersion = PLAN_ENTRY_DURATION_FORMAT_VERSION - 1,
                reps = 5,
                durationSeconds = null
            )
        )
    }

    @Test
    fun rejectsAPlanEntryWithBothOrNeitherMeasure() {
        assertFailure(BackupFailure.INVALID_VALUE) {
            planEntryFile(BACKUP_FORMAT_VERSION, reps = 5, durationSeconds = 45)
        }
        assertFailure(BackupFailure.INVALID_VALUE) {
            planEntryFile(BACKUP_FORMAT_VERSION, reps = null, durationSeconds = null)
        }
        assertFailure(BackupFailure.INVALID_VALUE) {
            planEntryFile(BACKUP_FORMAT_VERSION, reps = 0, durationSeconds = null)
        }
        assertFailure(BackupFailure.INVALID_VALUE) {
            planEntryFile(BACKUP_FORMAT_VERSION, reps = null, durationSeconds = 0)
        }
    }

    @Test
    fun rejectsAPlanEntryDurationInALegacyFormat() {
        assertFailure(BackupFailure.INVALID_VALUE) {
            planEntryFile(
                formatVersion = PLAN_ENTRY_DURATION_FORMAT_VERSION - 1,
                reps = null,
                durationSeconds = 45
            )
        }
    }

    @Test
    fun acceptsCountedAndTimedWarmupPreparationOverridesAtTheCurrentFormat() {
        validator.validate(
            overrideFile(
                BACKUP_FORMAT_VERSION,
                prepOverride("LOWER_BODY_PRACTICE", reps = 15),
                prepOverride("EASY_GENERAL_MOVEMENT", durationSeconds = 300)
            )
        )
    }

    @Test
    fun rejectsWarmupPreparationOverridesInLegacyFormats() {
        assertFailure(BackupFailure.INVALID_VALUE) {
            overrideFile(
                WARMUP_PREPARATION_OVERRIDE_FORMAT_VERSION - 1,
                prepOverride("LOWER_BODY_PRACTICE", reps = 15)
            )
        }
    }

    @Test
    fun anEmptyWarmupPreparationOverrideSectionStaysValidForLegacyFormats() {
        validator.validate(overrideFile(WARMUP_PREPARATION_OVERRIDE_FORMAT_VERSION - 1))
    }

    @Test
    fun rejectsAnUnknownDuplicateOrInvalidWarmupPreparationOverride() {
        assertFailure(BackupFailure.INVALID_VALUE) {
            overrideFile(BACKUP_FORMAT_VERSION, prepOverride("NOT_AN_ACTION", reps = 10))
        }
        assertFailure(BackupFailure.DUPLICATE_ID) {
            overrideFile(
                BACKUP_FORMAT_VERSION,
                prepOverride("LOWER_BODY_PRACTICE", reps = 10),
                prepOverride("LOWER_BODY_PRACTICE", reps = 12)
            )
        }
        assertFailure(BackupFailure.INVALID_VALUE) {
            overrideFile(
                BACKUP_FORMAT_VERSION,
                prepOverride("LOWER_BODY_PRACTICE", reps = 5, durationSeconds = 60)
            )
        }
        assertFailure(BackupFailure.INVALID_VALUE) {
            overrideFile(BACKUP_FORMAT_VERSION, prepOverride("LOWER_BODY_PRACTICE"))
        }
        assertFailure(BackupFailure.INVALID_VALUE) {
            overrideFile(BACKUP_FORMAT_VERSION, prepOverride("LOWER_BODY_PRACTICE", reps = 0))
        }
    }

    @Test
    fun rejectsAnInvalidEnum() {
        assertFailure(BackupFailure.INVALID_VALUE) {
            validFile().copy(workoutSets = listOf(set(loadKind = "NOT_A_KIND")))
        }
    }

    @Test
    fun rejectsAnInvalidTimingProvenance() {
        assertFailure(BackupFailure.INVALID_VALUE) {
            validFile().copy(workoutSets = listOf(set(timingProvenance = "NOT_A_SOURCE")))
        }
    }

    @Test
    fun rejectsAnIncompleteOrReversedElapsedSetInterval() {
        assertFailure(BackupFailure.INVALID_VALUE) {
            validFile().copy(workoutSets = listOf(set(startedAtElapsedMillis = 10L)))
        }
        assertFailure(BackupFailure.INVALID_VALUE) {
            validFile().copy(
                workoutSets = listOf(
                    set(startedAtElapsedMillis = 20L, completedAtElapsedMillis = 10L)
                )
            )
        }
    }

    @Test
    fun rejectsANonFiniteNumber() {
        assertFailure(BackupFailure.INVALID_VALUE) {
            validFile().copy(workoutSets = listOf(set(weightKg = Double.NaN)))
        }
    }

    @Test
    fun rejectsDuplicateSessionIds() {
        assertFailure(BackupFailure.DUPLICATE_ID) {
            validFile().copy(workoutSessions = listOf(session("s1"), session("s1")))
        }
    }

    @Test
    fun acceptsAMatchingCatalogProfile() {
        validator.validate(
            validFile().copy(
                catalog = BackupCatalogManifest(
                    listOf("back-squat"),
                    seedProfiles = listOf(BackupSeedProfile("back-squat", receiverProfile))
                )
            )
        )
    }

    @Test
    fun rejectsAChangedCatalogProfile() {
        assertFailure(BackupFailure.CATALOG_MISMATCH) {
            validFile().copy(
                catalog = BackupCatalogManifest(
                    listOf("back-squat"),
                    seedProfiles = listOf(BackupSeedProfile("back-squat", "changed"))
                )
            )
        }
    }

    @Test
    fun acceptsOnlyAnExactHistoricalProfileForItsOwnKnownIdentity() {
        val receiver = object : BackupCatalog by catalog {
            override fun compatiblePreviousSeedProfiles(): Map<String, Set<String>> =
                mapOf("back-squat" to setOf("approved-old-profile"))
        }
        val compatibleValidator = BackupValidator(receiver)
        val file = validFile().copy(
            catalog = BackupCatalogManifest(
                listOf("back-squat"),
                listOf(BackupSeedProfile("back-squat", "approved-old-profile"))
            )
        )
        compatibleValidator.validate(file)
        listOf(
            BackupSeedProfile("back-squat", "approved-old-profile "),
            BackupSeedProfile("back-squat", "unknown-old-profile"),
            BackupSeedProfile("missing", "approved-old-profile")
        ).forEach { profile ->
            val failure = assertFailsWith<BackupException> {
                compatibleValidator.validate(
                    file.copy(catalog = file.catalog.copy(seedProfiles = listOf(profile)))
                )
            }
            assertEquals(BackupFailure.CATALOG_MISMATCH, failure.failure)
        }
    }

    @Test
    fun rejectsASeedUnknownToThisInstall() {
        assertFailure(BackupFailure.CATALOG_MISMATCH) {
            validFile().copy(
                catalog = BackupCatalogManifest(
                    listOf("back-squat"),
                    seedProfiles = listOf(BackupSeedProfile("not-a-seed", "x"))
                )
            )
        }
    }

    @Test
    fun rejectsASetThatReferencesAnUnknownSession() {
        assertFailure(BackupFailure.INVALID_REFERENCE) {
            validFile().copy(workoutSets = listOf(set(sessionId = "missing")))
        }
    }

    @Test
    fun rejectsDuplicateNaturalKeys() {
        assertFailure(BackupFailure.DUPLICATE_ID) {
            validFile().copy(
                preferences = listOf(
                    BackupPreferenceRecord("back-squat", "PREFER"),
                    BackupPreferenceRecord("back-squat", "PREFER_LESS")
                )
            )
        }
    }

    @Test
    fun validatesExerciseNoteReferencesLengthAndDuplicates() {
        validator.validate(
            validFile().copy(
                exerciseNotes = listOf(
                    BackupExerciseNoteRecord("back-squat", "x".repeat(NOTE_MAX_LENGTH))
                )
            )
        )
        // An explicit clear (empty text) is valid and distinct from absence.
        validator.validate(
            validFile().copy(exerciseNotes = listOf(BackupExerciseNoteRecord("back-squat", "")))
        )
        assertFailure(BackupFailure.UNKNOWN_CATALOG_ID) {
            validFile().copy(exerciseNotes = listOf(BackupExerciseNoteRecord("missing", "x")))
        }
        assertFailure(BackupFailure.INVALID_VALUE) {
            validFile().copy(
                exerciseNotes = listOf(
                    BackupExerciseNoteRecord("back-squat", "x".repeat(NOTE_MAX_LENGTH + 1))
                )
            )
        }
        assertFailure(BackupFailure.DUPLICATE_ID) {
            validFile().copy(
                exerciseNotes = listOf(
                    BackupExerciseNoteRecord("back-squat", "a"),
                    BackupExerciseNoteRecord("back-squat", "b")
                )
            )
        }
    }

    @Test
    fun validatesSessionNoteReferencesLengthAndDuplicates() {
        validator.validate(
            validFile().copy(
                sessionNotes = listOf(BackupSessionNoteRecord("s1", "x".repeat(NOTE_MAX_LENGTH)))
            )
        )
        validator.validate(
            validFile().copy(sessionNotes = listOf(BackupSessionNoteRecord("s1", "")))
        )
        assertFailure(BackupFailure.INVALID_REFERENCE) {
            validFile().copy(sessionNotes = listOf(BackupSessionNoteRecord("missing", "note")))
        }
        assertFailure(BackupFailure.INVALID_VALUE) {
            validFile().copy(
                sessionNotes = listOf(
                    BackupSessionNoteRecord("s1", "x".repeat(NOTE_MAX_LENGTH + 1))
                )
            )
        }
        assertFailure(BackupFailure.DUPLICATE_ID) {
            validFile().copy(
                sessionNotes = listOf(
                    BackupSessionNoteRecord("s1", "first"),
                    BackupSessionNoteRecord("s1", "second")
                )
            )
        }
    }

    @Test
    fun rejectsExerciseNotesInLegacyFormats() {
        assertFailure(BackupFailure.INVALID_VALUE) {
            validFile().copy(
                formatVersion = 8,
                exerciseNotes = listOf(BackupExerciseNoteRecord("back-squat", "note"))
            )
        }
    }

    @Test
    fun acceptsExerciseNotesInTheirIntroductionFormat() {
        validator.validate(
            validFile().copy(
                formatVersion = EXERCISE_NOTES_FORMAT_VERSION,
                exerciseNotes = listOf(BackupExerciseNoteRecord("back-squat", "note"))
            )
        )
    }

    @Test
    fun anEmptyExerciseNoteSectionStaysValidForLegacyFormats() {
        // Versions 1-8 simply have no note section; an exported empty list must not be rejected.
        validator.validate(
            validFile().copy(formatVersion = 8, exerciseNotes = emptyList())
        )
    }

    @Test
    fun rejectsSessionNotesInLegacyFormats() {
        assertFailure(BackupFailure.INVALID_VALUE) {
            validFile().copy(
                formatVersion = 9,
                sessionNotes = listOf(BackupSessionNoteRecord("s1", "note"))
            )
        }
    }

    @Test
    fun acceptsSessionNotesInTheirIntroductionFormat() {
        validator.validate(
            validFile().copy(
                formatVersion = SESSION_NOTES_FORMAT_VERSION,
                sessionNotes = listOf(BackupSessionNoteRecord("s1", "note"))
            )
        )
    }

    @Test
    fun validatesReminderPreferencesAndRejectsThemBeforeTheirFormat() {
        val armed = BackupWorkoutReminderRecord(
            remindersEnabled = true,
            reminderMinuteOfDay = 390,
            quietHoursEnabled = true,
            quietStartMinuteOfDay = 1320,
            quietEndMinuteOfDay = 360
        )
        validator.validate(validFile().copy(workoutReminders = armed))
        // The same record is still valid when no time has been chosen yet.
        validator.validate(
            validFile().copy(workoutReminders = armed.copy(reminderMinuteOfDay = null))
        )
        // A file from before format 12 must not carry a section this reader would treat as current.
        assertFailure(BackupFailure.INVALID_VALUE) {
            validFile().copy(
                formatVersion = REMINDER_FORMAT_VERSION - 1,
                workoutReminders = armed
            )
        }
        listOf(
            armed.copy(reminderMinuteOfDay = -1),
            armed.copy(reminderMinuteOfDay = 1440),
            armed.copy(quietStartMinuteOfDay = -1),
            armed.copy(quietEndMinuteOfDay = 1440)
        ).forEach { reminders ->
            assertFailure(BackupFailure.INVALID_VALUE) {
                validFile().copy(workoutReminders = reminders)
            }
        }
    }

    @Test
    fun rejectsNonDefaultRestPresentationOptInsBeforeFormatTwelve() {
        val nonDefaults = listOf(
            BackupTrainingGuidanceRecord(
                restAlertsEnabled = false,
                shortRestCoachingEnabled = false,
                restCountdownEnabled = true
            ),
            BackupTrainingGuidanceRecord(
                restAlertsEnabled = false,
                shortRestCoachingEnabled = false,
                restCompletionSignalEnabled = true
            ),
            BackupTrainingGuidanceRecord(
                restAlertsEnabled = false,
                shortRestCoachingEnabled = false,
                restCountdownEnabled = true,
                restCompletionSignalEnabled = true
            )
        )
        // v11 is the last pre-12 version; v6 is the oldest version that carries trainingGuidance.
        listOf(REMINDER_FORMAT_VERSION - 1, 6).forEach { version ->
            nonDefaults.forEach { guidance ->
                assertFailure(BackupFailure.INVALID_VALUE) {
                    validFile().copy(formatVersion = version, trainingGuidance = guidance)
                }
            }
        }
    }

    @Test
    fun acceptsDefaultRestPresentationOptInsInLegacyFormats() {
        listOf(REMINDER_FORMAT_VERSION - 1, 6).forEach { version ->
            validator.validate(
                validFile().copy(
                    formatVersion = version,
                    trainingGuidance = BackupTrainingGuidanceRecord(false, false)
                )
            )
        }
    }

    @Test
    fun acceptsRestPresentationOptInsInFormatTwelve() {
        validator.validate(
            validFile().copy(
                trainingGuidance = BackupTrainingGuidanceRecord(
                    restAlertsEnabled = true,
                    shortRestCoachingEnabled = true,
                    restCountdownEnabled = true,
                    restCompletionSignalEnabled = true
                )
            )
        )
    }

    @Test
    fun rejectsNonDefaultRestOptInsDecodedFromLegacyJson() {
        // The booleans are known fields, so ignoreUnknownKeys does not reject them; the validator must.
        val file = validFile().copy(
            formatVersion = REMINDER_FORMAT_VERSION - 1,
            trainingGuidance = BackupTrainingGuidanceRecord(
                restAlertsEnabled = false,
                shortRestCoachingEnabled = false,
                restCountdownEnabled = true
            )
        )

        val failure = assertFailsWith<BackupException> {
            validator.validate(BackupJson.decode(BackupJson.encode(file)))
        }

        assertEquals(BackupFailure.INVALID_VALUE, failure.failure)
    }

    @Test
    fun rejectsDuplicateCompositeExplanationKeys() {
        assertFailure(BackupFailure.DUPLICATE_ID) {
            validFile().copy(
                volumeExplanations = listOf(
                    explanation(planId = 1, muscle = "BICEPS"),
                    explanation(planId = 1, muscle = "BICEPS")
                )
            )
        }
    }

    @Test
    fun rejectsTargetReasonWithoutItsPlanContextOrSlot() {
        validator.validate(targetReasonFile())
        assertFailure(BackupFailure.INVALID_REFERENCE) {
            targetReasonFile().copy(planTargetExplanationStates = emptyList())
        }
        assertFailure(BackupFailure.INVALID_REFERENCE) {
            targetReasonFile().copy(
                planTargetExplanations = listOf(targetExplanation(position = 1))
            )
        }
    }

    @Test
    fun rejectsInvalidTargetReasonEnumsAndNonFiniteValues() {
        assertFailure(BackupFailure.INVALID_VALUE) {
            targetReasonFile().copy(
                planTargetExplanations = listOf(
                    targetExplanation().copy(baselineSource = "NOT_A_SOURCE")
                )
            )
        }
        assertFailure(BackupFailure.INVALID_VALUE) {
            targetReasonFile().copy(
                planTargetExplanations = listOf(targetExplanation().copy(baselineKg = Double.NaN))
            )
        }
        assertFailure(BackupFailure.INVALID_VALUE) {
            targetReasonFile().copy(
                planTargetExplanationStates = listOf(
                    targetState().copy(attribution = "NOT_AN_ENGINE")
                )
            )
        }
        assertFailure(BackupFailure.INVALID_VALUE) {
            targetReasonFile().copy(
                planTargetExplanationStates = listOf(
                    targetState().copy(deloadIntensityScale = Double.POSITIVE_INFINITY)
                )
            )
        }
    }

    @Test
    fun rejectsUnsupportedTargetReasonVersionsAndDuplicateSlots() {
        assertFailure(BackupFailure.INVALID_VALUE) {
            targetReasonFile().copy(
                planTargetExplanations = listOf(targetExplanation().copy(explanationVersion = 2))
            )
        }
        assertFailure(BackupFailure.INVALID_VALUE) {
            targetReasonFile().copy(
                planTargetExplanationStates = listOf(targetState().copy(explanationVersion = 2))
            )
        }
        assertFailure(BackupFailure.DUPLICATE_ID) {
            targetReasonFile().copy(
                planTargetExplanations = listOf(targetExplanation(), targetExplanation())
            )
        }
    }

    @Test
    fun validatesFrozenEntryReasonReferencesKindEnumsNumbersAndVersion() {
        val file = frozenEntryReasonFile()
        validator.validate(file)
        assertFailure(BackupFailure.INVALID_REFERENCE) {
            file.copy(
                entryTargetExplanations = listOf(entryExplanation("ACTIVATION", entryId = 999))
            )
        }
        assertFailure(BackupFailure.INVALID_REFERENCE) {
            file.copy(
                entryTargetExplanations = listOf(entryExplanation("OCCURRENCE", entryId = 999))
            )
        }
        assertFailure(BackupFailure.INVALID_VALUE) {
            file.copy(entryTargetExplanations = listOf(entryExplanation("UNKNOWN", 100)))
        }
        assertFailure(BackupFailure.INVALID_VALUE) {
            file.copy(
                entryTargetExplanations = listOf(
                    entryExplanation("ACTIVATION", 100).copy(setFatigueEffect = "NOT_AN_EFFECT")
                )
            )
        }
        assertFailure(BackupFailure.INVALID_VALUE) {
            file.copy(
                entryTargetExplanations = listOf(
                    entryExplanation("OCCURRENCE", 200).copy(aiProposedKg = Double.NaN)
                )
            )
        }
        assertFailure(BackupFailure.INVALID_VALUE) {
            file.copy(
                entryTargetExplanations = listOf(
                    entryExplanation("OCCURRENCE", 200).copy(explanationVersion = 2)
                )
            )
        }
    }

    @Test
    fun rejectsTargetReasonWhoseSlotCoordinatesDoNotMatchItsPlan() {
        // Both plans exist and both carry context, so only the slot coordinate can reject the reason.
        val twoPlans = targetReasonFile().copy(
            plans = listOf(
                BackupPlanRecord(7L, "DETERMINISTIC", 1L, 1, 1),
                BackupPlanRecord(8L, "DETERMINISTIC", 2L, 2, 1)
            ),
            planTargetExplanationStates = listOf(targetState(), targetState().copy(planId = 8L))
        )
        validator.validate(twoPlans)

        assertFailure(BackupFailure.INVALID_REFERENCE) {
            twoPlans.copy(planTargetExplanations = listOf(targetExplanation().copy(planId = 8L)))
        }
        assertFailure(BackupFailure.INVALID_REFERENCE) {
            twoPlans.copy(
                planTargetExplanations = listOf(targetExplanation().copy(dayIndex = 5))
            )
        }
    }

    @Test
    fun roundTripsRecordedTargetAndEntryReasonsThroughJson() {
        val recordedPlanReason = targetExplanation().copy(
            baselineSource = "RECENT_PERFORMANCE",
            baselineRecentEstimateKg = 100.0,
            baselineQualifyingSetCount = 3,
            baselineKg = 100.0,
            progressionOutcome = "APPLIED",
            progressionIncrements = 1,
            progressionDeltaKg = 2.5,
            progressionRoundToKg = 2.5,
            progressionSuccessStreakSize = 3,
            progressionFailureStreakSize = 3,
            conversionReps = 8,
            conversionIntensityFraction = 0.765,
            conversionDeloadIntensityScale = 0.8,
            conversionRoundToKg = 2.5,
            conversionUncappedWorkingKg = 102.5,
            conversionEquipmentCeilingKg = 100.0,
            conversionEquipmentCeilingBinds = true,
            setBaseSets = 3,
            setDeloadApplied = false,
            setDeloadVolumeScale = 0.7,
            setFatigueEffect = "NONE",
            setFinalSets = 3
        )
        val file = frozenEntryReasonFile().copy(
            planTargetExplanations = listOf(recordedPlanReason),
            planTargetExplanationStates = listOf(targetState().copy(isDeload = true)),
            entryTargetExplanations = listOf(
                entryExplanation("ACTIVATION", 100L).copy(
                    setBaseSets = 4,
                    setDeloadApplied = true,
                    setDeloadVolumeScale = 0.7,
                    setFatigueEffect = "REDUCED",
                    setFinalSets = 3
                ),
                entryExplanation("OCCURRENCE", 200L).copy(
                    aiProposedKg = 62.5,
                    aiProposedAccepted = true,
                    aiDeloadScaled = false,
                    aiDeloadIntensityScale = 1.0,
                    aiBoundedByProgression = false,
                    aiCappedByEquipment = false
                )
            )
        )

        val decoded = BackupJson.decode(BackupJson.encode(file))

        validator.validate(decoded)
        assertEquals(file.planTargetExplanations, decoded.planTargetExplanations)
        assertEquals(file.planTargetExplanationStates, decoded.planTargetExplanationStates)
        assertEquals(file.entryTargetExplanations, decoded.entryTargetExplanations)
    }

    @Test
    fun rejectsDuplicateFrozenEntryReasonKeys() {
        assertFailure(BackupFailure.DUPLICATE_ID) {
            frozenEntryReasonFile().copy(
                entryTargetExplanations = listOf(
                    entryExplanation("ACTIVATION", 100),
                    entryExplanation("ACTIVATION", 100)
                )
            )
        }
    }

    @Test
    fun rejectsAnUnknownMuscleToken() {
        assertFailure(BackupFailure.INVALID_VALUE) {
            validFile().copy(customExercises = listOf(custom(involvements = "NOT_A_MUSCLE:1.0")))
        }
    }

    @Test
    fun rejectsANonFiniteInvolvementWeight() {
        assertFailure(BackupFailure.INVALID_VALUE) {
            validFile().copy(customExercises = listOf(custom(involvements = "ABS:NaN")))
        }
    }

    @Test
    fun acceptsLegacyBroadMuscleNamesInSnapshots() {
        validator.validate(
            validFile().copy(workoutSets = listOf(set(involvements = "CHEST:0.7,CORE:0.3")))
        )
    }

    @Test
    fun rejectsACustomThatStartupDedupeWouldMerge() {
        assertFailure(BackupFailure.STARTUP_UNSTABLE) {
            validFile().copy(
                customExercises = listOf(custom(id = "user-back-squat", name = "Back Squat"))
            )
        }
    }

    @Test
    fun acceptsACustomWhoseNameOnlyMatchesAProtectedP7Seed() {
        validator.validate(
            validFile().copy(
                customExercises = listOf(
                    custom(id = "user-bicycle-crunch", name = "Bicycle Crunch")
                )
            )
        )
    }

    @Test
    fun rejectsALegacySetWithNoSessionId() {
        assertFailure(BackupFailure.STARTUP_UNSTABLE) {
            validFile().copy(workoutSets = listOf(set(sessionId = null)))
        }
    }

    @Test
    fun rejectsSelectedEquipmentThatIsNotKnown() {
        assertFailure(BackupFailure.UNKNOWN_CATALOG_ID) {
            validFile().copy(selectedEquipment = listOf("NOT_REAL"))
        }
    }

    @Test
    fun rejectsACustomEquipmentIdThatOccupiesABuiltIn() {
        assertFailure(BackupFailure.UNKNOWN_CATALOG_ID) {
            validFile().copy(
                equipment = listOf(
                    BackupEquipmentRecord(
                        "BARBELL",
                        "Barbell",
                        isBuiltIn = false,
                        maxWeightKg = null
                    )
                )
            )
        }
    }

    @Test
    fun rejectsAnOccurrenceWorkoutFromAnotherActivation() {
        assertFailure(BackupFailure.INVALID_REFERENCE) {
            validFile().copy(
                activations = listOf(activation(1), activation(2)),
                activationWorkouts = listOf(activationWorkout(id = 10, activationId = 1)),
                occurrences = listOf(occurrence(id = 100, activationId = 2, workoutId = 10))
            )
        }
    }

    @Test
    fun rejectsASetOccurrenceEntryFromAnotherOccurrence() {
        assertFailure(BackupFailure.INVALID_REFERENCE) {
            validFile().copy(
                activations = listOf(activation(1)),
                activationWorkouts = listOf(activationWorkout(id = 1, activationId = 1)),
                occurrences = listOf(
                    occurrence(id = 1, activationId = 1, workoutId = 1),
                    occurrence(id = 2, activationId = 1, workoutId = 1)
                ),
                occurrenceEntries = listOf(occurrenceEntry(id = 5, occurrenceId = 1)),
                workoutSets = listOf(set(occurrenceId = 2, occurrenceEntryId = 5))
            )
        }
    }

    @Test
    fun acceptsBodyWeightSectionsInFormatThirteenAndRejectsBadValues() {
        val enabled = validFile().copy(
            bodyWeightMeasurements = listOf(
                BackupBodyWeightRecord(20_000L, 82.5),
                BackupBodyWeightRecord(20_001L, 82.25)
            ),
            bodyWeightGoalKg = 78.0
        )
        validator.validate(enabled)

        listOf(
            enabled.copy(bodyWeightMeasurements = listOf(BackupBodyWeightRecord(1L, 0.0))),
            enabled.copy(bodyWeightMeasurements = listOf(BackupBodyWeightRecord(1L, -1.0))),
            enabled.copy(bodyWeightMeasurements = listOf(BackupBodyWeightRecord(1L, Double.NaN))),
            enabled.copy(
                bodyWeightMeasurements = listOf(
                    BackupBodyWeightRecord(1L, 80.0),
                    BackupBodyWeightRecord(1L, 81.0)
                )
            ),
            enabled.copy(bodyWeightGoalKg = 0.0),
            enabled.copy(bodyWeightGoalKg = Double.NaN)
        ).forEach { file ->
            assertFailure(BackupFailure.INVALID_VALUE) { file }
        }
    }

    @Test
    fun rejectsBodyWeightSectionsInLegacyFormats() {
        listOf(
            validFile().copy(
                formatVersion = 12,
                bodyWeightMeasurements = listOf(BackupBodyWeightRecord(1L, 80.0))
            ),
            validFile().copy(formatVersion = 12, bodyWeightGoalKg = 80.0)
        ).forEach { file ->
            assertFailure(BackupFailure.INVALID_VALUE) { file }
        }
    }

    @Test
    fun acceptsATimedSetInFormatSeventeenAndRejectsTheBothOrNeitherShapes() {
        validator.validate(
            profiledFile(BACKUP_FORMAT_VERSION)
                .copy(workoutSets = listOf(set(reps = null, durationSeconds = 90)))
        )

        // Exactly one positive measurement: never reps and seconds together, never neither, and
        // never a non-positive value.
        listOf(
            set(reps = 5, durationSeconds = 90),
            set(reps = null, durationSeconds = null),
            set(reps = 0, durationSeconds = null),
            set(reps = null, durationSeconds = 0)
        ).forEach { invalid ->
            assertFailure(BackupFailure.INVALID_VALUE) {
                profiledFile(BACKUP_FORMAT_VERSION).copy(workoutSets = listOf(invalid))
            }
        }
    }

    @Test
    fun aLegacyRepsOnlySetStaysCountedAndRejectsADuration() {
        // The format before the metrics field predates it, so its counted rows restore verbatim.
        val legacy = profiledFile(WORKOUT_SET_METRICS_FORMAT_VERSION - 1)
        validator.validate(legacy.copy(workoutSets = listOf(set(reps = 5))))
        // A duration in a pre-17 file is rejected rather than read as extra reps.
        assertFailure(BackupFailure.INVALID_VALUE) {
            legacy.copy(workoutSets = listOf(set(reps = null, durationSeconds = 90)))
        }
    }

    @Test
    fun aTimedPrescriptionIsAcceptedAtFormatEighteenAndRefusedInEarlierFormats() {
        fun file(
            routine: BackupRoutineEntryRecord,
            activation: BackupActivationEntryRecord,
            occurrence: BackupOccurrenceEntryRecord
        ) = profiledFile(BACKUP_FORMAT_VERSION).copy(
            routines = listOf(
                BackupRoutineRecord(1L, "Push", 1, 1L, 1L, null, null)
            ),
            routineWorkouts = listOf(BackupRoutineWorkoutRecord(10L, 1L, 0, "Day", null)),
            routineEntries = listOf(routine),
            activations = listOf(activation(1L)),
            activationWorkouts = listOf(activationWorkout(10L, 1L)),
            activationEntries = listOf(activation),
            occurrences = listOf(occurrence(30L, 1L, 10L)),
            occurrenceEntries = listOf(occurrence)
        )

        val timed = file(
            routine = BackupRoutineEntryRecord(
                id = 100L,
                workoutId = 10L,
                position = 0,
                exerciseId = "back-squat",
                sets = 3,
                reps = null,
                durationSeconds = 90,
                weightKg = 100.0,
                loadKind = "EXTERNAL"
            ),
            activation = activationEntry(200L, 10L).copy(reps = null, durationSeconds = 90),
            occurrence = occurrenceEntry(300L, 30L).copy(reps = null, durationSeconds = 90)
        )

        assertEquals(BACKUP_FORMAT_VERSION, timed.formatVersion)
        validator.validate(timed)

        // The same payload written as the previous format has no field for a duration, so carrying
        // one is a corrupt file rather than a value to reinterpret.
        assertFailure(BackupFailure.INVALID_VALUE) { timed.copy(formatVersion = 17) }

        // A reps-only payload stays valid at either format: the field's presence is what is gated.
        val legacyCounted = timed.copy(
            formatVersion = 17,
            routineEntries = listOf(
                timed.routineEntries.single().copy(reps = 5, durationSeconds = null)
            ),
            activationEntries = listOf(
                timed.activationEntries.single().copy(reps = 5, durationSeconds = null)
            ),
            occurrenceEntries = listOf(
                timed.occurrenceEntries.single().copy(reps = 5, durationSeconds = null)
            )
        )
        validator.validate(legacyCounted)

        // Every prescription section enforces the same exactly-one rule.
        fun counted(entry: BackupRoutineEntryRecord) = entry.copy(reps = 5)
        assertFailure(BackupFailure.INVALID_VALUE) {
            timed.copy(routineEntries = listOf(counted(timed.routineEntries.single())))
        }
        assertFailure(BackupFailure.INVALID_VALUE) {
            timed.copy(
                activationEntries = listOf(
                    timed.activationEntries.single().copy(reps = 5)
                )
            )
        }
        assertFailure(BackupFailure.INVALID_VALUE) {
            timed.copy(
                occurrenceEntries = listOf(
                    timed.occurrenceEntries.single().copy(reps = 5)
                )
            )
        }
        assertFailure(BackupFailure.INVALID_VALUE) {
            timed.copy(
                routineEntries = listOf(
                    timed.routineEntries.single().copy(reps = null, durationSeconds = null)
                )
            )
        }
        assertFailure(BackupFailure.INVALID_VALUE) {
            timed.copy(
                occurrenceEntries = listOf(
                    timed.occurrenceEntries.single().copy(durationSeconds = 0)
                )
            )
        }
    }

    @Test
    fun aPolicyInAPreFormat22FileIsRejectedRatherThanAdopted() {
        assertFailure(BackupFailure.INVALID_VALUE) {
            policyFile(PROGRESSION_POLICY_FORMAT_VERSION - 1, encodedPolicy())
        }
    }

    @Test
    fun aPolicyAtItsOwnFormatIsAccepted() {
        validator.validate(policyFile(PROGRESSION_POLICY_FORMAT_VERSION, encodedPolicy()))
    }

    @Test
    fun anUndecodablePolicyIsRejectedBeforeAnyWrite() {
        assertFailure(BackupFailure.INVALID_VALUE) {
            policyFile(
                PROGRESSION_POLICY_FORMAT_VERSION,
                """{"version":1,"minReps":8,"maxReps":5,"incrementKg":2.5}"""
            )
        }
    }

    @Test
    fun orderedTargetsInAPreFormat23FileAreRejectedRatherThanAdopted() {
        assertFailure(BackupFailure.INVALID_VALUE) {
            orderedFile(SET_TARGETS_FORMAT_VERSION - 1, encodedOrderedTargets())
        }
    }

    @Test
    fun orderedTargetsAtTheirOwnFormatAreAccepted() {
        validator.validate(orderedFile(SET_TARGETS_FORMAT_VERSION, encodedOrderedTargets()))
    }

    @Test
    fun anUndecodableOrderedPrescriptionIsRejectedBeforeAnyWrite() {
        assertFailure(BackupFailure.INVALID_VALUE) {
            orderedFile(SET_TARGETS_FORMAT_VERSION, """{"version":1,"targets":[]}""")
        }
    }

    @Test
    fun aSlotIndexInsideARecordedTargetIsRejectedInAPreFormat23File() {
        assertFailure(BackupFailure.INVALID_VALUE) {
            profiledFile(SET_TARGETS_FORMAT_VERSION - 1).copy(
                workoutSets = listOf(set(prescriptionSnapshot = snapshot(setIndex = 0)))
            )
        }
    }

    @Test
    fun aSlotIndexInsideARecordedTargetIsAcceptedAtItsOwnFormat() {
        validator.validate(
            profiledFile(SET_TARGETS_FORMAT_VERSION).copy(
                workoutSets = listOf(set(prescriptionSnapshot = snapshot(setIndex = 0)))
            )
        )
    }

    private fun encodedOrderedTargets() = SlotSetTargetsJson.encode(
        SlotSetTargets.of(
            listOf(
                SetTarget(reps = 10, loadKind = LoadKind.EXTERNAL, weightKg = 60.0),
                SetTarget(reps = 8, loadKind = LoadKind.EXTERNAL, weightKg = 70.0)
            )
        )
    )

    /** One routine slot carrying ordered targets, so the format gate is the only variable. */
    private fun orderedFile(formatVersion: Int, encoded: String?) =
        profiledFile(formatVersion).copy(
            routines = listOf(BackupRoutineRecord(1L, "Block", 1, 1, 1, null, null)),
            routineWorkouts = listOf(BackupRoutineWorkoutRecord(10L, 1L, 0, "Day", "FULL_BODY")),
            routineEntries = listOf(
                BackupRoutineEntryRecord(
                    id = 100L,
                    workoutId = 10L,
                    position = 0,
                    exerciseId = "back-squat",
                    sets = 2,
                    reps = 10,
                    durationSeconds = null,
                    weightKg = 60.0,
                    loadKind = "EXTERNAL",
                    progressionPolicy = null,
                    setTargets = encoded
                )
            )
        )

    private fun policy() = SlotProgressionPolicy(minReps = 5, maxReps = 8, incrementKg = 2.5)

    private fun encodedPolicy() = SlotProgressionPolicyJson.encode(policy())

    /** One routine slot carrying a progression policy, so the format gate is the only variable. */
    private fun policyFile(formatVersion: Int, encodedPolicy: String?) =
        profiledFile(formatVersion).copy(
            routines = listOf(BackupRoutineRecord(1L, "Block", 1, 1, 1, null, null)),
            routineWorkouts = listOf(BackupRoutineWorkoutRecord(10L, 1L, 0, "Day", "FULL_BODY")),
            routineEntries = listOf(
                BackupRoutineEntryRecord(
                    id = 100L,
                    workoutId = 10L,
                    position = 0,
                    exerciseId = "back-squat",
                    sets = 3,
                    reps = 5,
                    durationSeconds = null,
                    weightKg = 100.0,
                    loadKind = "EXTERNAL",
                    progressionPolicy = encodedPolicy
                )
            )
        )

    private fun assertFailure(expected: BackupFailure, mutate: () -> BackupFile) {
        val failure = assertFailsWith<BackupException> { validator.validate(mutate()) }
        assertEquals(expected, failure.failure)
    }

    private fun validFile() = emptyBackupFile().copy(
        formatVersion = 13,
        equipmentProfiles = emptyList(),
        activeEquipmentProfileId = null,
        appVersion = "t",
        exportedAtMillis = 1L,
        catalog = BackupCatalogManifest(listOf("back-squat"), seedProfiles = emptyList()),
        customExercises = listOf(custom()),
        workoutSessions = listOf(session("s1")),
        workoutSets = listOf(set())
    )

    /**
     * A file in a format that requires the equipment-profile section (>= 14), which `validFile()`
     * deliberately leaves empty because it pins format 13. Tests about a later section use this base
     * so the only thing under test is that section.
     */
    private fun profiledFile(formatVersion: Int) = validFile().copy(
        formatVersion = formatVersion,
        equipmentProfiles = listOf(
            BackupEquipmentProfileRecord(1L, "Home", emptyList(), emptyList(), LoadingInventory())
        ),
        activeEquipmentProfileId = 1L
    )

    /** One accepted-plan slot, so a plan-entry duration test only exercises that section. */
    private fun planEntryFile(formatVersion: Int, reps: Int?, durationSeconds: Int?) =
        profiledFile(formatVersion).copy(
            plans = listOf(BackupPlanRecord(7L, "DETERMINISTIC", 1L, 1, 1)),
            planDays = listOf(BackupPlanDayRecord(70L, 7L, 0, "FULL_BODY")),
            planEntries = listOf(
                BackupPlanEntryRecord(
                    id = 700L,
                    dayId = 70L,
                    position = 0,
                    exerciseId = "back-squat",
                    sets = 3,
                    reps = reps,
                    durationSeconds = durationSeconds,
                    exerciseName = "Back Squat",
                    movementPattern = "SQUAT",
                    suggestedWeightKg = 100.0,
                    loadCapability = "EXTERNAL",
                    loadKind = "EXTERNAL"
                )
            )
        )

    /** One persisted warm-up preparation override, so that section is the only thing under test. */
    private fun prepOverride(action: String, reps: Int? = null, durationSeconds: Int? = null) =
        BackupWarmupPreparationOverrideRecord(action, reps, durationSeconds)

    private fun overrideFile(
        formatVersion: Int,
        vararg overrides: BackupWarmupPreparationOverrideRecord
    ) = profiledFile(formatVersion).copy(warmupPreparationOverrides = overrides.toList())

    private fun targetReasonFile() = validFile().copy(
        plans = listOf(BackupPlanRecord(7L, "DETERMINISTIC", 1L, 1, 1)),
        planDays = listOf(BackupPlanDayRecord(70L, 7L, 0, "FULL_BODY")),
        planEntries = listOf(
            BackupPlanEntryRecord(
                id = 700L,
                dayId = 70L,
                position = 0,
                exerciseId = "back-squat",
                sets = 3,
                reps = 5,
                exerciseName = "Back Squat",
                movementPattern = "SQUAT",
                suggestedWeightKg = 100.0,
                loadCapability = "EXTERNAL",
                loadKind = "EXTERNAL"
            )
        ),
        planTargetExplanations = listOf(targetExplanation()),
        planTargetExplanationStates = listOf(targetState())
    )

    private fun targetExplanation(position: Int = 0) = BackupPlanTargetExplanationRecord(
        planId = 7L,
        dayIndex = 0,
        position = position,
        explanationVersion = 1,
        baselineSource = null,
        baselineRecentEstimateKg = null,
        baselineQualifyingSetCount = null,
        baselineManualFloorKg = null,
        baselineKg = null,
        progressionOutcome = null,
        progressionIncrements = null,
        progressionDeltaKg = null,
        progressionRoundToKg = null,
        progressionSuccessStreakSize = null,
        progressionFailureStreakSize = null,
        conversionReps = null,
        conversionIntensityFraction = null,
        conversionDeloadIntensityScale = null,
        conversionRoundToKg = null,
        conversionUncappedWorkingKg = null,
        conversionEquipmentCeilingKg = null,
        conversionEquipmentCeilingBinds = null,
        aiProposedKg = null,
        aiProposedAccepted = null,
        aiDeloadScaled = null,
        aiDeloadIntensityScale = null,
        aiProgressionBoundKg = null,
        aiBoundedByProgression = null,
        aiEquipmentCeilingKg = null,
        aiCappedByEquipment = null,
        setBaseSets = null,
        setDeloadApplied = null,
        setDeloadVolumeScale = null,
        setFatigueEffect = null,
        setFinalSets = null,
        unavailableReason = null
    )

    private fun targetState() = BackupPlanTargetExplanationStateRecord(
        planId = 7L,
        attribution = "DETERMINISTIC",
        isDeload = false,
        deloadVolumeScale = 0.7,
        deloadIntensityScale = 0.8,
        explanationVersion = 1
    )

    private fun frozenEntryReasonFile() = targetReasonFile().copy(
        activations = listOf(activation(1L)),
        activationWorkouts = listOf(activationWorkout(10L, 1L)),
        activationEntries = listOf(activationEntry(100L, 10L)),
        occurrences = listOf(occurrence(20L, 1L, 10L)),
        occurrenceEntries = listOf(occurrenceEntry(200L, 20L)),
        entryTargetExplanations = listOf(
            entryExplanation("ACTIVATION", 100L),
            entryExplanation("OCCURRENCE", 200L)
        )
    )

    private fun entryExplanation(entryKind: String, entryId: Long) =
        BackupEntryTargetExplanationRecord(
            entryKind = entryKind,
            entryId = entryId,
            explanationVersion = 1,
            baselineSource = null,
            baselineRecentEstimateKg = null,
            baselineQualifyingSetCount = null,
            baselineManualFloorKg = null,
            baselineKg = null,
            progressionOutcome = null,
            progressionIncrements = null,
            progressionDeltaKg = null,
            progressionRoundToKg = null,
            progressionSuccessStreakSize = null,
            progressionFailureStreakSize = null,
            conversionReps = null,
            conversionIntensityFraction = null,
            conversionDeloadIntensityScale = null,
            conversionRoundToKg = null,
            conversionUncappedWorkingKg = null,
            conversionEquipmentCeilingKg = null,
            conversionEquipmentCeilingBinds = null,
            aiProposedKg = null,
            aiProposedAccepted = null,
            aiDeloadScaled = null,
            aiDeloadIntensityScale = null,
            aiProgressionBoundKg = null,
            aiBoundedByProgression = null,
            aiEquipmentCeilingKg = null,
            aiCappedByEquipment = null,
            setBaseSets = null,
            setDeloadApplied = null,
            setDeloadVolumeScale = null,
            setFatigueEffect = null,
            setFinalSets = null,
            unavailableReason = null
        )

    private fun activationEntry(id: Long, workoutId: Long) = BackupActivationEntryRecord(
        id = id,
        workoutId = workoutId,
        position = 0,
        exerciseId = "back-squat",
        exerciseName = "Back Squat",
        movementPattern = "SQUAT",
        requiredEquipment = "BARBELL",
        involvements = null,
        isUnilateral = false,
        sets = 3,
        reps = 5,
        weightKg = 100.0,
        loadCapability = "EXTERNAL",
        loadKind = "EXTERNAL"
    )

    private fun custom(
        id: String = "user-x",
        name: String = "X",
        requiredEquipment: String = "BARBELL",
        involvements: String? = null
    ) = BackupExerciseRecord(
        id = id,
        name = name,
        requiredEquipment = requiredEquipment,
        movementPattern = "SQUAT",
        isUnilateral = false,
        loadCapability = "EXTERNAL",
        involvements = involvements
    )

    private fun set(
        id: Long = 1L,
        exerciseId: String = "back-squat",
        weightKg: Double? = 100.0,
        loadKind: String = "EXTERNAL",
        timingProvenance: String = "UNKNOWN",
        startedAtElapsedMillis: Long? = null,
        completedAtElapsedMillis: Long? = null,
        involvements: String? = null,
        sessionId: String? = "s1",
        occurrenceId: Long? = null,
        occurrenceEntryId: Long? = null,
        reps: Int? = 5,
        durationSeconds: Int? = null,
        prescriptionSnapshot: String? = null
    ) = BackupWorkoutSetRecord(
        id = id,
        exerciseId = exerciseId,
        reps = reps,
        weightKg = weightKg,
        performedAtMillis = 1L,
        isWarmup = false,
        involvements = involvements,
        weekNumber = null,
        cycleNumber = null,
        dayIndex = null,
        rir = null,
        sessionId = sessionId,
        occurrenceId = occurrenceId,
        occurrenceEntryId = occurrenceEntryId,
        loadKind = loadKind,
        timingProvenance = timingProvenance,
        startedAtElapsedMillis = startedAtElapsedMillis,
        completedAtElapsedMillis = completedAtElapsedMillis,
        durationSeconds = durationSeconds,
        prescriptionSnapshot = prescriptionSnapshot
    )

    private fun session(id: String) = BackupWorkoutSessionRecord(
        id = id,
        startedAtMillis = 1L,
        endedAtMillis = null,
        localEpochDay = 0L,
        occurrenceId = null
    )

    private fun snapshot(
        exerciseId: String = "back-squat",
        loadKind: String = "EXTERNAL",
        weightKg: Double? = 100.0,
        policy: SlotProgressionPolicy? = null,
        setIndex: Int? = null,
        targetRir: Int? = null
    ) = WorkoutPrescriptionSnapshotJson.encode(
        WorkoutPrescriptionSnapshot(
            exerciseId = exerciseId,
            sets = 3,
            reps = 5,
            loadKind = LoadKind.valueOf(loadKind),
            weightKg = weightKg,
            isDeload = false,
            progressionPolicy = policy,
            setIndex = setIndex,
            targetRir = targetRir
        )
    )
    private fun activation(id: Long) = BackupActivationRecord(
        id = id,
        templateId = null,
        templateRevision = null,
        sourcePlanId = null,
        name = "Block",
        createdAtMillis = 1L,
        startEpochDay = 1L,
        mode = "SEQUENCE",
        weekdayMask = 0L,
        status = "ACTIVE",
        weekNumber = 1,
        cycleNumber = 1,
        endedAtMillis = null,
        revision = 1
    )

    private fun activationWorkout(id: Long, activationId: Long) = BackupActivationWorkoutRecord(
        id = id,
        activationId = activationId,
        sourceWorkoutId = null,
        position = 0,
        name = "Day",
        focus = "FULL_BODY"
    )

    private fun occurrence(id: Long, activationId: Long, workoutId: Long) = BackupOccurrenceRecord(
        id = id,
        activationId = activationId,
        activationWorkoutId = workoutId,
        queuePosition = 0,
        scheduledEpochDay = null,
        notBeforeEpochDay = null,
        startedAtMillis = null,
        resolvedAtMillis = null,
        status = "PENDING",
        revision = 1
    )

    private fun occurrenceEntry(id: Long, occurrenceId: Long) = BackupOccurrenceEntryRecord(
        id = id,
        occurrenceId = occurrenceId,
        sourceActivationEntryId = null,
        position = 0,
        exerciseId = "back-squat",
        exerciseName = "Back Squat",
        movementPattern = "SQUAT",
        requiredEquipment = "BARBELL",
        involvements = null,
        isUnilateral = false,
        sets = 3,
        reps = 5,
        weightKg = 100.0,
        loadCapability = "EXTERNAL",
        loadKind = "EXTERNAL",
        remainingDisposition = null,
        terminalRemainingSets = null
    )

    private fun explanation(planId: Long, muscle: String) = BackupVolumeExplanationRecord(
        planId = planId,
        muscle = muscle,
        targetSets = 4,
        isTargetEnforced = true,
        directIsolationSets = 4,
        estimatedOtherInvolvementCredits = 1.0,
        unmetReason = null,
        attribution = "DETERMINISTIC"
    )
}
