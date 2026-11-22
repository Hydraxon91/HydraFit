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
}
