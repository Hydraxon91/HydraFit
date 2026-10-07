package com.hydrafit.app.core.domain.schedule

import com.hydrafit.app.core.domain.time.DayOfWeek
import com.hydrafit.app.core.domain.time.dayOfWeekForEpochDay
import com.hydrafit.app.core.domain.workout.LoadKind

/** A scheduling action the user asked for cannot be carried out as requested. */
class ScheduleException(message: String) : IllegalStateException(message)

/** How a workout is being concluded. */
enum class FinishMode { FULL, PARTIAL, SKIP_REMAINING }

/** The choices made when a training block is activated. */
data class ActivationRequest(
    val name: String,
    val startEpochDay: Long,
    val mode: ScheduleMode,
    val weekdays: Set<DayOfWeek> = emptySet(),
    val templateId: Long? = null,
    val templateRevision: Int? = null,
    val sourcePlanId: Long? = null,
    val weekNumber: Int? = null,
    val cycleNumber: Int? = null,
    /** Start today even when today is not a chosen weekday (a one-off first occurrence). */
    val startToday: Boolean = false,
    /** Cancel the currently active block before activating this one. */
    val replaceActive: Boolean = false
)

/** One editable slot the user submits when re-prescribing an unstarted occurrence. */
data class OccurrenceEntryDraft(
    val exerciseId: String,
    val sets: Int,
    val reps: Int,
    val weightKg: Double? = null,
    val loadKind: LoadKind = LoadKind.EXTERNAL
)

/**
 * Assigns one scheduled civil day to each of [count] workouts.
 *
 * Chosen-weekday mode places the first workout on the start date only when [startToday] is set
 * (the explicit one-off exception) and otherwise walks forward to the next chosen weekday; every
 * later workout lands on a chosen weekday, so a late-week start crosses calendar boundaries without
 * compressing the block. Sequence mode returns no dates.
 */
class PreviewWorkoutScheduleUseCase {
    operator fun invoke(
        count: Int,
        startEpochDay: Long,
        mode: ScheduleMode,
        weekdays: Set<DayOfWeek>,
        startToday: Boolean = false
    ): List<Long?> {
        if (count <= 0) return emptyList()
        if (mode == ScheduleMode.SEQUENCE) return List(count) { null }
        if (weekdays.isEmpty()) {
            throw ScheduleException("Pick at least one weekday for a weekly schedule")
        }
        val dates = mutableListOf<Long>()
        var cursor = startEpochDay
        if (startToday) {
            dates += startEpochDay
            cursor = startEpochDay + 1
        }
        while (dates.size < count) {
            if (dayOfWeekForEpochDay(cursor) in weekdays) dates += cursor
            cursor++
        }
        return dates
    }
}
