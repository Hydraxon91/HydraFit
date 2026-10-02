package com.hydrafit.app.core.domain.engine

/**
 * One source of truth for per-day exercise counts. The model prompts and schemas target
 * [TARGET_MIN_PER_DAY]..[TARGET_MAX_PER_DAY], while the validators accept anything at or above the
 * looser [FLOOR_PER_DAY] so a slightly short plan is not discarded.
 */
object PlannerExerciseCounts {
    const val FLOOR_PER_DAY = 2
    const val TARGET_MIN_PER_DAY = 4
    const val TARGET_MAX_PER_DAY = 6
}
