package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
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
    val explanation: ExerciseTargetExplanation? = null
) {
    init {
        require((reps == null) != (durationSeconds == null)) {
            "A planned exercise is either reps-based or timed"
        }
    }
}
