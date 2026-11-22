package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.FatigueConfig
import com.hydrafit.app.core.domain.fatigue.MuscleGroup

enum class ArmCoverageUnmetReason {
    NO_COMPATIBLE_AVAILABLE_CANDIDATE,
    ALL_COMPATIBLE_CANDIDATES_SKIPPED_FOR_FATIGUE,
    COMPATIBLE_CANDIDATES_ABOVE_SORENESS_THRESHOLD,
    NOT_MET_WITH_AVAILABLE_CANDIDATES
}

data class ArmMuscleCoverage(
    val muscle: MuscleGroup,
    val targetSets: Int,
    val isTargetEnforced: Boolean,
    val directIsolationSets: Int,
    val estimatedOtherInvolvementCredits: Double,
    val unmetReason: ArmCoverageUnmetReason?
)

/** Coverage accounting for dedicated arm work and separate estimated contributions from other work. */
object DirectArmCoverage {
    /** The lowest weekly direct-set target: a two-day week, and the Endurance goal at any frequency. */
    const val BASE_TARGET_SETS_PER_MUSCLE = 4

    /**
     * The weekly direct isolation sets targeted for each of biceps and triceps. It grows with training
     * frequency, since more days leave more accessory slots: [BASE_TARGET_SETS_PER_MUSCLE] for one or
     * two days, 6 for three or four, and 8 for five or six. Endurance keeps the base target at any
     * frequency because its volume is deliberately lighter. Both arms share one number so neither
     * is favoured.
     */
    fun targetSetsPerMuscle(daysPerWeek: Int, goal: TrainingGoal): Int = when {
        goal == TrainingGoal.ENDURANCE || daysPerWeek <= 2 -> BASE_TARGET_SETS_PER_MUSCLE
        daysPerWeek <= 4 -> 6
        else -> 8
    }

    /**
     * Sets for one direct arm exercise: the selected accessory count, raised only as far as the split
     * needs to reach [targetSets]. Upper and full-body days share their few accessory slots between
     * both arms, so they hold one exercise per arm; a push or pull day trains one arm and has room for
     * two. A 4-day upper/lower week (two upper days, 6 target sets) therefore gets 3 sets per arm
     * exercise at an accessory count of 2, while a push/pull/legs week keeps the selected count and
     * adds exercises instead. A higher selected count is never lowered.
     */
    fun setsPerArmExercise(
        muscle: MuscleGroup,
        focuses: List<SplitFocus>,
        targetSets: Int,
        accessorySets: Int
    ): Int {
        val pattern = armPatterns[muscle] ?: return accessorySets
        val slots = focuses.sumOf { focus ->
            if (pattern !in isolationPatternsFor(focus)) {
                0
            } else if (focus == SplitFocus.PUSH || focus == SplitFocus.PULL) {
                SINGLE_ARM_DAY_SLOTS
            } else {
                SHARED_ARM_DAY_SLOTS
            }
        }
        if (slots == 0) return accessorySets
        return maxOf(accessorySets, (targetSets + slots - 1) / slots)
    }

    private const val SINGLE_ARM_DAY_SLOTS = 2
    private const val SHARED_ARM_DAY_SLOTS = 1

    private val armPatterns = mapOf(
        MuscleGroup.BICEPS to MovementPattern.BICEPS_ISOLATION,
        MuscleGroup.TRICEPS to MovementPattern.TRICEPS_ISOLATION
    )

    fun qualifies(exercise: Exercise, muscle: MuscleGroup): Boolean =
        exercise.movementPattern == armPatterns[muscle] &&
            (exercise.effectiveInvolvements[muscle] ?: 0.0) > 0.0

    fun muscleFor(exercise: Exercise): MuscleGroup? = armPatterns.keys
        .firstOrNull { muscle -> qualifies(exercise, muscle) }

    fun needsCoverage(
        exercise: Exercise,
        directSetsByMuscle: Map<MuscleGroup, Int>,
        targetSets: Int
    ): Boolean = muscleFor(exercise)?.let { muscle ->
        (directSetsByMuscle[muscle] ?: 0) < targetSets
    } == true

    fun compatibleCandidates(
        availableExercises: List<Exercise>,
        focuses: List<SplitFocus>
    ): Map<MuscleGroup, List<Exercise>> = armPatterns.mapValues { (muscle, pattern) ->
        availableExercises.filter { exercise ->
            qualifies(exercise, muscle) &&
                focuses.any { focus -> pattern in isolationPatternsFor(focus) }
        }
    }

