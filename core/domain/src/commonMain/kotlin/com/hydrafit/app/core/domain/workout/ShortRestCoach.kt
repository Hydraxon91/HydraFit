package com.hydrafit.app.core.domain.workout

data class LiveRestCompletion(
    val sessionId: String,
    val occurrenceId: Long,
    val exerciseId: String,
    val bootIdentity: String,
    val completedAtElapsedMillis: Long,
    val chosenRestMillis: Long
)

/** Transient, opt-in guidance. Two short intervals is a product default, not a recovery estimate. */
class ShortRestCoach {
    private var completion: LiveRestCompletion? = null
    private var streakKey: Triple<String, Long, String>? = null
    private var shortIntervals = 0
    private val shown = mutableSetOf<Pair<String, String>>()

    fun completed(value: LiveRestCompletion) {
        completion = value
    }

    fun started(
        sessionId: String?,
        occurrenceId: Long,
        exerciseId: String,
        bootIdentity: String?,
        startedAtElapsedMillis: Long
    ): Boolean {
        val previous = completion
        completion = null
        if (previous == null ||
            previous.sessionId != sessionId ||
            previous.occurrenceId != occurrenceId ||
            previous.exerciseId != exerciseId ||
            previous.bootIdentity != bootIdentity ||
            startedAtElapsedMillis < previous.completedAtElapsedMillis
        ) {
            resetEligibility()
            return false
        }
        val key = Triple(previous.sessionId, occurrenceId, exerciseId)
        if (streakKey != key) shortIntervals = 0
        streakKey = key
        val short =
            startedAtElapsedMillis - previous.completedAtElapsedMillis < previous.chosenRestMillis
        shortIntervals = if (short) shortIntervals + 1 else 0
        val displayKey = previous.sessionId to exerciseId
        return shortIntervals >= 2 && shown.add(displayKey)
    }

    /** Invalidated timing cannot contribute; previously shown guidance remains once per session. */
    fun resetEligibility() {
        completion = null
        streakKey = null
        shortIntervals = 0
    }
}
