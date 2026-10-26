package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.routine.PrescriptionBounds
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.WorkoutLoadPolicy
import com.hydrafit.app.core.domain.workout.requireForceIsLoadFree
import com.hydrafit.app.core.domain.workout.requireValidSetMeasurement
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One prescribed set's own target.
 *
 * A slot that carries ordered targets gives every set its own measure, load and optional prescribed
 * effort, so a pyramid is a real prescription rather than one repeated number. [targetRir] is the
 * effort the slot *prescribes*: it is never written back as the effort the user actually recorded.
 */
@Serializable
data class SetTarget(
    /** Repetitions for counted work, or null for a timed set. Never a `0` sentinel. */
    val reps: Int? = null,
    /** Elapsed whole seconds for a timed set, or null for counted work. */
    val durationSeconds: Int? = null,
    val loadKind: LoadKind = LoadKind.EXTERNAL,
    /** null = unspecified load; 0.0 = an explicit zero load; positive = stored kilograms. */
    val weightKg: Double? = null,
    /** Prescribed reps in reserve, or null when this set prescribes no effort target. */
    val targetRir: Int? = null,
    /** Whole metres for a distance target, or null for reps and timed work. */
    val distanceMeters: Int? = null,
    /** Measured force in newtons for a force target, or null for every other metric. */
    val forceNewtons: Double? = null
) {
    fun requireValid() {
        requireValidSetMeasurement(reps, durationSeconds, distanceMeters, forceNewtons)
        if (distanceMeters != null) {
            require(
                distanceMeters in
                    PrescriptionBounds.MIN_DISTANCE_METERS..PrescriptionBounds.MAX_DISTANCE_METERS
            ) {
                "Distance must be within the prescription bounds"
            }
        }
        if (forceNewtons != null) {
            require(
                forceNewtons in
                    PrescriptionBounds.MIN_FORCE_NEWTONS..PrescriptionBounds.MAX_FORCE_NEWTONS
            ) {
                "Force must be within the prescription bounds"
            }
        }
        if (reps != null) {
            require(reps in PrescriptionBounds.MIN_REPS..PrescriptionBounds.MAX_REPS) {
                "Reps must be between ${PrescriptionBounds.MIN_REPS} and " +
                    "${PrescriptionBounds.MAX_REPS}"
            }
        }
        require(targetRir == null || targetRir in MIN_RIR..MAX_RIR) {
            "A prescribed RIR must be between $MIN_RIR and $MAX_RIR"
        }
        require(weightKg == null || weightKg.toRawBits() != NEGATIVE_ZERO_BITS) {
            "A negative zero load is not a recorded load"
        }
        requireForceIsLoadFree(forceNewtons, weightKg)
        WorkoutLoadPolicy.validateShape(loadKind, weightKg)
    }

    /**
     * The equivalent resistance-training RPE for this set's prescribed effort, or null when it
     * prescribes none. RIR and RPE describe the same proximity to failure, so `RPE = 10 - RIR`; this
     * is a presentation of the same target, never a second independent value.
     */
    val equivalentRpe: Int? get() = targetRir?.let { RPE_SCALE - it }

    companion object {
        const val MIN_RIR: Int = 0
        const val MAX_RIR: Int = 4

        /** The conventional scale the RIR-to-RPE conversion is defined on. */
        const val RPE_SCALE: Int = 10

        /** `-0.0` renders as zero but is a distinct bit pattern; it is not an explicit zero load. */
        private val NEGATIVE_ZERO_BITS: Long = (-0.0).toRawBits()
    }
}

/**
 * A slot's ordered per-set targets. The list order *is* the prescription: index 0 is the first set.
 *
 * A slot with no ordered targets keeps its existing single repeated target, so a legacy or simply
 * repeated prescription is never reinterpreted as an ordered one. Nothing here infers a historical
 * set's slot index; only newly authored ordered prescriptions carry one.
 */
@Serializable
data class SlotSetTargets(
    val version: Int = TARGETS_VERSION,
    val targets: List<SetTarget>,
    /**
     * The recipe inputs that produced these targets, or null when they were not generated from one.
     * Recorded so reopening a slot shows what the user entered rather than an inferred shape; it is
     * inert for evaluation, which reads [targets] only.
     */
    val recipe: SetPrescriptionRecipeSnapshot? = null
) {
    fun requireValid() {
        require(version == TARGETS_VERSION) { "Unsupported ordered target version" }
        require(targets.size in 1..PrescriptionBounds.MAX_SETS) {
            "An ordered prescription allows between 1 and ${PrescriptionBounds.MAX_SETS} sets"
        }
        targets.forEach { it.requireValid() }
        recipe?.requireValid()
    }

    /** The number of prescribed sets; always the list size, never a separate count. */
    val sets: Int get() = targets.size

    operator fun get(index: Int): SetTarget? = targets.getOrNull(index)

    companion object {
        const val TARGETS_VERSION: Int = 1

        /** Builds a validated ordered prescription from already-built targets. */
        fun of(
            targets: List<SetTarget>,
            recipe: SetPrescriptionRecipeSnapshot? = null
        ): SlotSetTargets = SlotSetTargets(targets = targets, recipe = recipe)
            .also { it.requireValid() }
    }
}

/** Strict persisted encoding; a corrupt ordered prescription must never become an approved plan. */
object SlotSetTargetsJson {
    private val json = Json { encodeDefaults = true }

    fun encode(targets: SlotSetTargets): String {
        targets.requireValid()
        return json.encodeToString(SlotSetTargets.serializer(), targets)
    }

    fun decode(text: String): SlotSetTargets =
        json.decodeFromString(SlotSetTargets.serializer(), text).also { it.requireValid() }
}
