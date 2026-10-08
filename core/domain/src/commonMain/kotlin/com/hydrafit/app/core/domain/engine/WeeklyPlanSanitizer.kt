package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.WorkoutLoadPolicy
import kotlin.math.roundToInt

/**
 * Shared guard for model-backed plans. It drops exercises that are unknown or need unselected
 * equipment, applies the requested set count and the compound/isolation rep scheme, and rejects
 * a plan that is not a complete week. Returns null when the plan should be discarded.
 */
class WeeklyPlanSanitizer(
    private val catalog: ExerciseCatalog,
    private val volumeAwareReps: VolumeAwareReps = VolumeAwareReps(),
    private val varietyEnforcer: PlanVarietyEnforcer = PlanVarietyEnforcer(),
    private val periodization: PeriodizationConfig = PeriodizationConfig(),
    private val weightConfig: SuggestedWeightConfig = SuggestedWeightConfig()
) {

    suspend fun sanitize(plan: WeeklyPlan, request: PlanRequest): WeeklyPlan? {
        val minSets = DeterministicWorkoutPlannerEngine.MIN_SETS
        val maxSets = DeterministicWorkoutPlannerEngine.MAX_SETS
        require(request.setsPerExercise in minSets..maxSets) {
            "setsPerExercise must be between $minSets and $maxSets"
        }
        require(request.accessorySetsPerExercise in minSets..maxSets) {
            "accessorySetsPerExercise must be between $minSets and $maxSets"
        }
        val usable = PlannerCandidateEligibility.candidates(catalog.all(), request)
            .associateBy { it.id }
        val isDeload = request.isDeload

        val days = plan.days.map { day ->
            day.copy(
                exercises = day.exercises.mapNotNull { planned ->
                    val exercise = usable[planned.exerciseId] ?: return@mapNotNull null
                    val sets = setsFor(exercise, request, isDeload)
                    val reps = volumeAwareReps.repsFor(
                        request.goal,
                        exercise.movementPattern.isCompound,
                        sets
                    )
                    val capability = exercise.loadCapability
                    // The model may propose a number, but the app decides its meaning: only external
                    // resistance keeps one. A bodyweight or added-load exercise is sanitized to a
                    // typed no-number prescription without discarding the selection.
                    val suggestedWeightKg =
                        if (WorkoutLoadPolicy.allowsAutomaticLoad(capability)) {
                            planned.suggestedWeightKg
                                ?.takeIf {
                                    request.includeWorkoutData &&
                                        exercise.id !in request.withheldWeightExerciseIds &&
                                        it > 0.0 &&
                                        it <= MAX_SUGGESTED_WEIGHT_KG
                                }
                                ?.let { weight ->
                                    val proposed = weight * if (isDeload) {
                                        periodization.deloadIntensityScale
                                    } else {
                                        1.0
                                    }
                                    val bound = request.recentWeightCaps[exercise.id]?.let {
                                        weightConfig.workingWeightFor(
                                            it,
                                            reps,
                                            if (isDeload) {
                                                periodization.deloadIntensityScale
                                            } else {
                                                1.0
                                            }
                                        )
                                    }
                                    EquipmentWeightLimit.clamp(
                                        if (bound == null) proposed else minOf(proposed, bound),
                                        EquipmentWeightLimit.ceilingFor(
                                            exercise,
                                            request.equipmentMaxWeights
                                        )
                                    )
                                }
                        } else {
                            null
                        }
                    PlannedExercise(
                        exerciseId = exercise.id,
                        sets = sets,
                        reps = reps,
                        suggestedWeightKg = suggestedWeightKg,
                        loadKind = if (suggestedWeightKg != null) {
                            LoadKind.EXTERNAL
                        } else {
                            WorkoutLoadPolicy.defaultKind(capability)
                        },
                        loadCapability = capability
                    )
                }
            )
        }

        if (days.size < request.daysPerWeek) return null
        if (days.any { it.exercises.size < PlannerExerciseCounts.FLOOR_PER_DAY }) return null
        val trimmed = plan.copy(
            days = days.take(request.daysPerWeek)
                .mapIndexed { index, day -> day.copy(dayIndex = index) },
            weekNumber = request.weekNumber,
            cycleNumber = request.cycleNumber
        )
        val enforced = varietyEnforcer.enforce(trimmed, request) { id ->
            usable[id]?.movementPattern?.isCompound ?: true
        } ?: return null
        val focuses = SplitResolver.focusSequence(request.splitPreference, request.daysPerWeek)
        return enforced.copy(
            armCoverage = DirectArmCoverage.assess(
                days = enforced.days,
                exercisesById = usable,
                compatibleCandidatesByMuscle = DirectArmCoverage.compatibleCandidates(
                    usable.values.toList(),
                    focuses
                ),
                fatigue = request.muscleFatigue,
                isDeload = isDeload,
                attribution = PlanAttribution.AI_GENERATED
            )
        )
    }

    private fun setsFor(exercise: Exercise, request: PlanRequest, isDeload: Boolean): Int {
        val baseSets = if (exercise.movementPattern.isCompound) {
            request.setsPerExercise
        } else {
            request.accessorySetsPerExercise
        }
        return if (isDeload) {
            (baseSets * periodization.deloadVolumeScale).roundToInt().coerceAtLeast(1)
        } else {
            baseSets
        }
    }

    companion object {
        const val MAX_SUGGESTED_WEIGHT_KG = 1_000.0
    }
}
