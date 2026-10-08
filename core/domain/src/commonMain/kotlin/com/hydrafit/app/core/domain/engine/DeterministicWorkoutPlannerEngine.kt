package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.FatigueConfig
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.WorkoutLoadPolicy
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
        val directArmSets = MuscleGroup.entries.associateWith { 0 }.toMutableMap()
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
                    directArmSets,
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

        val compatibleArmCandidates = DirectArmCoverage.compatibleCandidates(
            availableExercises,
            List(request.daysPerWeek) { index -> focusCycle[index % focusCycle.size] }
        )
        return WeeklyPlan(
            engine = id,
            days = days,
            weekNumber = request.weekNumber,
            cycleNumber = request.cycleNumber,
            armCoverage = DirectArmCoverage.assess(
                days = days,
                exercisesById = availableExercises.associateBy { it.id },
                compatibleCandidatesByMuscle = compatibleArmCandidates,
                fatigue = request.muscleFatigue,
                isDeload = isDeload
            )
        )
    }

    private fun selectExercises(
        focus: SplitFocus,
        exercises: List<Exercise>,
        weekUsed: MutableSet<String>,
        weeklyVolume: MutableMap<MuscleGroup, Double>,
        directArmSets: MutableMap<MuscleGroup, Int>,
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

        fun pick(candidates: List<Exercise>): Boolean {
            // Ordering is by weighted fatigue, but the skip uses raw targeted fatigue, so a lower-
            // ranked candidate can be fresh while the top one is sore. Try candidates in order and
            // take the first that is not sore instead of rejecting the whole group.
            val candidate = pickFirstNonSore(
                rankCandidates(
                    candidates = candidates,
                    fatigue = fatigue,
                    weekUsed = weekUsed,
                    recentExerciseIdsByPattern = recentExerciseIdsByPattern,
                    weeklyVolume = weeklyVolume,
                    target = target
                ),
                fatigue
            ) ?: return false
            val soreness = targetedFatigue(candidate, fatigue)
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
            DirectArmCoverage.muscleFor(candidate)?.let { muscle ->
                directArmSets[muscle] = directArmSets.getValue(muscle) + planned.sets
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

        // Pursue unmet direct arm coverage first, then fill toward TARGET_MIN and chase weighted
        // deficits up to TARGET_MAX.
        val isolationPool = isolationPatterns(focus)
        while (picks.size < PlannerExerciseCounts.TARGET_MAX_PER_DAY) {
            val pastMinimum = picks.size >= PlannerExerciseCounts.TARGET_MIN_PER_DAY
            val candidates = exercises.filter {
                it.movementPattern in isolationPool &&
                    it.id !in used
            }
            val directArmCandidates = if (isDeload) {
                emptyList()
            } else {
                candidates.filter { DirectArmCoverage.needsCoverage(it, directArmSets) }
            }
            if (directArmCandidates.isNotEmpty() && pick(directArmCandidates)) {
                // Dedicated arm coverage takes precedence over the weighted ceiling: compound
                // credits must not make qualifying direct work ineligible.
                continue
            }
            val remaining = candidates.filter { candidate ->
                !DirectArmCoverage.needsCoverage(candidate, directArmSets) &&
                    !isAtMax(candidate, weeklyVolume, target) &&
                    (!pastMinimum || hasDeficit(candidate, weeklyVolume, target))
            }
            if (!pick(remaining)) break
        }

        return picks
    }

    /**
     * Orders candidate exercises for selection: recovery first, then the largest remaining weekly
     * deficit, then freshness within the generated week, previous-plan compound rotation, equipment
     * preference, and finally id for a stable order. Extracted from [selectExercises] so the same
     * ranking can drive substitution of an accepted plan's slot ([SubstituteExerciseUseCase]).
     */
    internal fun rankCandidates(
        candidates: List<Exercise>,
        fatigue: Map<MuscleGroup, Double>,
        weekUsed: Set<String>,
        recentExerciseIdsByPattern: Map<MovementPattern, Set<String>>,
        weeklyVolume: Map<MuscleGroup, Double>,
        target: VolumeTarget
    ): List<Exercise> {
        val comparator = compareBy<Exercise>(
            { weightedFatigue(it, fatigue) },
            { -deficitScore(it, weeklyVolume, target) },
            { it.id in weekUsed },
            { it.id in recentExerciseIdsByPattern[it.movementPattern].orEmpty() },
            { equipmentRank(it) },
            { it.id }
        )
        return candidates.sortedWith(comparator)
    }

    /**
     * The first ranked candidate that is not sore. Ordering is by weighted fatigue while the skip
     * uses raw targeted fatigue, so a lower-ranked candidate can be fresh while the top one is sore.
     */
    internal fun pickFirstNonSore(
        ranked: List<Exercise>,
        fatigue: Map<MuscleGroup, Double>
    ): Exercise? = ranked.firstOrNull {
        targetedFatigue(it, fatigue) < fatigueConfig.skipThreshold
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
        val capability = candidate.loadCapability
        // A numeric working weight is only meaningful for external resistance. A bodyweight or
        // added-load exercise is prescribed without a generated number, even if a stale baseline
        // exists for its id.
        val suggestedWeightKg = if (WorkoutLoadPolicy.allowsAutomaticLoad(capability)) {
            suggestedWeightsKg[candidate.id]?.let { oneRepMax ->
                val working = weightConfig.roundToIncrement(
                    oneRepMax * weightConfig.intensityForReps(reps) * intensityScale(isDeload)
                )
                EquipmentWeightLimit.clamp(
                    working,
                    EquipmentWeightLimit.ceilingFor(candidate, equipmentMaxWeights)
                )
            }
        } else {
            null
        }
        return PlannedExercise(
            exerciseId = candidate.id,
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

    /**
     * Involvements that drive weekly-volume deficit targeting. TRAPS is excluded: the trapezius
     * shares most work with UPPER_BACK, so giving it its own deficit window would double-count the
     * same sets. It still contributes to fatigue and soreness (see [weightedFatigue]); only the
     * weekly volume ledger ignores it, until a dedicated policy is agreed (VOL-01).
     */
    private fun Exercise.plannerInvolvements(): Map<MuscleGroup, Double> =
        effectiveInvolvements.filterKeys { it !in PLANNER_DEFERRED_MUSCLES }

    /** The largest weighted shortfall among the muscles this exercise trains below their target. */
    private fun deficitScore(
        exercise: Exercise,
        weeklyVolume: Map<MuscleGroup, Double>,
        target: VolumeTarget
    ): Double = exercise.plannerInvolvements()
        .filter { (muscle, _) -> (weeklyVolume[muscle] ?: 0.0) < target.targetSets }
        .maxOfOrNull { (muscle, weight) ->
            (target.targetSets - weeklyVolume.getValue(muscle)) * weight
        }
        ?: 0.0

    private fun hasDeficit(
        exercise: Exercise,
        weeklyVolume: Map<MuscleGroup, Double>,
        target: VolumeTarget
    ): Boolean = exercise.plannerInvolvements().any { (muscle, _) ->
        (weeklyVolume[muscle] ?: 0.0) < target.targetSets
    }

    /** True when every muscle this exercise trains is already at or beyond its weekly ceiling. */
    private fun isAtMax(
        exercise: Exercise,
        weeklyVolume: Map<MuscleGroup, Double>,
        target: VolumeTarget
    ): Boolean {
        val targeted = exercise.plannerInvolvements()
        return targeted.isNotEmpty() &&
            targeted.all { (muscle, _) ->
                (weeklyVolume[muscle] ?: 0.0) >= target.maxSets
            }
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
        DirectArmCoverage.targetedFatigue(exercise, fatigue, fatigueConfig)

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
    private fun isolationPatterns(focus: SplitFocus): List<MovementPattern> =
        DirectArmCoverage.isolationPatternsFor(focus)

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

        /** Muscles excluded from weekly-volume deficit targeting (still counted for fatigue). */
        private val PLANNER_DEFERRED_MUSCLES = setOf(MuscleGroup.TRAPS)

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
