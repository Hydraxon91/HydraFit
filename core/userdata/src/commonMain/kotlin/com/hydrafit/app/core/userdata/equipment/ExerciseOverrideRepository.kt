package com.hydrafit.app.core.userdata.equipment

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup

/**
 * Per-exercise overrides layered on top of the seeded catalog. Only built-in exercises use this;
 * custom exercises are edited directly. A null field keeps the seeded value, so [reset] restores it.
 */
interface ExerciseOverrideRepository {
    suspend fun update(
        exerciseId: String,
        name: String?,
        requiredEquipment: Set<EquipmentTag>,
        primaryMuscles: Set<MuscleGroup>,
        secondaryMuscles: Set<MuscleGroup>,
        movementPattern: MovementPattern?,
        unilateral: Boolean? = null
    )

    suspend fun reset(exerciseId: String)
}
