package com.hydrafit.app.core.domain.transfer

import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.routine.RoutineEntry
import com.hydrafit.app.core.domain.routine.RoutineTemplate
import com.hydrafit.app.core.domain.routine.RoutineTemplateRepository
import com.hydrafit.app.core.domain.workout.LoadKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking

/** The versioned prescription document: strict shape, privacy by construction, ordered export. */
class PrescriptionInterchangeTest {

    private val seeded = Exercise(
        id = "back-squat",
        name = "Back Squat",
        requiredEquipment = setOf(EquipmentTag.BARBELL),
        primaryMuscles = setOf(MuscleGroup.QUADS)
    )
    private val custom = Exercise(
        id = "user-my-lift",
        name = "My Lift",
        requiredEquipment = setOf(EquipmentTag.DUMBBELL),
        primaryMuscles = emptySet(),
        movementPattern = MovementPattern.CORE,
        isCustom = true,
        isUnilateral = true,
        loadCapability = ExerciseLoadCapability.BODYWEIGHT_ADDABLE,
        involvements = mapOf(MuscleGroup.ABS to 0.7, MuscleGroup.OBLIQUES to 0.3)
    )
    private val catalog = object : ExerciseCatalog {
        override suspend fun all(): List<Exercise> = listOf(seeded, custom)
    }

    private val routine = RoutineTemplate(
        id = 7L,
        name = "Push day",
        workouts = listOf(
            com.hydrafit.app.core.domain.routine.RoutineWorkout(
                position = 0,
                name = "Day 1",
                entries = listOf(
                    RoutineEntry(
                        position = 0,
                        exerciseId = "back-squat",
                        sets = 3,
                        reps = 5,
                        weightKg = 100.0,
                        loadKind = LoadKind.EXTERNAL
                    ),
                    RoutineEntry(
                        position = 1,
                        exerciseId = "user-my-lift",
                        sets = 2,
                        reps = 12,
                        weightKg = 5.0,
                        loadKind = LoadKind.ADDED
                    )
                )
            )
        )
    )

    private fun repository() = object : RoutineTemplateRepository {
        override fun observeAll(): Flow<List<RoutineTemplate>> = flowOf(listOf(routine))
        override suspend fun get(id: Long): RoutineTemplate? = routine.takeIf { it.id == id }
        override suspend fun save(template: RoutineTemplate): Long = 0L
        override suspend fun setArchived(id: Long, archivedAtMillis: Long?) = Unit
        override suspend fun isReferencedByActivation(id: Long): Boolean = false
        override suspend fun delete(id: Long) = Unit
    }

    @Test
    fun exportOmitsWeightsByDefaultAndIncludesThemOnlyOnRequest() = runBlocking {
        val stripped = ExportPrescriptionsUseCase(repository(), catalog)(listOf(7L), false, "t", 1L)
        assertFalse(stripped.includesWeights)
        assertTrue(stripped.routines.single().allEntries().all { it.weightKg == null })
        PrescriptionValidator.validate(stripped)
        // The custom definition travels with its resolved involvement map.
        assertEquals(
            mapOf("ABS" to 0.7, "OBLIQUES" to 0.3),
            stripped.customExercises.single().involvements
        )
        assertEquals(listOf("DUMBBELL"), stripped.customExercises.single().requiredEquipment)

        val withWeights =
            ExportPrescriptionsUseCase(repository(), catalog)(listOf(7L), true, "t", 1L)
        assertTrue(withWeights.includesWeights)
        val entries = withWeights.routines.single().allEntries()
        assertEquals(listOf(100.0, 5.0), entries.map { it.weightKg })
        assertEquals(listOf("EXTERNAL", "ADDED"), entries.map { it.loadKind })
        assertEquals(listOf(3, 2), entries.map { it.sets })
        PrescriptionValidator.validate(withWeights)
    }

    @Test
    fun roundTripsThroughJsonAndRejectsUnknownFields() = runBlocking {
        val document = ExportPrescriptionsUseCase(repository(), catalog)(listOf(7L), true, "t", 42L)
        val text = PrescriptionJson.encode(document)
        assertEquals(document, PrescriptionJson.decode(text))

        // Every section the contract excludes is unrepresentable, so a document cannot carry one.
        listOf(
            "workoutSets",
            "personalRecords",
            "sessionNotes",
            "exerciseNotes",
            "credentials",
            "equipmentProfiles",
            "scheduleState",
            "activations",
            "planTargetExplanations"
        ).forEach { forbidden ->
            val smuggled = text.dropLast(1) + ",\"$forbidden\":[],}"
            val failure =
                assertFailsWith<PrescriptionException> { PrescriptionJson.decode(smuggled) }
            assertEquals(PrescriptionFailure.MALFORMED, failure.failure)
        }
        // The encoded document names only prescription fields.
        listOf("workoutSets", "personalRecord", "note", "credential", "equipmentProfile").forEach {
            assertFalse(text.contains(it), "encoded document leaked $it")
        }
    }

