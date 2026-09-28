package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.fatigue.MuscleGroup

data class PlanRequest(
    val daysPerWeek: Int,
    val availableEquipment: Set<EquipmentTag>,
    val muscleFatigue: Map<MuscleGroup, Double>,
    val splitPreference: SplitType = SplitType.AUTO,
    val nowMillis: Long,
    val setsPerExercise: Int = 3
)
