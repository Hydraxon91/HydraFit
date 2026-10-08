package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.time.DayOfWeek

data class WeeklyPlan(
    val engine: PlannerEngineId,
    val days: List<WorkoutDay>,
    val weekNumber: Int = 1,
    val cycleNumber: Int = 1,
    val armCoverage: List<ArmMuscleCoverage> = emptyList()
) {
    /** Spreads the plan's training days across the week, starting on Monday. */
    fun scheduledDay(dayIndex: Int): DayOfWeek? {
        if (days.isEmpty() || dayIndex !in days.indices) return null
        val offset = dayIndex * DayOfWeek.entries.size / days.size
        return DayOfWeek.entries[offset.coerceIn(0, DayOfWeek.entries.lastIndex)]
    }

    fun dayFor(dayOfWeek: DayOfWeek): WorkoutDay? =
        days.firstOrNull { scheduledDay(it.dayIndex) == dayOfWeek }
}