    fun isolationPatternsFor(focus: SplitFocus): List<MovementPattern> = when (focus) {
        SplitFocus.PUSH -> listOf(
            MovementPattern.TRICEPS_ISOLATION,
            MovementPattern.SHOULDER_ISOLATION,
            MovementPattern.CHEST_FLY
        )
        SplitFocus.PULL -> listOf(
            MovementPattern.BICEPS_ISOLATION,
            MovementPattern.FOREARM_ISOLATION,
            // Rear-delt work belongs to a pull day, and without it a second pull day could only
            // repeat arm and forearm exercises.
            MovementPattern.SHOULDER_ISOLATION
        )
        SplitFocus.LEGS, SplitFocus.LOWER -> listOf(
            MovementPattern.LEG_ISOLATION,
            MovementPattern.CALF_RAISE,
            MovementPattern.CORE
        )
        SplitFocus.UPPER -> listOf(
            MovementPattern.BICEPS_ISOLATION,
            MovementPattern.TRICEPS_ISOLATION,
            // An upper/lower week has no push day, so chest flies belong on upper days too.
            MovementPattern.CHEST_FLY,
            MovementPattern.FOREARM_ISOLATION,
            MovementPattern.SHOULDER_ISOLATION
        )
        SplitFocus.FULL_BODY -> listOf(
            MovementPattern.BICEPS_ISOLATION,
            MovementPattern.TRICEPS_ISOLATION,
            MovementPattern.FOREARM_ISOLATION,
            MovementPattern.SHOULDER_ISOLATION,
            MovementPattern.CHEST_FLY,
            MovementPattern.LEG_ISOLATION,
            MovementPattern.CALF_RAISE,
            MovementPattern.CORE
        )
    }

    fun assess(
        days: List<WorkoutDay>,
        exercisesById: Map<String, Exercise>,
        compatibleCandidatesByMuscle: Map<MuscleGroup, List<Exercise>>,
        fatigue: Map<MuscleGroup, Double>,
        isDeload: Boolean,
        targetSets: Int,
        attribution: PlanAttribution = PlanAttribution.DETERMINISTIC,
        skippedCandidateIds: Set<String> = emptySet()
    ): List<ArmMuscleCoverage> = armPatterns.map { (muscle, _) ->
        var directSets = 0
        var otherCredits = 0.0
        for (day in days) {
            for (planned in day.exercises) {
                val exercise = exercisesById[planned.exerciseId] ?: continue
                val involvement = exercise.effectiveInvolvements[muscle] ?: continue
                if (qualifies(exercise, muscle)) {
                    directSets += planned.sets
                } else {
                    otherCredits += planned.sets * involvement
                }
            }
        }
        val targetEnforced = !isDeload
        val unmetReason = when {
            !targetEnforced -> null
            directSets >= targetSets -> null
            compatibleCandidatesByMuscle[muscle].orEmpty().isEmpty() ->
                ArmCoverageUnmetReason.NO_COMPATIBLE_AVAILABLE_CANDIDATE
            compatibleCandidatesByMuscle.getValue(muscle).all {
                isSkippedForFatigue(it, fatigue)
            } -> {
                if (attribution == PlanAttribution.DETERMINISTIC &&
                    compatibleCandidatesByMuscle.getValue(muscle).all {
                        it.id in skippedCandidateIds
                    }
                ) {
                    ArmCoverageUnmetReason.ALL_COMPATIBLE_CANDIDATES_SKIPPED_FOR_FATIGUE
                } else {
                    ArmCoverageUnmetReason.COMPATIBLE_CANDIDATES_ABOVE_SORENESS_THRESHOLD
                }
            }
            else -> ArmCoverageUnmetReason.NOT_MET_WITH_AVAILABLE_CANDIDATES
        }
        ArmMuscleCoverage(
            muscle = muscle,
            targetSets = targetSets,
            isTargetEnforced = targetEnforced,
            directIsolationSets = directSets,
            estimatedOtherInvolvementCredits = otherCredits,
            unmetReason = unmetReason
        )
    }

    fun targetedFatigue(
        exercise: Exercise,
        fatigue: Map<MuscleGroup, Double>,
        config: FatigueConfig = FatigueConfig()
    ): Double = exercise.effectiveInvolvements
        .filterValues { it >= config.targetedInvolvementCutoff }
        .keys
        .maxOfOrNull { fatigue[it] ?: 0.0 }
        ?: 0.0

    private fun isSkippedForFatigue(
        exercise: Exercise,
        fatigue: Map<MuscleGroup, Double>
    ): Boolean = targetedFatigue(exercise, fatigue) >= FatigueConfig().skipThreshold
}