    @Test
    fun rejectsAMissingSectionAnUnknownVersionAndAMalformedBody() {
        val document = PrescriptionDocument(
            format = PRESCRIPTION_FORMAT,
            formatVersion = PRESCRIPTION_FORMAT_VERSION,
            appVersion = "t",
            exportedAtMillis = 1L,
            routines = listOf(
                PrescriptionRoutineRecord(
                    "Push",
                    workouts = listOf(
                        PrescriptionWorkoutRecord(
                            "Day 1",
                            entries = listOf(entry("back-squat"))
                        )
                    )
                )
            )
        )
        val text = PrescriptionJson.encode(document)
        val missing = text.replace(",\"customExercises\":[]", "")
        assertFailsWith<PrescriptionException> { PrescriptionJson.decode(missing) }
        assertFailsWith<PrescriptionException> { PrescriptionJson.decode("not json") }
        val unknownVersion = document.copy(formatVersion = 99)
        assertEquals(
            PrescriptionFailure.UNSUPPORTED_VERSION,
            assertFailsWith<PrescriptionException> {
                PrescriptionValidator.validate(unknownVersion)
            }
                .failure
        )
    }

    @Test
    fun aWeightsFreeDocumentCannotCarryAWeight() {
        val document = PrescriptionDocument(
            format = PRESCRIPTION_FORMAT,
            formatVersion = PRESCRIPTION_FORMAT_VERSION,
            appVersion = "t",
            exportedAtMillis = 1L,
            includesWeights = false,
            routines = listOf(
                PrescriptionRoutineRecord(
                    "Push",
                    workouts = listOf(
                        PrescriptionWorkoutRecord(
                            "Day 1",
                            entries = listOf(entry("back-squat").copy(weightKg = 80.0))
                        )
                    )
                )
            )
        )
        assertEquals(
            PrescriptionFailure.INVALID_VALUE,
            assertFailsWith<PrescriptionException> {
                PrescriptionValidator.validate(document)
            }.failure
        )
    }

    @Test
    fun rejectsOutOfBoundsSlotsDuplicateDefinitionsAndUnknownTokens() {
        fun document(
            entries: List<PrescriptionEntryRecord>,
            customExercises: List<PrescriptionExerciseRecord> = emptyList()
        ) = PrescriptionDocument(
            format = PRESCRIPTION_FORMAT,
            formatVersion = PRESCRIPTION_FORMAT_VERSION,
            appVersion = "t",
            exportedAtMillis = 1L,
            routines = listOf(
                PrescriptionRoutineRecord(
                    "Push",
                    workouts = listOf(PrescriptionWorkoutRecord("Day 1", entries = entries))
                )
            ),
            customExercises = customExercises
        )
        listOf(
            entry("back-squat").copy(sets = 0),
            entry("back-squat").copy(reps = 0),
            entry("back-squat").copy(loadKind = "NOPE"),
            entry(""),
            entry("back-squat").copy(exerciseName = "")
        ).forEach { invalid ->
            assertFailsWith<PrescriptionException> {
                PrescriptionValidator.validate(document(listOf(invalid)))
            }
        }
        // A declared definition must be referenced, and a referenced custom must be declared.
        val base = PrescriptionExerciseRecord(
            id = "user-my-lift",
            name = "My Lift",
            requiredEquipment = listOf("DUMBBELL"),
            movementPattern = "CORE",
            isUnilateral = true,
            loadCapability = "BODYWEIGHT_ADDABLE",
            involvements = mapOf("ABS" to 0.5)
        )
        // Declared but unreferenced.
        assertEquals(
            PrescriptionFailure.INVALID_VALUE,
            assertFailsWith<PrescriptionException> {
                PrescriptionValidator.validate(document(listOf(entry("back-squat")), listOf(base)))
            }.failure
        )
        // Referenced but declared with an unknown muscle token.
        assertEquals(
            PrescriptionFailure.INVALID_VALUE,
            assertFailsWith<PrescriptionException> {
                PrescriptionValidator.validate(
                    document(
                        listOf(entry("user-my-lift")),
                        listOf(base.copy(involvements = mapOf("NOT_A_MUSCLE" to 0.5)))
                    )
                )
            }.failure
        )
        // Referenced, declared and valid.
        PrescriptionValidator.validate(
            document(listOf(entry("user-my-lift")), listOf(base))
        )
        assertEquals(
            PrescriptionFailure.DUPLICATE_ID,
            assertFailsWith<PrescriptionException> {
                PrescriptionValidator.validate(
                    document(listOf(entry("user-my-lift")), listOf(base, base))
                )
            }.failure
        )
    }

    private fun entry(exerciseId: String) = PrescriptionEntryRecord(
        exerciseId = exerciseId,
        exerciseName = exerciseId,
        sets = 3,
        reps = 5
    )
}
