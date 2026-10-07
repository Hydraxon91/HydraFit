package com.hydrafit.app.feature.splitbuilder

import com.hydrafit.app.core.domain.engine.AcceptedPlan
import com.hydrafit.app.core.domain.engine.PlanFailureReason
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.SwapCandidate
import com.hydrafit.app.core.domain.engine.WeeklyPlan

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
    val swapNoCandidates: Boolean = false
) {
    val usedFallbackEngine: Boolean
        get() = plan != null && requestedEngine != null && plan.engine != requestedEngine
}
