package com.hydrafit.app.core.userdata.equipment

import com.hydrafit.app.core.domain.fatigue.MuscleGroup

/**
 * Per-exercise muscle-mapping overrides, layered on top of the seeded catalog. Storing them
 * separately keeps the seeded data as the fallback, so [reset] restores the original mapping.
 */
interface ExerciseMuscleRepository {
    suspend fun update(
        exerciseId: String,
        primaryMuscles: Set<MuscleGroup>,
        secondaryMuscles: Set<MuscleGroup>
    )

    suspend fun reset(exerciseId: String)
}
