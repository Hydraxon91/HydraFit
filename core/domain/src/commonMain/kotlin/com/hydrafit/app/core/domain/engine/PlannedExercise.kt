package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.workout.LoadKind

data class PlannedExercise(
    val exerciseId: String,
    val sets: Int,
    val reps: Int,
    val suggestedWeightKg: Double? = null,
    /** What [suggestedWeightKg] means; external for the common weighted prescription. */
    val loadKind: LoadKind = LoadKind.EXTERNAL,
    /** The exercise's load capability at generation time; external by default. */
    val loadCapability: ExerciseLoadCapability = ExerciseLoadCapability.EXTERNAL
)
