package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.FatigueConfig
import com.hydrafit.app.core.domain.fatigue.MuscleGroup

enum class ArmCoverageUnmetReason {
    NO_COMPATIBLE_AVAILABLE_CANDIDATE,
    ALL_COMPATIBLE_CANDIDATES_SKIPPED_FOR_FATIGUE,
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
    const val TARGET_SETS_PER_MUSCLE = 4

    private val armPatterns = mapOf(
        MuscleGroup.BICEPS to MovementPattern.BICEPS_ISOLATION,
        MuscleGroup.TRICEPS to MovementPattern.TRICEPS_ISOLATION
    )

    fun qualifies(exercise: Exercise, muscle: MuscleGroup): Boolean =
        exercise.movementPattern == armPatterns[muscle] &&
            (exercise.effectiveInvolvements[muscle] ?: 0.0) > 0.0

    fun muscleFor(exercise: Exercise): MuscleGroup? = armPatterns.keys
        .firstOrNull { muscle -> qualifies(exercise, muscle) }

    fun needsCoverage(exercise: Exercise, directSetsByMuscle: Map<MuscleGroup, Int>): Boolean =
        muscleFor(exercise)?.let { muscle ->
            (directSetsByMuscle[muscle] ?: 0) < TARGET_SETS_PER_MUSCLE
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

    fun assess(
        days: List<WorkoutDay>,
        exercisesById: Map<String, Exercise>,
        compatibleCandidatesByMuscle: Map<MuscleGroup, List<Exercise>>,
        fatigue: Map<MuscleGroup, Double>,
        isDeload: Boolean
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
            directSets >= TARGET_SETS_PER_MUSCLE -> null
            compatibleCandidatesByMuscle[muscle].orEmpty().isEmpty() ->
                ArmCoverageUnmetReason.NO_COMPATIBLE_AVAILABLE_CANDIDATE
            compatibleCandidatesByMuscle.getValue(muscle).all {
                isSkippedForFatigue(it, fatigue)
            } ->
                ArmCoverageUnmetReason.ALL_COMPATIBLE_CANDIDATES_SKIPPED_FOR_FATIGUE
            else -> ArmCoverageUnmetReason.NOT_MET_WITH_AVAILABLE_CANDIDATES
        }
        ArmMuscleCoverage(
            muscle = muscle,
            targetSets = TARGET_SETS_PER_MUSCLE,
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
