package com.hydrafit.app.core.domain.equipment

import com.hydrafit.app.core.domain.fatigue.MuscleGroup

/**
 * Advisory check that a chosen [MovementPattern] matches an exercise's involvement profile. It never
 * blocks saving; it only lets the editor suggest a more likely pattern, because the deterministic
 * planner trusts the stored pattern when it selects exercises.
 */
object MovementPatternGuardrail {

    /** A muscle at or above this weight counts as a primary target of a pattern. */
    const val PRIMARY_THRESHOLD = 0.7

    /** The muscles a pattern is expected to load as a primary target. */
    fun expectedMusclesFor(pattern: MovementPattern): Set<MuscleGroup> = when (pattern) {
        MovementPattern.HORIZONTAL_PUSH -> setOf(
            MuscleGroup.CHEST,
            MuscleGroup.SHOULDERS,
            MuscleGroup.TRICEPS
        )
        MovementPattern.VERTICAL_PUSH -> setOf(
            MuscleGroup.SHOULDERS,
            MuscleGroup.CHEST,
            MuscleGroup.TRICEPS
        )
        MovementPattern.CHEST_FLY -> setOf(MuscleGroup.CHEST)
        MovementPattern.HORIZONTAL_PULL -> setOf(MuscleGroup.BACK, MuscleGroup.BICEPS)
        MovementPattern.VERTICAL_PULL -> setOf(MuscleGroup.BACK, MuscleGroup.BICEPS)
        MovementPattern.SQUAT -> setOf(MuscleGroup.QUADS, MuscleGroup.GLUTES)
        MovementPattern.LUNGE -> setOf(MuscleGroup.QUADS, MuscleGroup.GLUTES)
        MovementPattern.HINGE -> setOf(
            MuscleGroup.HAMSTRINGS,
            MuscleGroup.GLUTES,
            MuscleGroup.BACK
        )
        MovementPattern.BICEPS_ISOLATION -> setOf(MuscleGroup.BICEPS)
        MovementPattern.TRICEPS_ISOLATION -> setOf(MuscleGroup.TRICEPS)
        MovementPattern.SHOULDER_ISOLATION -> setOf(MuscleGroup.SHOULDERS)
        MovementPattern.LEG_ISOLATION -> setOf(
            MuscleGroup.QUADS,
            MuscleGroup.HAMSTRINGS,
            MuscleGroup.GLUTES
        )
        MovementPattern.CALF_RAISE -> setOf(MuscleGroup.CALVES)
        MovementPattern.CORE -> setOf(MuscleGroup.CORE)
    }

    /**
     * True when none of the pattern's expected muscles is a primary target
     * (>= [PRIMARY_THRESHOLD]) of the profile. An empty profile is not flagged.
     */
    fun conflicts(pattern: MovementPattern, involvements: Map<MuscleGroup, Double>): Boolean {
        if (involvements.isEmpty()) return false
        return expectedMusclesFor(pattern).none { muscle ->
            (involvements[muscle] ?: 0.0) >= PRIMARY_THRESHOLD
        }
    }
}
