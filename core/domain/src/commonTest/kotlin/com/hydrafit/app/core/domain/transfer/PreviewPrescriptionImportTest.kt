package com.hydrafit.app.core.domain.transfer

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.workout.LoadKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Resolution of a prescription document against a local catalog: nothing is decided silently. */
class PreviewPrescriptionImportTest {

    private val seeded = Exercise(
        id = "back-squat",
        name = "Back Squat",
        requiredEquipment = setOf(EquipmentTag.BARBELL),
        primaryMuscles = setOf(MuscleGroup.QUADS)
    )
    private val bodyweight = Exercise(
        id = "user-pushup",
        name = "Push-up",
        requiredEquipment = setOf(EquipmentTag.BODYWEIGHT),
        primaryMuscles = setOf(MuscleGroup.CHEST_UPPER),
        loadCapability = ExerciseLoadCapability.BODYWEIGHT_ONLY
    )

    private fun document(
        routines: List<PrescriptionRoutineRecord>,
        customExercises: List<PrescriptionExerciseRecord> = emptyList(),
        includesWeights: Boolean = true
    ) = PrescriptionDocument(
        format = PRESCRIPTION_FORMAT,
        formatVersion = PRESCRIPTION_FORMAT_VERSION,
        appVersion = "t",
        exportedAtMillis = 1L,
        includesWeights = includesWeights,
        routines = routines,
        customExercises = customExercises
    )

    private fun routine(vararg ids: String, secondWorkout: Boolean = false) =
        PrescriptionRoutineRecord(
            name = "Shared plan",
            workouts = buildList {
                add(PrescriptionWorkoutRecord("Day 1", entries = ids.map { entry(it) }))
                if (secondWorkout) {
                    add(
                        PrescriptionWorkoutRecord("Day 2", entries = listOf(entry(ids.first())))
                    )
                }
            }
        )

    private fun entry(id: String, weight: Double? = 100.0) = PrescriptionEntryRecord(
        exerciseId = id,
        exerciseName = id,
        sets = 3,
        reps = 5,
        weightKg = weight,
        loadKind = LoadKind.EXTERNAL.name
    )

    private fun preview(
        document: PrescriptionDocument,
        choices: PrescriptionImportChoices = PrescriptionImportChoices(),
        catalog: List<Exercise> = listOf(seeded, bodyweight),
        equipment: Set<EquipmentTag> = setOf(EquipmentTag.BARBELL, EquipmentTag.BODYWEIGHT)
    ) = PreviewPrescriptionImport.build(document, choices, catalog, equipment)

    @Test
    fun anExistingLocalIdentityNeedsNoChoiceAndKeepsWorkoutOrder() {
        val result = preview(document(listOf(routine("back-squat", secondWorkout = true))))
        assertTrue(result.canImport, result.problems.toString())
        val imported = result.routines.single()
        assertEquals(listOf("Day 1", "Day 2"), imported.workouts.map { it.name })
        assertEquals(listOf(0, 1), imported.workouts.map { it.position })
        assertTrue(
            result.identities.single().resolution is PrescriptionResolution.Existing
        )
        assertTrue(result.customExercisesToCreate.isEmpty())
    }

    @Test
    fun aDeclaredDefinitionMustBeApprovedAndItsEquipmentGapAccepted() {
        val definition = PrescriptionExerciseRecord(
            id = "user-cable-fly",
            name = "Cable Fly",
            requiredEquipment = listOf("CABLE_MACHINE"),
            movementPattern = "HORIZONTAL_PUSH",
            isUnilateral = false,
            loadCapability = "EXTERNAL",
            involvements = mapOf("CHEST_UPPER" to 0.7)
        )
        val base = document(listOf(routine("user-cable-fly")), listOf(definition))

        assertFalse(preview(base).canImport)
        assertTrue(
            preview(base).problems.any { it.issue == PrescriptionImportIssue.UNDECIDED_IDENTITY }
        )

        val created = preview(
            base,
            PrescriptionImportChoices(mapOf("user-cable-fly" to PrescriptionIdentityChoice.Create))
        )
        // Cable machine is not selected, so the create is approved but the gap still blocks.
        assertTrue(
            created.problems.any { it.issue == PrescriptionImportIssue.EQUIPMENT_GAP },
            created.problems.toString()
        )
        assertEquals(1, created.customExercisesToCreate.size)
        assertEquals("user-cable-fly", created.customExercisesToCreate.single().id)

        val accepted = preview(
            base,
            PrescriptionImportChoices(
                mapOf("user-cable-fly" to PrescriptionIdentityChoice.AcceptEquipmentGap)
            )
        )
        assertFalse(accepted.problems.any { it.issue == PrescriptionImportIssue.EQUIPMENT_GAP })
        // Accepting the gap does not declare the definition as a locally selectable row.
        assertTrue(accepted.customExercisesToCreate.isEmpty())
    }

