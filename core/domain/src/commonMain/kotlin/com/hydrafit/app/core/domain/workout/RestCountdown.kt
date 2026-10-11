package com.hydrafit.app.core.domain.workout

/** A transient countdown anchored to the monotonic time of a live set completion. */
data class RestCountdown(val completedAtElapsedMillis: Long, val durationMillis: Long) {
    init {
        require(completedAtElapsedMillis >= 0L)
        require(durationMillis in 1L..MAX_DURATION_MILLIS)
        require(durationMillis <= Long.MAX_VALUE - completedAtElapsedMillis)
    }

    val deadlineElapsedMillis: Long get() = completedAtElapsedMillis + durationMillis

    fun remainingMillis(nowElapsedMillis: Long): Long {
        require(nowElapsedMillis >= 0L)
        return (deadlineElapsedMillis - nowElapsedMillis).coerceAtLeast(0L)
    }

    fun withDuration(newDurationMillis: Long): RestCountdown =
        copy(durationMillis = newDurationMillis)

    companion object {
        const val MAX_DURATION_MILLIS = 86_400_000L
    }
}
