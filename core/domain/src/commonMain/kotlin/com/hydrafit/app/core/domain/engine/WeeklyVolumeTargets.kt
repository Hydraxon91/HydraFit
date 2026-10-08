package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.fatigue.MuscleGroup

/**
 * A muscle's weekly volume window, in involvement-weighted sets. These are soft planning
 * heuristics, not validated physiological thresholds.
 *
 * [targetSets] is the goal the selector chases. [maxSets] is a **soft** ceiling: ordinary selection
 * stops offering an exercise only once *every* muscle it targets is already at it, and whole-slot
 * additions may overshoot; the direct-arm coverage priority bypasses it. [minSets] is currently
 * **report-only** — selection keys on [targetSets], so [minSets] is not enforced and any consumer
 * (e.g. the volume-explanation slice) should treat it as an informational threshold.
 */
data class VolumeTarget(val minSets: Double, val targetSets: Double, val maxSets: Double) {
    init {
        require(minSets >= 0.0) { "minSets must not be negative" }
        require(targetSets >= minSets) { "targetSets must be at least minSets" }
        require(maxSets >= targetSets) { "maxSets must be at least targetSets" }
    }
}

/**
 * Weekly volume targets per [TrainingGoal]. The table is uniform across muscles: a weighted set
 * counts once per unit of involvement, so a compound's secondary contributions already raise its
 * helpers' totals. Muscles that the catalog or the split cannot supply stay below target on
 * purpose — an unfilled deficit is not an error. The values are product defaults for broad goals;
 * they are not established per-region optima and the same window is applied to every muscle.
 */
object WeeklyVolumeTargets {

    fun forGoal(goal: TrainingGoal): VolumeTarget = when (goal) {
        TrainingGoal.BALANCED -> VolumeTarget(minSets = 8.0, targetSets = 11.0, maxSets = 18.0)
        TrainingGoal.STRENGTH -> VolumeTarget(minSets = 6.0, targetSets = 9.0, maxSets = 15.0)
        TrainingGoal.HYPERTROPHY -> VolumeTarget(minSets = 8.0, targetSets = 13.0, maxSets = 20.0)
        TrainingGoal.ENDURANCE -> VolumeTarget(minSets = 6.0, targetSets = 9.0, maxSets = 16.0)
    }

    /**
     * Effective weighted sets per muscle across [days]: each planned set contributes its exercise's
     * involvement weight to every muscle it trains. Muscles with no work are present with `0.0`.
     */
    fun weightedSetsByMuscle(
        days: List<WorkoutDay>,
        exercisesById: Map<String, Exercise>
    ): Map<MuscleGroup, Double> {
        val totals = MuscleGroup.entries.associateWith { 0.0 }.toMutableMap()
        days.forEach { day ->
            day.exercises.forEach { planned ->
                val exercise = exercisesById[planned.exerciseId] ?: return@forEach
                exercise.effectiveInvolvements.forEach { (muscle, weight) ->
                    totals[muscle] = totals.getValue(muscle) + planned.sets * weight
                }
            }
        }
        return totals
    }
}
