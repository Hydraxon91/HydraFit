package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.ExercisePreference
import com.hydrafit.app.core.domain.equipment.LoadingInventory
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
    /**
     * Recorded added load advanced by the success/miss streak rule, in kilograms added to bodyweight.
     * It is a recorded value, not an estimate: engines prescribe it with
     * [com.hydrafit.app.core.domain.workout.LoadKind.ADDED] and never derive it from reps or body mass.
     */
    val suggestedAddedLoadsKg: Map<String, Double> = emptyMap(),
    val withheldWeightExerciseIds: Set<String> = emptySet(),
    /**
     * Progressed manual PR floors from records below [BuildPlannerLoadInputsUseCase]'s rep-range
     * threshold. They supply a floor only for a short prescription (fewer than three reps) and are
     * reference-only for a rep-range prescription. Missing evidence stays represented by
     * [withheldWeightExerciseIds].
     */
    val lowRepManualFloorsKg: Map<String, Double> = emptyMap(),
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
    /**
     * The active profile's saved bars, plates and dumbbells, used to round an automatic working
     * load down onto a real reachable value. Null or an empty inventory keeps the generic increment
     * rounding; machines are not represented here.
     */
    val loadingInventory: LoadingInventory? = null,
    val includeWorkoutData: Boolean = false,
    val recentWeights: List<WeightHistoryEntry> = emptyList(),
    val weekNumber: Int = 1,
    val cycleNumber: Int = 1,
    val isDeload: Boolean = false,
    /**
     * Recorded baseline/progression provenance per exercise id, produced by
     * [BuildPlannerLoadInputsUseCase]. Inert data: engines record it on their output explanations
     * and it never affects selection or prescription.
     */
    val loadExplanations: Map<String, LoadOriginExplanation> = emptyMap(),
    /** Confirmed civil dates in workout order; empty preserves the undated planning behavior. */
    val workoutEpochDays: List<Long> = emptyList(),
    /** Recorded-history decay only, calculated at each date; no hypothetical training is added. */
    val fatigueByWorkout: List<Map<MuscleGroup, Double>> = emptyList(),
    /**
     * The user's opt-in for generating ordered per-set prescriptions at plan time, or null for none.
     * Absent leaves every existing plan shape exactly as it was.
     */
    val automaticSetPrescription: AutomaticSetPrescription? = null,
    /**
     * The user's opt-in for curated, adjacent biceps/triceps pairing, or null to leave generated days
     * exactly as the engine produced them.
     */
    val plannerSupersetPreference: PlannerSupersetPreference? = null
) {
    init {
        require(workoutEpochDays.isEmpty() || workoutEpochDays.size == daysPerWeek)
        require(fatigueByWorkout.size == workoutEpochDays.size)
        require(workoutEpochDays.zipWithNext().all { (first, second) -> first < second })
        plannerSupersetPreference?.requireValid()
    }

    fun fatigueForWorkout(dayIndex: Int): Map<MuscleGroup, Double> =
        fatigueByWorkout.getOrNull(dayIndex) ?: muscleFatigue
}
