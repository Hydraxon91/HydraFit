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

    /**
     * The window for [goal]. A deload scales it by [volumeScale] (the deload set-count scale) so the
     * week-long ledger, which counts the already reduced sets, is measured against a matching
     * target; otherwise a deload would chase the full deficit and pad every day with extra exercises.
     */
    fun forGoal(goal: TrainingGoal, volumeScale: Double = 1.0): VolumeTarget {
        require(volumeScale > 0.0 && volumeScale <= 1.0) { "volumeScale must be in (0.0, 1.0]" }
        val base = when (goal) {
            TrainingGoal.BALANCED -> VolumeTarget(minSets = 8.0, targetSets = 11.0, maxSets = 18.0)
            TrainingGoal.STRENGTH -> VolumeTarget(minSets = 6.0, targetSets = 9.0, maxSets = 15.0)
            TrainingGoal.HYPERTROPHY ->
                VolumeTarget(minSets = 8.0, targetSets = 13.0, maxSets = 20.0)
            TrainingGoal.ENDURANCE -> VolumeTarget(minSets = 6.0, targetSets = 9.0, maxSets = 16.0)
        }
        return if (volumeScale == 1.0) {
            base
        } else {
            VolumeTarget(
                minSets = base.minSets * volumeScale,
                targetSets = base.targetSets * volumeScale,
                maxSets = base.maxSets * volumeScale
            )
        }
    }

    /**
     * A muscle's share of the goal window from [forGoal], or null when the muscle has no weekly
     * target of its own (traps only). Large muscles get the full window; smaller ones a fraction,
     * because the compound lifts already credit them and a uniform window left them permanently
     * under target, which kept every day filled to the exercise cap. A muscle without a target still
     * counts for fatigue and appears in the weekly totals; it just never justifies adding a slot.
     */
    fun sizeFactor(muscle: MuscleGroup): Double? = when (muscle) {
        MuscleGroup.CHEST_UPPER,
        MuscleGroup.CHEST_LOWER,
        MuscleGroup.LATS,
        MuscleGroup.UPPER_BACK,
        MuscleGroup.QUADS,
        MuscleGroup.HAMSTRINGS,
        MuscleGroup.GLUTES -> LARGE_MUSCLE_FACTOR
        MuscleGroup.SIDE_DELTS,
        MuscleGroup.REAR_DELTS,
        MuscleGroup.BICEPS,
        MuscleGroup.TRICEPS,
        MuscleGroup.CALVES -> MEDIUM_MUSCLE_FACTOR
        MuscleGroup.FRONT_DELTS,
        MuscleGroup.ABS,
        MuscleGroup.LOWER_BACK,
        MuscleGroup.FOREARMS,
        MuscleGroup.OBLIQUES,
        MuscleGroup.ADDUCTORS,
        MuscleGroup.HIP_ABDUCTORS,
        MuscleGroup.NECK,
        MuscleGroup.TIBIALIS_ANTERIOR,
        MuscleGroup.SERRATUS_ANTERIOR,
        MuscleGroup.HIP_FLEXORS -> SMALL_MUSCLE_FACTOR
        // Traps share their work with UPPER_BACK and stay out of deficit targeting (VOL-01).
        MuscleGroup.TRAPS -> null
    }

    /** [base] scaled to [muscle] by [sizeFactor]; null when the muscle has no weekly target. */
    fun forMuscle(base: VolumeTarget, muscle: MuscleGroup): VolumeTarget? =
        sizeFactor(muscle)?.let { factor ->
            VolumeTarget(
                minSets = base.minSets * factor,
                targetSets = base.targetSets * factor,
                maxSets = base.maxSets * factor
            )
        }

    const val LARGE_MUSCLE_FACTOR = 1.0
    const val MEDIUM_MUSCLE_FACTOR = 0.7
    const val SMALL_MUSCLE_FACTOR = 0.5

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
