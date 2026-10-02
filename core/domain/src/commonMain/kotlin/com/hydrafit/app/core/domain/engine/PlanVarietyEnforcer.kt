package com.hydrafit.app.core.domain.engine

/**
 * Deterministic quality guard for model-backed plans. A small on-device model reliably produces
 * valid-but-repetitive weeks, so this enforces the rules the prompt only asks for:
 *
 * - a day never lists the same exercise twice;
 * - no two days share a focus (the model sometimes fills the week with one focus);
 * - a compound exercise is not repeated across the week (accessory/isolation work may repeat,
 *   since it is exempt from week-over-week rotation).
 *
 * Returns null when the surviving plan no longer satisfies the request (too few days, or a day left
 * with too few usable exercises), so the caller can fall back to the deterministic engine.
 */
class PlanVarietyEnforcer {

    /**
     * @param isCompound maps an exercise id to whether it is compound; unknown ids are treated as
     *   compound so an unrecognized repetition is still deduped.
     */
    fun enforce(
        plan: WeeklyPlan,
        request: PlanRequest,
        isCompound: (String) -> Boolean
    ): WeeklyPlan? {
        val seenFoci = mutableSetOf<SplitFocus>()
        val usedCompoundIds = mutableSetOf<String>()
        val days = mutableListOf<WorkoutDay>()

        for (day in plan.days.sortedBy { it.dayIndex }) {
            if (!seenFoci.add(day.focus)) continue
            val seenExercises = mutableSetOf<String>()
            val exercises = day.exercises.filter { planned ->
                if (!seenExercises.add(planned.exerciseId)) return@filter false
                val compound = isCompound(planned.exerciseId)
                !compound || usedCompoundIds.add(planned.exerciseId)
            }
            if (exercises.size < PlannerExerciseCounts.FLOOR_PER_DAY) return null
            days += day.copy(exercises = exercises)
        }

        if (days.size < request.daysPerWeek) return null
        return plan.copy(
            days = days.take(request.daysPerWeek)
                .mapIndexed { index, day -> day.copy(dayIndex = index) }
        )
    }
}
