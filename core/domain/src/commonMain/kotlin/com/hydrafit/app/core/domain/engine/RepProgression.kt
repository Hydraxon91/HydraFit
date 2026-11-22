package com.hydrafit.app.core.domain.engine

/** An exercise's last comparable, non-deload target and whether every working set met it. */
data class RepProgressionHistory(
    val reps: Int,
    val weightKg: Double?,
    val outcome: ProgressionExposureOutcome
)

/** How a slot's target was reached from its last comparable exposure. */
enum class RepProgressionStep {
    /** No usable last target inside the range: start at the bottom with the converted load. */
    FIRST,

    /** Last target met below the top: same load, one more rep. */
    ADD_REP,

    /** Last target met at the top: the load steps up and reps return to the bottom. */
    STEP_LOAD,

    /** Last target missed, not yet judged, or a deload week: repeat it. */
    REPEAT
}

/** The week's reps and, when the history supplies one, the load to hold instead of converting. */
data class RepProgressionTarget(
    val reps: Int,
    val heldLoadKg: Double?,
    val step: RepProgressionStep
)

/**
 * Kept with a generated slot so the plan can say why its reps and load are what they are. It is not
 * stored with an accepted plan.
 */
data class RepProgressionExplanation(
    val step: RepProgressionStep,
    val minReps: Int,
    val maxReps: Int,
    val previousReps: Int? = null,
    val previousWeightKg: Double? = null
)

/**
 * Double progression inside a goal's rep range, shared by both engines. A slot starts at the bottom
 * of the range. Each time every working set meets the target it gains a rep at the same load; at the
 * top of the range the load steps up by [loadStepKg] and reps return to the bottom. A miss, an
 * unjudged session or a deload week repeats the last target. A last target outside the range (a goal
 * change, or an older fixed-rep plan) starts over at the bottom.
 */
object RepProgression {
    fun next(
        last: RepProgressionHistory?,
        range: IntRange,
        loadStepKg: Double,
        holdForDeload: Boolean = false
    ): RepProgressionTarget {
        if (last == null || last.reps !in range) {
            return RepProgressionTarget(range.first, null, RepProgressionStep.FIRST)
        }
        if (holdForDeload || last.outcome != ProgressionExposureOutcome.SUCCESS) {
            return RepProgressionTarget(last.reps, last.weightKg, RepProgressionStep.REPEAT)
        }
        return if (last.reps < range.last) {
            RepProgressionTarget(last.reps + 1, last.weightKg, RepProgressionStep.ADD_REP)
        } else {
            RepProgressionTarget(
                range.first,
                last.weightKg?.plus(loadStepKg),
                RepProgressionStep.STEP_LOAD
            )
        }
    }
}
