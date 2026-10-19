package com.hydrafit.app.feature.splitbuilder

import com.hydrafit.app.core.domain.engine.AcceptedPlan
import com.hydrafit.app.core.domain.engine.AutomaticSetPrescription
import com.hydrafit.app.core.domain.engine.PlanFailureReason
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.SetPrescriptionShape
import com.hydrafit.app.core.domain.engine.SwapCandidate
import com.hydrafit.app.core.domain.engine.WeeklyPlan
import com.hydrafit.app.core.domain.schedule.ScheduleMode
import com.hydrafit.app.core.domain.time.DayOfWeek

/** The scheduling choices shown when a generated plan is started as a block. */
data class SplitScheduleDialogState(
    val mode: ScheduleMode = ScheduleMode.WEEKDAY,
    val weekdays: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
    val startToday: Boolean = true,
    val startEpochDay: Long,
    val preview: List<Long?> = emptyList(),
    /** True when a block is already active, so the replacement choice is worth showing. */
    val hasActiveBlock: Boolean = false,
    val replaceActive: Boolean = false,
    val error: String? = null
)

/**
 * The planning-date configuration: chosen weekdays plus a start date, previewed as civil dates.
 * An empty [dates] list means unknown dates, which keeps the undated planning behavior.
 */
data class PlanningDatesState(
    /** The confirmed civil dates sent to generation; empty means unknown dates. */
    val dates: List<Long> = emptyList(),
    val weekdays: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
    val startToday: Boolean = true,
    val startEpochDay: Long = 0L,
    /** The live preview while the editor is open; the confirmed [dates] once confirmed. */
    val preview: List<Long?> = emptyList(),
    val error: String? = null
)

/** Which measure a slot's dialog edits: a rep count or elapsed whole seconds. */
enum class MeasureMetric { REPS, SECONDS }

/** Why a slot measure edit was rejected; the UI maps each to a specific message. */
enum class MeasureError { REPS_RANGE, SECONDS_RANGE, SECONDS_TOO_LARGE }

/**
 * The open measure dialog for one slot of the shown plan. [value] is the raw field text; a non-null
 * [error] means the last Confirm was rejected, so nothing was written.
 */
data class MeasureDialogState(
    val dayIndex: Int,
    val position: Int,
    val metric: MeasureMetric,
    val value: String,
    val error: MeasureError? = null
)

/** Which automatic-prescription input is invalid; the dialog shows the matching localized message. */
enum class AutomaticSetPrescriptionError { REPS_RANGE, RIR_RANGE }

/**
 * The open automatic-ordered-prescription dialog: the opt-in's inputs, with the numbers as text
 * until they are applied so partial input is allowed. [error] is set only when Apply is refused.
 */
data class AutomaticSetPrescriptionState(
    val shape: SetPrescriptionShape = SetPrescriptionShape.STRAIGHT,
    val minReps: String = "",
    val maxReps: String = "",
    val targetRir: String = "",
    val error: AutomaticSetPrescriptionError? = null
)

data class SplitBuilderUiState(
    val daysPerWeek: Int = 4,
    val setsPerExercise: Int = 3,
    val accessorySetsPerExercise: Int = 2,
    val plan: WeeklyPlan? = null,
    val exerciseNames: Map<String, String> = emptyMap(),
    val history: List<AcceptedPlan> = emptyList(),
    val requestedEngine: PlannerEngineId? = null,
    val isLoading: Boolean = true,
    val isPlanAccepted: Boolean = false,
    val canRegenerate: Boolean = true,
    val hasError: Boolean = false,
    val isTransientError: Boolean = false,
    val errorDetail: String? = null,
    val failureReason: PlanFailureReason? = null,
    /** Why a fallback plan was shown, when the requested engine is known to have failed a step. */
    val fallbackReason: PlanFailureReason? = null,
    /** True while the swap candidate dialog is shown for [swapTargetDayIndex]/[swapTargetPosition]. */
    val swapDialogOpen: Boolean = false,
    val swapTargetDayIndex: Int? = null,
    val swapTargetPosition: Int? = null,
    val swapCandidates: List<SwapCandidate> = emptyList(),
    /** True when a selected candidate could not be applied (it became unavailable or sore). */
    val swapNoCandidates: Boolean = false,
    /** True when a training block is already active, so starting a new one would replace it. */
    val hasActiveBlock: Boolean = false,
    /** The schedule dialog shown when starting the plan as a block; null when closed. */
    val scheduleDialog: SplitScheduleDialogState? = null,
    /** The confirmed planning dates and the weekday/start-date configuration behind them. */
    val planningDates: PlanningDatesState = PlanningDatesState(),
    /** The open planning-date editor; null when closed. */
    val planningDatesDialog: PlanningDatesState? = null,
    /** The dates the planner actually used in the latest request; empty when unknown or rejected. */
    val appliedPlanningDates: List<Long> = emptyList(),
    /** The dates the shown draft was generated with; null when no draft provenance is tracked. */
    val draftDates: List<Long>? = null,
    /** One-shot: true after "Save as routine" succeeds, until the screen shows the confirmation. */
    val routineSaved: Boolean = false,
    /** True after a confirmed swap invalidated the shown plan's persisted volume explanation. */
    val volumeExplanationInvalidated: Boolean = false,
    /** The open per-slot measure dialog; null when closed. */
    val measureDialog: MeasureDialogState? = null,
    /** The applied automatic per-set opt-in, or null when it is off. */
    val automaticSetPrescription: AutomaticSetPrescription? = null,
    /** The open automatic-prescription dialog; null when closed. */
    val automaticSetPrescriptionDialog: AutomaticSetPrescriptionState? = null,
    /**
     * The user's opt-in for curated adjacent biceps/triceps pairing. Off by default; on passes the
     * configured transition and pair rest with the next generation request.
     */
    val plannerSupersetEnabled: Boolean = false,
    /** Whole seconds of transition between paired exercises, as typed. Never persisted here. */
    val plannerSupersetTransitionSeconds: String = "0",
    /** Whole seconds of rest after a completed pair round, as typed. */
    val plannerSupersetPairRestSeconds: String = "120"
) {
    val usedFallbackEngine: Boolean
        get() = plan != null && requestedEngine != null && plan.engine != requestedEngine

    /**
     * True when the shown draft was planned under different dates than the current request, so its
     * per-day fatigue belongs to another date context. Accepting or scheduling it would present a
     * plan that was never generated for the dates now in force; Regenerate is the way forward.
     */
    val draftContextStale: Boolean
        get() = plan != null &&
            !isPlanAccepted &&
            draftDates != null &&
            draftDates != appliedPlanningDates

    /** True when dates are confirmed but the planner could not use them (frequency, order or past). */
    val planningDatesRejected: Boolean
        get() = planningDates.dates.isNotEmpty() && appliedPlanningDates.isEmpty()
}
