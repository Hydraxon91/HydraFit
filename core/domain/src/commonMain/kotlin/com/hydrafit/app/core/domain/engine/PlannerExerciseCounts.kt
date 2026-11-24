package com.hydrafit.app.core.domain.engine

/**
 * One source of truth for per-day exercise counts. The model prompts and schemas target
 * [TARGET_MIN_PER_DAY]..[TARGET_MAX_PER_DAY], while the validators accept anything at or above the
 * looser [FLOOR_PER_DAY] so a slightly short plan is not discarded.
 *
 * The deterministic planner has no minimum: ordinary deficit-filling accessories stop at
 * [TARGET_MAX_PER_DAY], and only the must-haves (direct arm coverage, the shrug on pull and upper
 * days, leg extension and leg curl on lower and legs days) may take a day up to
 * [MUST_HAVE_MAX_PER_DAY].
 */
object PlannerExerciseCounts {
    const val FLOOR_PER_DAY = 2
    const val TARGET_MIN_PER_DAY = 4
    const val TARGET_MAX_PER_DAY = 6
    const val MUST_HAVE_MAX_PER_DAY = 8

    /** Longer sessions keep the same two-tier shape with a higher ceiling on each tier. */
    const val LONGER_TARGET_MAX_PER_DAY = 9
    const val LONGER_MUST_HAVE_MAX_PER_DAY = 11

    /** Ordinary deficit-filling accessories stop here for the chosen [length]. */
    fun targetMax(length: SessionLength): Int = when (length) {
        SessionLength.STANDARD -> TARGET_MAX_PER_DAY
        SessionLength.LONGER -> LONGER_TARGET_MAX_PER_DAY
    }

    /** Must-haves (direct arm work, weekly floors, the shrug, leg isolation) stop here. */
    fun mustHaveMax(length: SessionLength): Int = when (length) {
        SessionLength.STANDARD -> MUST_HAVE_MAX_PER_DAY
        SessionLength.LONGER -> LONGER_MUST_HAVE_MAX_PER_DAY
    }
}
