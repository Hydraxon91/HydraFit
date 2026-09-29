package com.hydrafit.app.core.userdata.equipment

import com.hydrafit.app.core.domain.equipment.EquipmentTag

/**
 * Per-exercise overrides on top of the seeded catalog. Storing them separately keeps the seeded
 * data as a known-good fallback, so [reset] restores the original requirements.
 */
interface ExerciseEquipmentRepository {
    suspend fun update(exerciseId: String, equipment: Set<EquipmentTag>)

    suspend fun reset(exerciseId: String)
}
