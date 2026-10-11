package com.hydrafit.app.core.domain.workout

import kotlinx.coroutines.flow.Flow

/** Process-restorable timer state. Boot identity and workout context are required to resume it. */
data class PersistedRestCountdown(
    val deadlineElapsedMillis: Long,
    val durationMillis: Long,
    val bootIdentity: String,
    val sessionId: String,
    val occurrenceId: Long,
    val exerciseId: String
) {
    init {
        require(deadlineElapsedMillis >= 0L)
        require(durationMillis in 1L..RestCountdown.MAX_DURATION_MILLIS)
        require(bootIdentity.isNotBlank())
        require(sessionId.isNotBlank())
        require(occurrenceId > 0L)
        require(exerciseId.isNotBlank())
    }
}

interface RestCountdownRepository {
    /** Emits the current countdown and every subsequent save or clear. */
    fun observe(): Flow<PersistedRestCountdown?>

    suspend fun load(): PersistedRestCountdown?

    suspend fun save(countdown: PersistedRestCountdown)

    suspend fun clear()
}
