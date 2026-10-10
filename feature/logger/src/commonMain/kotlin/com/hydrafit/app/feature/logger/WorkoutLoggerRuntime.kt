package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.time.BootIdentityProvider
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.workout.PersistedRestCountdown
import com.hydrafit.app.core.domain.workout.RestCountdown
import com.hydrafit.app.core.domain.workout.RestCountdownRepository
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

/** Logger-scoped timer controller; persisted state is restored only for a matching boot and context. */
class RestTimerController(
    private val scope: CoroutineScope,
    private val elapsedNow: () -> Long = ::elapsedRealtimeMillis,
    private val bootIdentity: () -> String? = { null },
    private val repository: RestCountdownRepository? = null
) {
    private val _state = MutableStateFlow<RestTimerState?>(null)
    val state: StateFlow<RestTimerState?> = _state.asStateFlow()
    private var ticker: Job? = null
    private var persistenceJob: Job? = null
    private var persistedContext: PersistedRestCountdown? = null

    fun start(
        durationMillis: Long = DEFAULT_DURATION_MILLIS,
        completedAtElapsedMillis: Long = elapsedNow(),
        sessionId: String? = null,
        occurrenceId: Long? = null,
        exerciseId: String? = null
    ) {
        val countdown = RestCountdown(completedAtElapsedMillis, durationMillis)
        ticker?.cancel()
        publish(countdown)
        persistedContext = buildPersistedContext(
            countdown,
            sessionId,
            occurrenceId,
            exerciseId
        )
        queuePersistence(persistedContext)
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
        persistedContext = persistedContext?.copy(
            deadlineElapsedMillis = countdown.deadlineElapsedMillis,
            durationMillis = countdown.durationMillis
        )
        queuePersistence(persistedContext)
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

    fun cancel(clearPersisted: Boolean = true) {
        ticker?.cancel()
        ticker = null
        _state.value = null
        if (clearPersisted) {
            persistedContext = null
            queuePersistence(null)
        }
    }

    suspend fun restore(
        currentSessionId: String,
        currentOccurrenceId: Long,
        validExerciseIds: Set<String>
    ): PersistedRestCountdown? {
        val stored = repository?.load() ?: return null
        val currentBootIdentity = bootIdentity()
        if (currentBootIdentity == null ||
            stored.bootIdentity != currentBootIdentity ||
            stored.sessionId != currentSessionId ||
            stored.occurrenceId != currentOccurrenceId ||
            stored.exerciseId !in validExerciseIds ||
            stored.deadlineElapsedMillis <= elapsedNow()
        ) {
            repository.clear()
            return null
        }
        val countdown = RestCountdown(
            completedAtElapsedMillis = stored.deadlineElapsedMillis - stored.durationMillis,
            durationMillis = stored.durationMillis
        )
        persistedContext = stored
        ticker?.cancel()
        publish(countdown)
        ticker = scope.launch {
            while (_state.value?.isFinished == false) {
                delay(TICK_MILLIS)
                publish(countdown)
            }
        }
        return stored
    }

    private fun buildPersistedContext(
        countdown: RestCountdown,
        sessionId: String?,
        occurrenceId: Long?,
        exerciseId: String?
    ): PersistedRestCountdown? {
        val identity = bootIdentity() ?: return null
        if (sessionId == null || occurrenceId == null || exerciseId == null) return null
        return PersistedRestCountdown(
            deadlineElapsedMillis = countdown.deadlineElapsedMillis,
            durationMillis = countdown.durationMillis,
            bootIdentity = identity,
            sessionId = sessionId,
            occurrenceId = occurrenceId,
            exerciseId = exerciseId
        )
    }

    private fun queuePersistence(value: PersistedRestCountdown?) {
        val store = repository ?: return
        val previous = persistenceJob
        persistenceJob = scope.launch {
            previous?.join()
            if (value == null) store.clear() else store.save(value)
        }
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
    private val elapsedNow: () -> Long = ::elapsedRealtimeMillis,
    private val bootIdentityProvider: BootIdentityProvider = BootIdentityProvider { null },
    private val restCountdownRepository: RestCountdownRepository? = null
) {
    fun nowMillis(): Long = timeProvider.nowMillis()

    fun utcOffsetMillis(): Long = timeProvider.utcOffsetMillis()

    fun elapsedRealtimeMillis(): Long = elapsedNow()

    fun restTimer(scope: CoroutineScope): RestTimerController = RestTimerController(
        scope,
        elapsedNow,
        bootIdentityProvider::currentBootIdentity,
        restCountdownRepository
    )
}
