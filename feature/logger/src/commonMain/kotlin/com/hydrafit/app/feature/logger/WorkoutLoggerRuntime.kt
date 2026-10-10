package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.workout.RestCountdown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Platform monotonic clock that advances while the device sleeps. */
expect fun elapsedRealtimeMillis(): Long

data class RestTimerState(val countdown: RestCountdown, val remainingMillis: Long) {
    val isFinished: Boolean get() = remainingMillis == 0L
}

/** Logger-scoped timer controller; no countdown state is persisted. */
class RestTimerController(
    private val scope: CoroutineScope,
    private val elapsedNow: () -> Long = ::elapsedRealtimeMillis
) {
    private val _state = MutableStateFlow<RestTimerState?>(null)
    val state: StateFlow<RestTimerState?> = _state.asStateFlow()
    private var ticker: Job? = null

    fun start(durationMillis: Long = DEFAULT_DURATION_MILLIS) {
        val countdown = RestCountdown(elapsedNow(), durationMillis)
        ticker?.cancel()
        publish(countdown)
        ticker = scope.launch {
            while (_state.value?.isFinished == false) {
                delay(TICK_MILLIS)
                publish(countdown)
            }
        }
    }

    fun updateDuration(durationMillis: Long) {
        val countdown = _state.value?.countdown?.withDuration(durationMillis) ?: return
        ticker?.cancel()
        publish(countdown)
        ticker = if (_state.value?.isFinished == false) {
            scope.launch {
                while (_state.value?.isFinished == false) {
                    delay(TICK_MILLIS)
                    publish(countdown)
                }
            }
        } else {
            null
        }
    }

    fun cancel() {
        ticker?.cancel()
        ticker = null
        _state.value = null
    }

    private fun publish(countdown: RestCountdown) {
        _state.value = RestTimerState(
            countdown = countdown,
            remainingMillis = countdown.remainingMillis(elapsedNow())
        )
    }

    companion object {
        const val DEFAULT_DURATION_MILLIS = 120_000L
        private const val TICK_MILLIS = 250L
    }
}

/** Groups Logger's wall clock and ViewModel-owned timer factory without widening its DI surface. */
class WorkoutLoggerRuntime(
    private val timeProvider: TimeProvider,
    private val elapsedNow: () -> Long = ::elapsedRealtimeMillis
) {
    fun nowMillis(): Long = timeProvider.nowMillis()

    fun utcOffsetMillis(): Long = timeProvider.utcOffsetMillis()

    fun restTimer(scope: CoroutineScope): RestTimerController =
        RestTimerController(scope, elapsedNow)
}
