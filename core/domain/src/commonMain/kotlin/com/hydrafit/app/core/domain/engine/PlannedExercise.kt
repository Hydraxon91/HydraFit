package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.routine.Superset
import com.hydrafit.app.core.domain.workout.LoadKind

data class PlannedExercise(
    val exerciseId: String,
    val sets: Int,
    /** Repetitions for counted work, or null for a timed slot. Never a `0` sentinel. */
    val reps: Int? = null,
    /**
     * Elapsed whole seconds for a timed slot, or null for counted work. The engine never invents a
     * duration: it is only ever set by the user's own measure edit.
     */
    val durationSeconds: Int? = null,
    val suggestedWeightKg: Double? = null,
    /** What [suggestedWeightKg] means; external for the common weighted prescription. */
    val loadKind: LoadKind = LoadKind.EXTERNAL,
    /** The exercise's load capability at generation time; external by default. */
    val loadCapability: ExerciseLoadCapability = ExerciseLoadCapability.EXTERNAL,
    /**
     * Recorded reasoning for this target, or null when the producer did not record one (for example
     * a slot built only as ranking context). Inert data: it never affects selection or prescription.
     */
    val explanation: ExerciseTargetExplanation? = null,
    /**
     * The slot's approved progression policy, or null when none applies. Carried so an accepted plan
     * keeps the rule the user approved for that slot.
     */
    val progressionPolicy: SlotProgressionPolicy? = null,
    /**
     * The slot's ordered per-set targets, or null when its sets share one repeated prescription. An
     * ordered slot's [sets] always equals [SlotSetTargets.sets].
     */
    val setTargets: SlotSetTargets? = null,
    val superset: Superset? = null,
    /** Whole metres for a distance slot, or null for reps and timed work. */
    val distanceMeters: Int? = null,
    /** Measured force in newtons for a force slot, or null for every other metric. */
    val forceNewtons: Double? = null
) {
    init {
        val metrics = listOf(
            reps != null,
            durationSeconds != null,
            distanceMeters != null,
            forceNewtons != null
        )
        require(metrics.count { it } == 1) {
            "A planned exercise is reps-based, timed, distance-based or force-based, exactly one"
        }
    }
}
