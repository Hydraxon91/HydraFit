package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.workout.PersistedRestCountdown
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes receiver/timer presentation without persisting a second countdown or delivery ledger. */
class RestCompletionPresentation(private val elapsedNow: () -> Long = ::elapsedRealtimeMillis) {
    private val lock = Mutex()
    private val visibleSince = MutableStateFlow<Long?>(null)
    private val signals = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val completions = signals.asSharedFlow()
    private var armed: PersistedRestCountdown? = null
    private var completed: PersistedRestCountdown? = null

    fun setVisible(visible: Boolean) {
        visibleSince.value = if (visible) visibleSince.value ?: elapsedNow() else null
    }

    suspend fun update(countdown: PersistedRestCountdown?, enabled: Boolean) = lock.withLock {
        if (!enabled || countdown == null) {
            armed = null
        } else if (countdown != armed && countdown.deadlineElapsedMillis > elapsedNow()) {
            armed = countdown
        }
    }

    /**
     * A foreground signal must have been enabled and visibly resumed before expiry. Background
     * delivery remains the receiver's responsibility; a foreground timer never posts a system alert.
     */
    suspend fun complete(
        countdown: PersistedRestCountdown,
        foregroundEnabled: Boolean,
        background: (suspend () -> Unit)? = null
    ) = lock.withLock {
        if (completed == countdown || elapsedNow() < countdown.deadlineElapsedMillis) {
            return@withLock
        }
        val since = visibleSince.value
        if (foregroundEnabled &&
            armed == countdown &&
            since != null &&
            since < countdown.deadlineElapsedMillis &&
            signals.subscriptionCount.value > 0
        ) {
            completed = countdown
            signals.tryEmit(Unit)
        } else if (background != null) {
            background()
            completed = countdown
        }
    }
}
