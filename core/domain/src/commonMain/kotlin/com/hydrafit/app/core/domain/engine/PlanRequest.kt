package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup

const val DEFAULT_SETS_PER_EXERCISE = 3

data class PlanRequest(
    val daysPerWeek: Int,
    val availableEquipment: Set<EquipmentTag>,
    val muscleFatigue: Map<MuscleGroup, Double>,
    val splitPreference: SplitType = SplitType.AUTO,
    val nowMillis: Long,
    val goal: TrainingGoal = TrainingGoal.BALANCED,
    val setsPerExercise: Int = goal.defaultSets,
    val accessorySetsPerExercise: Int = goal.accessorySets,
    val recentExerciseIdsByPattern: Map<MovementPattern, Set<String>> = emptyMap(),
    val suggestedWeightsKg: Map<String, Double> = emptyMap(),
    val includeWorkoutData: Boolean = false,
    val recentWeights: List<WeightHistoryEntry> = emptyList()
)
