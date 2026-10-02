package com.hydrafit.app.core.domain.workout

/**
 * Corrects the performed-at time of an already-logged set.
 *
 * This is a time-only edit: the set keeps its reps, weight, warm-up flag, RIR, plan snapshot
 * (week/cycle/day), muscle snapshot, and its `sessionId`. Because the session is left in place, a
 * correction that lands between another session's sets makes the timestamp-ordered session ids
 * alternate, which the fatigue calculator reads as extra session resets, and a correction that
 * crosses local days leaves the original session's bounds and `localEpochDay` stale. Re-segmenting
 * is a possible follow-up; it is intentionally out of scope here.
 */
class CorrectWorkoutSetTimeUseCase(private val repository: WorkoutLogRepository) {
    suspend operator fun invoke(setId: Long, performedAtMillis: Long) =
        repository.updateSetPerformedAt(setId, performedAtMillis)
}
