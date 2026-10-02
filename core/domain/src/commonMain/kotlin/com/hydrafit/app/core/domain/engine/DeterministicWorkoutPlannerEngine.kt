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
        // Exercises already chosen earlier in the week; compounds are never repeated across days,
        // while accessories merely prefer a fresh option when one exists.
        val weekUsed = mutableSetOf<String>()
        // Running involvement-weighted sets per muscle, accumulated across the week so each day can
        // chase the largest remaining volume deficit.
        val weeklyVolume = MuscleGroup.entries.associateWith { 0.0 }.toMutableMap()
        val target = WeeklyVolumeTargets.forGoal(request.goal)

        val days = List(request.daysPerWeek) { index ->
            val focus = focusCycle[index % focusCycle.size]
            WorkoutDay(
                dayIndex = index,
                focus = focus,
                exercises = selectExercises(
                    focus,
                    availableExercises,
                    weekUsed,
                    weeklyVolume,
                    target,
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
        focus: SplitFocus,
        exercises: List<Exercise>,
        weekUsed: MutableSet<String>,
        weeklyVolume: MutableMap<MuscleGroup, Double>,
        target: VolumeTarget,
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

        // Recovery first, then the largest remaining weekly deficit, then freshness, rotation,
        // equipment preference, and finally id for a stable order.
        val comparator = compareBy<Exercise>(
            { weightedFatigue(it, fatigue) },
            { -deficitScore(it, weeklyVolume, target) },
            { it.id in weekUsed },
            { it.id in recentExerciseIdsByPattern[it.movementPattern].orEmpty() },
            { equipmentRank(it) },
            { it.id }
        )

        fun pick(candidates: List<Exercise>): Boolean {
            val candidate = candidates.minWithOrNull(comparator) ?: return false
            val soreness = targetedFatigue(candidate, fatigue)
            if (soreness >= fatigueConfig.skipThreshold) return false
            val planned = plannedExercise(
                candidate,
                soreness,
                setsPerExercise,
                accessorySetsPerExercise,
                goal,
                suggestedWeightsKg,
                equipmentMaxWeights,
                isDeload
            )
            used += candidate.id
            weekUsed += candidate.id
            picks += planned
            candidate.effectiveInvolvements.forEach { (muscle, weight) ->
                weeklyVolume[muscle] = weeklyVolume.getValue(muscle) + planned.sets * weight
            }
            return true
        }

        // One compound for each major pattern in the focus, chosen by the largest remaining deficit.
        compoundGroups(focus).forEach { group ->
            if (picks.size >= PlannerExerciseCounts.TARGET_MAX_PER_DAY) return@forEach
            pick(
                exercises.filter {
                    it.movementPattern in group &&
                        it.id !in used &&
                        it.id !in weekUsed &&
                        !isAtMax(it, weeklyVolume, target)
                }
            )
        }

        // Fill isolation slots by deficit: always toward TARGET_MIN, then only while a muscle is
        // still below its target, capped at TARGET_MAX.
        val isolationPool = isolationPatterns(focus)
        while (picks.size < PlannerExerciseCounts.TARGET_MAX_PER_DAY) {
            val pastMinimum = picks.size >= PlannerExerciseCounts.TARGET_MIN_PER_DAY
            val candidates = exercises.filter {
                it.movementPattern in isolationPool &&
                    it.id !in used &&
                    !isAtMax(it, weeklyVolume, target) &&
                    (!pastMinimum || hasDeficit(it, weeklyVolume, target))
            }
            if (!pick(candidates)) break
        }

        return picks
    }

    private fun plannedExercise(
        candidate: Exercise,
        soreness: Double,
        setsPerExercise: Int,
        accessorySetsPerExercise: Int,
        goal: TrainingGoal,
        suggestedWeightsKg: Map<String, Double>,
        equipmentMaxWeights: Map<EquipmentTag, Double>,
        isDeload: Boolean
    ): PlannedExercise {
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
        return PlannedExercise(
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

    /** The largest weighted shortfall among the muscles this exercise trains below their target. */
    private fun deficitScore(
        exercise: Exercise,
        weeklyVolume: Map<MuscleGroup, Double>,
        target: VolumeTarget
    ): Double = exercise.effectiveInvolvements
        .filter { (muscle, _) -> (weeklyVolume[muscle] ?: 0.0) < target.targetSets }
        .maxOfOrNull { (muscle, weight) ->
            (target.targetSets - weeklyVolume.getValue(muscle)) * weight
        }
        ?: 0.0

    private fun hasDeficit(
        exercise: Exercise,
        weeklyVolume: Map<MuscleGroup, Double>,
        target: VolumeTarget
    ): Boolean = exercise.effectiveInvolvements.any { (muscle, _) ->
        (weeklyVolume[muscle] ?: 0.0) < target.targetSets
    }

    /** True when every muscle this exercise trains is already at or beyond its weekly ceiling. */
    private fun isAtMax(
        exercise: Exercise,
        weeklyVolume: Map<MuscleGroup, Double>,
        target: VolumeTarget
    ): Boolean = exercise.effectiveInvolvements.isNotEmpty() &&
        exercise.effectiveInvolvements.all { (muscle, _) ->
            (weeklyVolume[muscle] ?: 0.0) >= target.maxSets
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

    /**
     * The compound slots a focus fills, grouped so one exercise is chosen per group. Each group is a
     * movement family; the highest-deficit pattern inside it wins. FULL_BODY groups lower/push/pull
     * so every day stays full-body while still chasing the week's volume deficits.
     */
    private fun compoundGroups(focus: SplitFocus): List<List<MovementPattern>> = when (focus) {
        SplitFocus.PUSH -> listOf(
            listOf(MovementPattern.HORIZONTAL_PUSH),
            listOf(MovementPattern.VERTICAL_PUSH)
        )
        SplitFocus.PULL -> listOf(
            listOf(MovementPattern.VERTICAL_PULL),
            listOf(MovementPattern.HORIZONTAL_PULL)
        )
        SplitFocus.LEGS, SplitFocus.LOWER -> listOf(
            listOf(MovementPattern.SQUAT),
            listOf(MovementPattern.HINGE)
        )
        SplitFocus.UPPER -> listOf(
            listOf(MovementPattern.HORIZONTAL_PUSH),
            listOf(MovementPattern.VERTICAL_PUSH),
            listOf(MovementPattern.HORIZONTAL_PULL),
            listOf(MovementPattern.VERTICAL_PULL)
        )
        SplitFocus.FULL_BODY -> listOf(
            listOf(MovementPattern.SQUAT, MovementPattern.HINGE, MovementPattern.LUNGE),
            listOf(MovementPattern.HORIZONTAL_PUSH, MovementPattern.VERTICAL_PUSH),
            listOf(MovementPattern.HORIZONTAL_PULL, MovementPattern.VERTICAL_PULL)
        )
    }

    /** The isolation families a focus fills after its compounds, by largest remaining deficit. */
    private fun isolationPatterns(focus: SplitFocus): List<MovementPattern> = when (focus) {
        SplitFocus.PUSH -> listOf(
            MovementPattern.TRICEPS_ISOLATION,
            MovementPattern.SHOULDER_ISOLATION
        )
        SplitFocus.PULL -> listOf(MovementPattern.BICEPS_ISOLATION)
        SplitFocus.LEGS, SplitFocus.LOWER -> listOf(
            MovementPattern.LEG_ISOLATION,
            MovementPattern.CALF_RAISE,
            MovementPattern.CORE
        )
        SplitFocus.UPPER -> listOf(
            MovementPattern.BICEPS_ISOLATION,
            MovementPattern.TRICEPS_ISOLATION
        )
        SplitFocus.FULL_BODY -> listOf(
            MovementPattern.BICEPS_ISOLATION,
            MovementPattern.TRICEPS_ISOLATION,
            MovementPattern.SHOULDER_ISOLATION,
            MovementPattern.LEG_ISOLATION,
            MovementPattern.CALF_RAISE,
            MovementPattern.CORE
        )
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
