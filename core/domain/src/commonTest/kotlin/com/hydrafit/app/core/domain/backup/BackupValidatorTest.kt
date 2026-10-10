package com.hydrafit.app.core.domain.backup

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
    fun rejectsAnUnknownExerciseReference() {
        assertFailure(BackupFailure.UNKNOWN_CATALOG_ID) {
            validFile().copy(
                workoutSets = listOf(set(exerciseId = "not-a-known-exercise"))
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

    private fun assertFailure(expected: BackupFailure, mutate: () -> BackupFile) {
        val failure = assertFailsWith<BackupException> { validator.validate(mutate()) }
        assertEquals(expected, failure.failure)
    }

    private fun validFile() = emptyBackupFile().copy(
        appVersion = "t",
        exportedAtMillis = 1L,
        catalog = BackupCatalogManifest(listOf("back-squat"), seedProfiles = emptyList()),
        customExercises = listOf(custom()),
        workoutSessions = listOf(session("s1")),
        workoutSets = listOf(set())
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
        involvements: String? = null,
        sessionId: String? = "s1",
        occurrenceId: Long? = null,
        occurrenceEntryId: Long? = null
    ) = BackupWorkoutSetRecord(
        id = id,
        exerciseId = exerciseId,
        reps = 5,
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
        timingProvenance = timingProvenance
    )

    private fun session(id: String) = BackupWorkoutSessionRecord(
        id = id,
        startedAtMillis = 1L,
        endedAtMillis = null,
        localEpochDay = 0L,
        occurrenceId = null
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
