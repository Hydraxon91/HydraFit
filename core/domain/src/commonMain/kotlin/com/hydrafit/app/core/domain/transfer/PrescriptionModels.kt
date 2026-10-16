package com.hydrafit.app.core.domain.transfer

import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.routine.PrescriptionBounds
import com.hydrafit.app.core.domain.workout.LoadKind
import kotlinx.serialization.Serializable

/** Stable file identifiers. A reader accepts only [PRESCRIPTION_FORMAT] at a version it knows. */
const val PRESCRIPTION_FORMAT: String = "hydrafit-routines"

/** The interchange version this build writes. */
const val PRESCRIPTION_FORMAT_VERSION: Int = 1

/** Resource safeguards for one prescription document; deliberately smaller than a full backup. */
object PrescriptionLimits {
    const val MAX_BYTES: Long = 2L * 1024 * 1024
    const val MAX_DEPTH: Int = 24
    const val MAX_STRING_LENGTH: Int = 16 * 1024
    const val MAX_RECORDS: Int = 20_000
}

/**
 * The portable prescription document: routines, their ordered slots and only the custom exercise
 * definitions they reference.
 *
 * It is a prescription container, not a data export. Performed history, personal records, notes,
 * credentials, equipment inventories, active schedule state, cycle progress and engine explanations
 * have no representation here, and the codec rejects an unknown field instead of ignoring it, so a
 * document cannot smuggle one in. [includesWeights] is the recorded user choice: prescribed weights
 * are omitted unless it is true, and a false document may not carry one.
 *
 * Referenced exercise identities are resolved by the importer against the receiving catalog and the
 * document's own [customExercises]; this type validates only what is checkable without a catalog.
 */
@Serializable
data class PrescriptionDocument(
    val format: String,
    val formatVersion: Int,
    val appVersion: String,
    val exportedAtMillis: Long,
    val includesWeights: Boolean = false,
    val routines: List<PrescriptionRoutineRecord>,
    val customExercises: List<PrescriptionExerciseRecord> = emptyList()
)

/** One routine, transferred as an editable draft rather than an accepted or scheduled plan. */
@Serializable
data class PrescriptionRoutineRecord(
    val name: String,
    val focus: String? = null,
    val entries: List<PrescriptionEntryRecord> = emptyList()
)

/** One ordered prescription slot. [weightKg] is null unless the document includes weights. */
@Serializable
data class PrescriptionEntryRecord(
    val exerciseId: String,
    val exerciseName: String,
    val sets: Int,
    val reps: Int,
    val weightKg: Double? = null,
    val loadKind: String = "EXTERNAL",
    val movementPattern: String? = null,
    val loadCapability: String? = null
)

/** A custom exercise definition the document's routines need, in resolved form (not a stored blob). */
@Serializable
data class PrescriptionExerciseRecord(
    val id: String,
    val name: String,
    val requiredEquipment: List<String>,
    val movementPattern: String,
    val isUnilateral: Boolean,
    val loadCapability: String,
    /** Muscle name to involvement weight, or null when only the muscle tags were recorded. */
    val involvements: Map<String, Double>? = null
)

/** Why a prescription document could not be read. */
enum class PrescriptionFailure {
    UNSUPPORTED_VERSION,
    MALFORMED,
    TOO_LARGE,
    OVER_LIMIT,
    INVALID_VALUE,
    DUPLICATE_ID
}

class PrescriptionException(val failure: PrescriptionFailure, message: String? = null) :
    IllegalArgumentException(message ?: failure.name)

/** Validates a document's internal consistency and bounds without touching storage or a catalog. */
object PrescriptionValidator {

    fun validate(document: PrescriptionDocument) {
        if (document.format != PRESCRIPTION_FORMAT ||
            document.formatVersion != PRESCRIPTION_FORMAT_VERSION
        ) {
            fail(PrescriptionFailure.UNSUPPORTED_VERSION)
        }
        if (document.routines.isEmpty()) fail(PrescriptionFailure.INVALID_VALUE)
        if (document.appVersion.length > PrescriptionLimits.MAX_STRING_LENGTH) {
            fail(PrescriptionFailure.OVER_LIMIT)
        }
        val recordCount = document.routines.size +
            document.routines.sumOf { it.entries.size } +
            document.customExercises.size
        if (recordCount > PrescriptionLimits.MAX_RECORDS) fail(PrescriptionFailure.OVER_LIMIT)

        val customIds = uniqueBy(document.customExercises) { it.id }
        document.customExercises.forEach { exercise ->
            if (exercise.id.isBlank() ||
                exercise.name.isBlank() ||
                exercise.requiredEquipment.isEmpty() ||
                exercise.requiredEquipment.any { it.isBlank() }
            ) {
                fail(PrescriptionFailure.INVALID_VALUE)
            }
            enumName(exercise.movementPattern, MovementPattern.entries)
            enumName(exercise.loadCapability, ExerciseLoadCapability.entries)
            exercise.involvements?.let(::involvements)
        }
        document.routines.forEach { routine ->
            if (routine.name.isBlank()) fail(PrescriptionFailure.INVALID_VALUE)
            routine.focus?.let { enumName(it, SplitFocus.entries) }
            routine.entries.forEach { entry ->
                if (entry.exerciseId.isBlank() || entry.exerciseName.isBlank()) {
                    fail(PrescriptionFailure.INVALID_VALUE)
                }
                if (entry.weightKg != null && !document.includesWeights) {
                    // A weights-free document that carries a weight is not what it claims.
                    fail(PrescriptionFailure.INVALID_VALUE)
                }
                if (runCatching {
                        PrescriptionBounds.validate(entry.sets, entry.reps, entry.weightKg)
                    }.isFailure
                ) {
                    fail(PrescriptionFailure.INVALID_VALUE)
                }
                enumName(entry.loadKind, LoadKind.entries)
                entry.movementPattern?.let { enumName(it, MovementPattern.entries) }
                entry.loadCapability?.let { enumName(it, ExerciseLoadCapability.entries) }
            }
        }
        // A declared custom definition is only meaningful when a routine references it.
        val referenced = document.routines.flatMap { routine ->
            routine.entries.map { it.exerciseId }
        }.toSet()
        if (customIds.any { it !in referenced }) fail(PrescriptionFailure.INVALID_VALUE)
    }

    private fun involvements(values: Map<String, Double>) {
        values.forEach { (muscle, weight) ->
            if (muscle !in MUSCLE_TOKENS) fail(PrescriptionFailure.INVALID_VALUE)
            if (!weight.isFinite() || weight < 0.0) fail(PrescriptionFailure.INVALID_VALUE)
        }
    }

    private fun enumName(value: String, known: List<*>) {
        if (known.none { (it as Enum<*>).name == value }) fail(PrescriptionFailure.INVALID_VALUE)
    }

    private fun <T, K> uniqueBy(items: List<T>, key: (T) -> K): Set<K> {
        val seen = mutableSetOf<K>()
        items.forEach { if (!seen.add(key(it))) fail(PrescriptionFailure.DUPLICATE_ID) }
        return seen
    }

    private fun fail(failure: PrescriptionFailure): Nothing = throw PrescriptionException(failure)

    private val MUSCLE_TOKENS: Set<String> =
        MuscleGroup.entries.map { it.name }.toSet() +
            setOf("CHEST", "BACK", "SHOULDERS", "CORE")
}
