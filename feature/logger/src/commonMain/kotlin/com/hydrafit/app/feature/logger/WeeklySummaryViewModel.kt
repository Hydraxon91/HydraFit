package com.hydrafit.app.feature.logger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hydrafit.app.core.domain.workout.ObserveWeeklyTrainingSummaryUseCase
import com.hydrafit.app.core.domain.workout.WeeklyTrainingSummary
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Read-only state for the Train weekly card. A failed read leaves no summary, so the card simply
 * stays hidden; nothing here writes anything.
 */
class WeeklySummaryViewModel(observeSummary: ObserveWeeklyTrainingSummaryUseCase) : ViewModel() {
    val summary: StateFlow<WeeklyTrainingSummary?> = observeSummary()
        .map<WeeklyTrainingSummary, WeeklyTrainingSummary?> { it }
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS), null)

    private companion object {
        const val STOP_TIMEOUT_MILLIS = 5_000L
    }
}
