package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.time.DayOfWeek

/**
 * A plan the user explicitly accepted. Display data is snapshotted so a later catalog edit cannot
 * silently rewrite the meaning of an accepted plan.
 */
data class AcceptedPlan(
    val engine: PlannerEngineId,
    val acceptedAtMillis: Long,
    val days: List<AcceptedDay>
) {
    /** Spreads the plan's training days across the week, starting on Monday. */
    fun scheduledDay(dayIndex: Int): DayOfWeek? {
        if (days.isEmpty() || dayIndex !in days.indices) return null
        val offset = dayIndex * DayOfWeek.entries.size / days.size
        return DayOfWeek.entries[offset.coerceIn(0, DayOfWeek.entries.lastIndex)]
    }

    fun dayFor(dayOfWeek: DayOfWeek): AcceptedDay? =
        days.firstOrNull { scheduledDay(it.dayIndex) == dayOfWeek }
}

data class AcceptedDay(
    val dayIndex: Int,
    val focus: SplitFocus,
    val exercises: List<AcceptedExercise>
)

data class AcceptedExercise(
    val exerciseId: String,
    val sets: Int,
    val reps: Int,
    val name: String,
    val movementPattern: MovementPattern,
    val suggestedWeightKg: Double? = null
)
