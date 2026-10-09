package com.hydrafit.app.core.domain.backup

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BackupValidatorTest {

    private val catalog = object : BackupCatalog {
        override fun seedExerciseIds(): Set<String> = setOf("back-squat")
        override fun builtInEquipmentIds(): Set<String> = setOf("BARBELL")
    }

    private val validator = BackupValidator(catalog)

    @Test
    fun acceptsAValidMinimalFile() {
        validator.validate(validFile())
    }

    @Test
    fun rejectsAnUnsupportedVersion() {
        assertFailure(BackupFailure.UNSUPPORTED_VERSION) {
            validFile().copy(formatVersion = 2)
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
    fun rejectsANonFiniteNumber() {
        assertFailure(BackupFailure.INVALID_VALUE) {
            validFile().copy(workoutSets = listOf(set(weightKg = Double.NaN)))
        }
    }

    private fun assertFailure(expected: BackupFailure, mutate: () -> BackupFile) {
        val failure = assertFailsWith<BackupException> { validator.validate(mutate()) }
        assertEquals(expected, failure.failure)
    }

    private fun validFile() = BackupFile(
        appVersion = "t",
        exportedAtMillis = 1L,
        catalog = BackupCatalogManifest(listOf("back-squat")),
        customExercises = listOf(custom()),
        workoutSets = listOf(set())
    )

    private fun custom(id: String = "user-x", requiredEquipment: String = "BARBELL") =
        BackupExerciseRecord(
            id = id,
            name = "X",
            requiredEquipment = requiredEquipment,
            movementPattern = "SQUAT",
            isUnilateral = false,
            loadCapability = "EXTERNAL",
            involvements = null
        )

    private fun set(
        id: Long = 1L,
        exerciseId: String = "back-squat",
        weightKg: Double? = 100.0,
        loadKind: String = "EXTERNAL"
    ) = BackupWorkoutSetRecord(
        id = id,
        exerciseId = exerciseId,
        reps = 5,
        weightKg = weightKg,
        performedAtMillis = 1L,
        isWarmup = false,
        involvements = null,
        weekNumber = null,
        cycleNumber = null,
        dayIndex = null,
        rir = null,
        sessionId = null,
        occurrenceId = null,
        occurrenceEntryId = null,
        loadKind = loadKind
    )
}
