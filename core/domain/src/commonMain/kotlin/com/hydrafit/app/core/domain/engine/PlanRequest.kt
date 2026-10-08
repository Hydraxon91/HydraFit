package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.ExercisePreference
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
    /**
     * Progression-adjusted estimated-1RM bound per exercise, in kilograms; engines convert it to a
     * working load at prescribed reps. Missing evidence is represented by [withheldWeightExerciseIds].
     */
    val recentWeightCaps: Map<String, Double> = emptyMap(),
    val withheldWeightExerciseIds: Set<String> = emptySet(),
    /**
     * Explicit user preference per exercise id, used only as a soft ordering tier among candidates
     * that already passed the equipment, EX-01, soreness and coverage gates. Absence means
     * [ExercisePreference.NEUTRAL]. It never makes an exercise required and never excludes one.
     */
    val exercisePreferences: Map<String, ExercisePreference> = emptyMap(),
    /**
     * Exercise ids the user has excluded (EX-01 hard gate). Excluded candidates are unavailable to
     * generation, ranking and model sanitization; this is distinct from a soft
     * [ExercisePreference.PREFER_LESS].
     */
    val excludedExerciseIds: Set<String> = emptySet(),
    /** Heaviest weight each piece of equipment can provide; equipment absent here is unlimited. */
    val equipmentMaxWeights: Map<EquipmentTag, Double> = emptyMap(),
    val includeWorkoutData: Boolean = false,
    val recentWeights: List<WeightHistoryEntry> = emptyList(),
    val weekNumber: Int = 1,
    val cycleNumber: Int = 1,
    val isDeload: Boolean = false
)
