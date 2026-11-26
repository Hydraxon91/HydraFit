package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.fatigue.MuscleGroup

/**
 * A muscle's weekly volume window, in involvement-weighted sets. These are soft planning
 * heuristics, not validated physiological thresholds.
 *
 * [targetSets] is the goal the selector chases. [maxSets] is a **soft** ceiling: ordinary selection
 * stops offering an exercise only once *every* muscle it targets is already at it, and whole-slot
 * additions may overshoot; the direct-arm coverage priority bypasses it. [minSets] is the weekly
 * floor for the [WeeklyVolumeTargets.FLOOR_MUSCLES] prime movers: selection fills one that is below
 * its (paced) floor before the shrug or ordinary accessories. For every other muscle it stays an
 * informational threshold, since selection keys on [targetSets].
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
     * target of its own (traps and forearms). The shares come from [BALANCED_TARGET_SETS], each muscle's
     * weekly target on the Balanced goal divided by Balanced's base target, so every goal keeps the
     * same proportions. A uniform window left smaller muscles permanently under target, which kept
     * every day filled to the exercise cap. A muscle without a target still counts for fatigue and
     * appears in the weekly totals; it just never justifies adding a slot.
     */
    fun sizeFactor(muscle: MuscleGroup): Double? =
        BALANCED_TARGET_SETS[muscle]?.let { it / BALANCED_BASE_TARGET_SETS }

    /**
     * Weekly involvement-weighted set targets on the Balanced goal. They follow widely used coaching
     * volume landmarks and the hypertrophy meta-analyses' finding that about ten or more weekly sets
     * per muscle grows it more than fewer: the large prime movers sit above ten, the smaller muscles
     * near eight, and muscles that presses, rows and hinges already train heavily (front delts, lower
     * back) or that rarely need direct work low. These are sourced product defaults, not validated
     * per-person optima; see `docs/planner-deterministic.md`. Traps are left out on purpose (VOL-01), and so are forearms: every pull, press and curl already
     * loads the grip, the catalog credits none of it, and a target would only chase a false deficit.
     * Wrist work stays in the library to be logged or picked by hand. The chest is split into two
     * regions with a window each, and most presses and flies credit both, so each region sits at ten
     * rather than twelve: two twelves asked for about five chest exercises a week on two upper days.
     */
    val BALANCED_TARGET_SETS: Map<MuscleGroup, Double> = mapOf(
        MuscleGroup.CHEST_UPPER to 10.0,
        MuscleGroup.CHEST_LOWER to 10.0,
        MuscleGroup.LATS to 12.0,
        MuscleGroup.UPPER_BACK to 12.0,
        MuscleGroup.QUADS to 12.0,
        MuscleGroup.HAMSTRINGS to 10.0,
        MuscleGroup.SIDE_DELTS to 10.0,
        MuscleGroup.GLUTES to 8.0,
        MuscleGroup.BICEPS to 8.0,
        MuscleGroup.TRICEPS to 8.0,
        MuscleGroup.CALVES to 8.0,
        MuscleGroup.REAR_DELTS to 8.0,
        MuscleGroup.FRONT_DELTS to 4.0,
        MuscleGroup.ABS to 4.0,
        MuscleGroup.LOWER_BACK to 4.0,
        MuscleGroup.OBLIQUES to 4.0,
        MuscleGroup.ADDUCTORS to 4.0,
        MuscleGroup.HIP_ABDUCTORS to 4.0,
        MuscleGroup.NECK to 4.0,
        MuscleGroup.TIBIALIS_ANTERIOR to 4.0,
        MuscleGroup.SERRATUS_ANTERIOR to 4.0,
        MuscleGroup.HIP_FLEXORS to 4.0
    )

    /**
     * The prime movers that get a guaranteed weekly floor: the planner fills any of them still below
     * its [VolumeTarget.minSets] before arm overflow, the shrug or ordinary accessories. Biceps and
     * triceps keep their own direct-set guarantee ([DirectArmCoverage]); smaller muscles keep their
     * soft targets only.
     */
    val FLOOR_MUSCLES: Set<MuscleGroup> = setOf(
        MuscleGroup.CHEST_UPPER,
        MuscleGroup.CHEST_LOWER,
        MuscleGroup.LATS,
        MuscleGroup.UPPER_BACK,
        MuscleGroup.QUADS,
        MuscleGroup.HAMSTRINGS,
        MuscleGroup.GLUTES,
        MuscleGroup.SIDE_DELTS
    )

    /**
     * Weighted sets each [FLOOR_MUSCLES] member is still missing against its weekly floor, from a
     * ledger such as [weightedSetsByMuscle]. Only muscles under their floor are present.
     */
    fun floorShortfalls(
        weightedSets: Map<MuscleGroup, Double>,
        base: VolumeTarget
    ): Map<MuscleGroup, Double> = FLOOR_MUSCLES.mapNotNull { muscle ->
        val floor = forMuscle(base, muscle)?.minSets ?: return@mapNotNull null
        val missing = floor - (weightedSets[muscle] ?: 0.0)
        if (missing > 0.0) muscle to missing else null
    }.toMap()

    /** Balanced's base weekly target, the window [forGoal] returns for it. */
    private const val BALANCED_BASE_TARGET_SETS = 11.0

    /** [base] scaled to [muscle] by [sizeFactor]; null when the muscle has no weekly target. */
    fun forMuscle(base: VolumeTarget, muscle: MuscleGroup): VolumeTarget? =
        sizeFactor(muscle)?.let { factor ->
            VolumeTarget(
                minSets = base.minSets * factor,
                targetSets = base.targetSets * factor,
                maxSets = base.maxSets * factor
            )
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
