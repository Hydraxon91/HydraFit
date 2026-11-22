package com.hydrafit.app.core.domain.schedule

import com.hydrafit.app.core.domain.time.DayOfWeek
import com.hydrafit.app.core.domain.time.dayOfWeekForEpochDay
import com.hydrafit.app.core.domain.workout.LoadKind

/**
 * Why a scheduling action cannot be carried out. The UI shows its own localized sentence per
 * reason; [ScheduleException.message] stays English for logs and for any failure that has no
 * reason yet, which the UI then shows as written.
 */
enum class ScheduleFailure {
    WORKOUT_NOT_FOUND,
    WORKOUT_ALREADY_FINISHED,
    WORKOUT_NOT_IN_ACTIVE_BLOCK,
    WORKOUT_CHANGED,
    WORKOUT_ALREADY_STARTED,
    WORKOUT_HAS_RECORDED_SETS,
    WORKOUT_NEEDS_EXERCISE,
    NOTHING_LOGGED_SKIP_INSTEAD,
    NOT_ALL_SETS_RECORDED,
    FINISH_PARTIAL_CLUSTER_FIRST,
    ENTRY_NOT_FOUND,
    NOT_ACTIVE_GUIDED_WORKOUT,
    MEASURE_CHANGE_UNAVAILABLE,
    MEASURE_KEPT_BY_ORDERED_TARGETS,
    MEASURE_KEPT_BY_CLUSTER_SHAPE,
    MEASURE_NOT_VALID_FOR_SLOT,
    SLOT_HAS_RECORDED_WORK,
    SLOT_HAS_NO_PROGRESSION_POLICY,
    SLOT_HAS_NO_PROGRESSION_CHANGE,
    SLOT_NOT_UNIQUE,
    EXERCISE_NOT_FOUND,
    PRESCRIPTION_NOT_VALID,
    CLUSTER_NEEDS_REPS_AND_LOAD,
    SUPERSET_NOT_VALID,
    BLOCK_NOT_FOUND,
    BLOCK_ALREADY_ACTIVE,
    BLOCK_NEEDS_WORKOUT,
    ONLY_ACTIVE_BLOCK_CAN_FINISH,
    ONLY_ACTIVE_BLOCK_CAN_RESCHEDULE,
    WORKOUTS_STILL_PENDING,
    ONLY_PENDING_WORKOUT_CAN_MOVE,
    ONLY_BEFORE_START_CAN_EDIT,
    ROUTINE_ARCHIVED,
    ROUTINE_NEEDS_WORKOUT,
    ROUTINE_UNKNOWN_EXERCISE,
    PICK_A_WEEKDAY,
    PLAN_NEEDS_WEEKDAYS
}

/** A scheduling action the user asked for cannot be carried out as requested. */
class ScheduleException(message: String, val reason: ScheduleFailure? = null) :
    IllegalStateException(message)

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
    /** Repetitions for counted work, or null for a timed slot. Never a `0` sentinel. */
    val reps: Int? = null,
    /** Elapsed whole seconds for a timed slot, or null for counted work. */
    val durationSeconds: Int? = null,
    /** Whole metres for a distance slot, or null for reps and timed work. */
    val distanceMeters: Int? = null,
    /** Measured force in newtons for a force slot, or null for every other metric. */
    val forceNewtons: Double? = null,
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
            throw ScheduleException(
                "Pick at least one weekday for a weekly schedule",
                ScheduleFailure.PICK_A_WEEKDAY
            )
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