    @Test
    fun aNameCollisionCannotBeCreatedAndMustBeMapped() {
        val colliding = PrescriptionExerciseRecord(
            id = "user-other",
            name = "  back   squat ",
            requiredEquipment = listOf("BARBELL"),
            movementPattern = "SQUAT",
            isUnilateral = false,
            loadCapability = "EXTERNAL"
        )
        val base = document(listOf(routine("user-other")), listOf(colliding))

        assertTrue(
            preview(base).problems.any { it.issue == PrescriptionImportIssue.NAME_COLLISION }
        )
        // Creating it is refused: it would put a second exercise under the same name.
        assertTrue(
            preview(
                base,
                PrescriptionImportChoices(mapOf("user-other" to PrescriptionIdentityChoice.Create))
            ).problems.any { it.issue == PrescriptionImportIssue.NAME_COLLISION }
        )
        val mapped = preview(
            base,
            PrescriptionImportChoices(
                mapOf("user-other" to PrescriptionIdentityChoice.MapTo("back-squat"))
            )
        )
        assertTrue(mapped.canImport, mapped.problems.toString())
        assertEquals(
            "back-squat",
            mapped.routines.single().workouts.single().entries.single().exerciseId
        )
    }

    @Test
    fun anUnknownIdentityBlocksUntilMapped() {
        val base = document(listOf(routine("gone-forever")))
        assertTrue(
            preview(base).problems.any { it.issue == PrescriptionImportIssue.UNKNOWN_IDENTITY }
        )
        // Nothing is importable while the identity is unresolved, so no partial routine is offered.
        assertTrue(preview(base).routines.isEmpty())
        val mapped = preview(
            base,
            PrescriptionImportChoices(
                mapOf("gone-forever" to PrescriptionIdentityChoice.MapTo("back-squat"))
            )
        )
        assertTrue(mapped.canImport, mapped.problems.toString())
        // A MapTo that names nothing local is refused rather than dropped.
        assertTrue(
            preview(
                base,
                PrescriptionImportChoices(
                    mapOf("gone-forever" to PrescriptionIdentityChoice.MapTo("nope"))
                )
            ).problems.any { it.issue == PrescriptionImportIssue.INVALID_CHOICE }
        )
    }

    @Test
    fun aRoutineDroppedByTheRoutineValidatorReportsInvalidRoutine() {
        // The identity resolves, but the routine itself cannot be rebuilt into a valid local routine.
        val broken = PrescriptionRoutineRecord(
            name = "Broken",
            workouts = listOf(
                PrescriptionWorkoutRecord("  ", entries = listOf(entry("back-squat")))
            )
        )
        val result = preview(document(listOf(broken)))
        assertTrue(result.routines.isEmpty())
        assertTrue(
            result.problems.any { it.issue == PrescriptionImportIssue.INVALID_ROUTINE },
            result.problems.toString()
        )
        assertFalse(
            result.problems.any { it.issue == PrescriptionImportIssue.UNDECIDED_IDENTITY },
            result.problems.toString()
        )
        assertFalse(result.canImport)
    }

    @Test
    fun aResolvedLoadIsSnappedOntoTheLocalCapability() {
        val mapping = PrescriptionImportChoices(
            mapOf("gone" to PrescriptionIdentityChoice.MapTo("user-pushup"))
        )
        val result = preview(
            document(
                listOf(
                    PrescriptionRoutineRecord(
                        "Bodyweight",
                        workouts = listOf(
                            PrescriptionWorkoutRecord("Day 1", entries = listOf(entry("gone")))
                        )
                    )
                )
            ),
            mapping
        )
        assertTrue(result.canImport, result.problems.toString())
        val entry = result.routines.single().workouts.single().entries.single()
        // A bodyweight-only exercise cannot keep the document's external number.
        assertEquals(null, entry.weightKg)
        assertEquals(LoadKind.BODYWEIGHT, entry.loadKind)
    }

    @Test
    fun anEmptyDocumentIsRefused() {
        val result = preview(document(emptyList()))
        assertFalse(result.canImport)
        assertTrue(result.problems.any { it.issue == PrescriptionImportIssue.NO_ROUTINES })
    }
}
