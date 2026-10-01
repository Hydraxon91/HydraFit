package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.FatigueConfig
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import kotlin.math.roundToInt

class DeterministicWorkoutPlannerEngine(
    private val catalog: ExerciseCatalog,
    private val volumeAwareReps: VolumeAwareReps = VolumeAwareReps(),
    private val weightConfig: SuggestedWeightConfig = SuggestedWeightConfig(),
    private val periodization: PeriodizationConfig = PeriodizationConfig()
) : WorkoutPlannerEngine {

    override val id: PlannerEngineId = PlannerEngineId.DETERMINISTIC

    private val fatigueConfig = FatigueConfig()

    override suspend fun generatePlan(request: PlanRequest): WeeklyPlan =
        plan(request, catalog.all())

    internal fun plan(request: PlanRequest, exercises: List<Exercise>): WeeklyPlan {
        require(request.daysPerWeek in MIN_DAYS..MAX_DAYS) {
            "daysPerWeek must be between $MIN_DAYS and $MAX_DAYS"
        }
        require(request.setsPerExercise in MIN_SETS..MAX_SETS) {
            "setsPerExercise must be between $MIN_SETS and $MAX_SETS"
        }

        val focusCycle =
            SplitResolver.focusCycle(
                SplitResolver.resolveSplitType(request.splitPreference, request.daysPerWeek)
            )
        val availableExercises = exercises.filter { it.isAvailableWith(request.availableEquipment) }
        val isDeload = request.isDeload

        val days = List(request.daysPerWeek) { index ->
            val focus = focusCycle[index % focusCycle.size]
            WorkoutDay(
                dayIndex = index,
                focus = focus,
                exercises = selectExercises(
                    templateFor(focus, index),
                    availableExercises,
                    request.muscleFatigue,
                    request.setsPerExercise,
                    request.accessorySetsPerExercise,
                    request.goal,
                    request.recentExerciseIdsByPattern,
                    request.suggestedWeightsKg,
                    request.equipmentMaxWeights,
                    isDeload
                )
            )
        }

        return WeeklyPlan(
            engine = id,
            days = days,
            weekNumber = request.weekNumber,
            cycleNumber = request.cycleNumber
        )
    }

    private fun selectExercises(
        template: List<MovementPattern>,
        exercises: List<Exercise>,
        fatigue: Map<MuscleGroup, Double>,
        setsPerExercise: Int,
        accessorySetsPerExercise: Int,
        goal: TrainingGoal,
        recentExerciseIdsByPattern: Map<MovementPattern, Set<String>>,
        suggestedWeightsKg: Map<String, Double>,
        equipmentMaxWeights: Map<EquipmentTag, Double>,
        isDeload: Boolean
    ): List<PlannedExercise> {
        val used = mutableSetOf<String>()
        val picks = mutableListOf<PlannedExercise>()

        for (pattern in template) {
            val candidate = exercises
                .filter { it.movementPattern == pattern && it.id !in used }
                // Recovery comes first: a fresh less-preferred exercise outranks a sore preferred
                // one. Equipment preference only breaks ties between equally fresh candidates.
                .minWithOrNull(
                    compareBy(
                        { weightedFatigue(it, fatigue) },
                        { it.id in recentExerciseIdsByPattern[pattern].orEmpty() },
                        { equipmentRank(it) },
                        { it.id }
                    )
                )
                ?: continue

            val soreness = targetedFatigue(candidate, fatigue)
            if (soreness >= fatigueConfig.skipThreshold) continue

            val isCompound = candidate.movementPattern.isCompound
            val baseSets = if (isCompound) setsPerExercise else accessorySetsPerExercise
            val deloadedSets = if (isDeload) {
                (baseSets * periodization.deloadVolumeScale).roundToInt().coerceAtLeast(1)
            } else {
                baseSets
            }
            val sets = (deloadedSets - if (soreness >= fatigueConfig.reduceThreshold) 1 else 0)
                .coerceAtLeast(1)
            val reps = volumeAwareReps.repsFor(goal, isCompound, sets)
            used += candidate.id
            picks += PlannedExercise(
                exerciseId = candidate.id,
                sets = sets,
                reps = reps,
                suggestedWeightKg = suggestedWeightsKg[candidate.id]?.let { oneRepMax ->
                    val working = weightConfig.roundToIncrement(
                        oneRepMax * weightConfig.intensityForReps(reps) * intensityScale(isDeload)
                    )
                    EquipmentWeightLimit.clamp(
                        working,
                        EquipmentWeightLimit.ceilingFor(candidate, equipmentMaxWeights)
                    )
                }
            )
        }

        return picks
    }

    private fun intensityScale(isDeload: Boolean): Double =
        if (isDeload) periodization.deloadIntensityScale else 1.0

    /**
     * Candidate ordering score: how sore the exercise leaves you, scaled by how strongly it loads
     * each muscle. A 0.4-weighted stabiliser at high fatigue counts less than a 1.0 primary.
     */
    private fun weightedFatigue(exercise: Exercise, fatigue: Map<MuscleGroup, Double>): Double =
        exercise.effectiveInvolvements.maxOfOrNull { (muscle, weight) ->
            weight * (fatigue[muscle] ?: 0.0)
        } ?: 0.0

    /**
     * The raw fatigue of muscles at or above [FatigueConfig.targetedInvolvementCutoff],
     * used for the skip/reduce decision so a low-weight stabiliser can't veto an exercise.
     */
    private fun targetedFatigue(exercise: Exercise, fatigue: Map<MuscleGroup, Double>): Double =
        exercise.effectiveInvolvements
            .filterValues { it >= fatigueConfig.targetedInvolvementCutoff }
            .keys
            .maxOfOrNull { fatigue[it] ?: 0.0 }
            ?: 0.0

    /** Prefers barbell > dumbbell > machine/kettlebell > band > pull-up bar > bodyweight. */
    private fun equipmentRank(exercise: Exercise): Int = exercise.requiredEquipment
        .filterNot { it == EquipmentTag.BODYWEIGHT }
        .minOfOrNull { EQUIPMENT_RANK[it] ?: CUSTOM_EQUIPMENT_RANK }
        ?: Int.MAX_VALUE

    private fun templateFor(focus: SplitFocus, dayIndex: Int): List<MovementPattern> =
        when (focus) {
            SplitFocus.PUSH -> listOf(
                MovementPattern.HORIZONTAL_PUSH,
                MovementPattern.VERTICAL_PUSH,
                MovementPattern.TRICEPS_ISOLATION,
                MovementPattern.SHOULDER_ISOLATION
            )
            SplitFocus.PULL -> listOf(
                MovementPattern.VERTICAL_PULL,
                MovementPattern.HORIZONTAL_PULL,
                MovementPattern.BICEPS_ISOLATION
            )
            SplitFocus.LEGS -> listOf(
                MovementPattern.SQUAT,
                MovementPattern.HINGE,
                MovementPattern.LEG_ISOLATION,
                MovementPattern.CALF_RAISE,
                MovementPattern.CORE
            )
            SplitFocus.UPPER -> listOf(
                MovementPattern.HORIZONTAL_PUSH,
                MovementPattern.HORIZONTAL_PULL,
                MovementPattern.VERTICAL_PUSH,
                MovementPattern.VERTICAL_PULL,
                MovementPattern.BICEPS_ISOLATION,
                MovementPattern.TRICEPS_ISOLATION
            )
            SplitFocus.LOWER -> listOf(
                MovementPattern.SQUAT,
                MovementPattern.HINGE,
                MovementPattern.LEG_ISOLATION,
                MovementPattern.CALF_RAISE,
                MovementPattern.CORE
            )
            SplitFocus.FULL_BODY -> FULL_BODY_TEMPLATES[dayIndex % FULL_BODY_TEMPLATES.size]
        }

    companion object {
        const val MIN_DAYS = 2
        const val MAX_DAYS = 6
        const val MIN_SETS = 1
        const val MAX_SETS = 8
        const val DEFAULT_SETS = DEFAULT_SETS_PER_EXERCISE
        const val COMPOUND_REPS = 6
        const val ISOLATION_REPS = 12

        /** Any user-added equipment is ranked after the built-ins until it has its own preference. */
        const val CUSTOM_EQUIPMENT_RANK = 20

        private val FULL_BODY_TEMPLATES = listOf(
            listOf(
                MovementPattern.SQUAT,
                MovementPattern.HORIZONTAL_PUSH,
                MovementPattern.HORIZONTAL_PULL,
                MovementPattern.CORE
            ),
            listOf(
                MovementPattern.HINGE,
                MovementPattern.VERTICAL_PUSH,
                MovementPattern.VERTICAL_PULL,
                MovementPattern.CALF_RAISE
            ),
            listOf(
                MovementPattern.LUNGE,
                MovementPattern.HORIZONTAL_PUSH,
                MovementPattern.HORIZONTAL_PULL,
                MovementPattern.BICEPS_ISOLATION
            )
        )

        private val EQUIPMENT_RANK = mapOf(
            EquipmentTag.BARBELL to 0,
            EquipmentTag.DUMBBELL to 1,
            EquipmentTag.CABLE_MACHINE to 2,
            EquipmentTag.KETTLEBELL to 3,
            EquipmentTag.RESISTANCE_BAND to 4,
            EquipmentTag.PULL_UP_BAR to 5,
            EquipmentTag.BENCH to 6,
            EquipmentTag.BODYWEIGHT to 7
        )
    }
}
