package com.hydrafit.app.feature.splitbuilder

import com.hydrafit.app.core.domain.engine.AcceptedPlan
import com.hydrafit.app.core.domain.engine.PlanFailureReason
import com.hydrafit.app.core.domain.engine.PlannerEngineId
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
    val scheduleDialog: SplitScheduleDialogState? = null
) {
    val usedFallbackEngine: Boolean
        get() = plan != null && requestedEngine != null && plan.engine != requestedEngine
}
