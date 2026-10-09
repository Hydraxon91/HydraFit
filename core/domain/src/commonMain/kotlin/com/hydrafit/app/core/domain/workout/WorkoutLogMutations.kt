package com.hydrafit.app.core.domain.workout

import kotlinx.coroutines.flow.Flow

/**
 * Groups the use cases that mutate the workout log so the Logger can depend on one collaborator
 * instead of one binding per operation. Pure delegation only: it adds no behavior of its own, so
 * every call keeps the semantics of the use case it forwards to.
 */
class WorkoutLogMutations(
    private val logWorkoutSet: LogWorkoutSetUseCase,
    private val deleteWorkoutSet: DeleteWorkoutSetUseCase,
    private val correctWorkoutSetTime: CorrectWorkoutSetTimeUseCase,
    private val correctWorkoutSet: CorrectWorkoutSetUseCase
) {
    /** Streams the open session (or null) so the logger can show active-session state. */
    fun observeOpenSession(): Flow<WorkoutSession?> = logWorkoutSet.observeOpenSession()

    /** Resolves the set's session, stamps it, and persists the set. */
    suspend operator fun invoke(set: WorkoutSet, utcOffsetMillis: Long) =
        logWorkoutSet(set, utcOffsetMillis)

    /** Persists [set] into an explicit session, bypassing auto-resolution. */
    suspend fun logInto(set: WorkoutSet, sessionId: String) = logWorkoutSet.logInto(set, sessionId)

    /** Persists a backdated set, choosing and returning the session it belongs to. */
    suspend fun logBackdated(
        set: WorkoutSet,
        utcOffsetMillis: Long,
        forceNewSession: Boolean
    ): WorkoutSession = logWorkoutSet.logBackdated(set, utcOffsetMillis, forceNewSession)

    /** Closes the open session at [endedAtMillis] if one exists. */
    suspend fun endSession(endedAtMillis: Long) = logWorkoutSet.endSession(endedAtMillis)

    /** Closes the current session and opens a fresh one anchored at [startedAtMillis]. */
    suspend fun startNewSession(startedAtMillis: Long, utcOffsetMillis: Long) =
        logWorkoutSet.startNewSession(startedAtMillis, utcOffsetMillis)

    /** Closes an open session that rolled into a new local day or went idle. */
    suspend fun expireOpenSession(nowMillis: Long, utcOffsetMillis: Long) =
        logWorkoutSet.expireOpenSession(nowMillis, utcOffsetMillis)

    /** Removes a single logged set. */
    suspend fun delete(id: Long) = deleteWorkoutSet(id)

    /** Corrects only a logged set's performed-at time. */
    suspend fun correctTime(setId: Long, performedAtMillis: Long) =
        correctWorkoutSetTime(setId, performedAtMillis)

    /** Corrects the performed values of the same recorded set. */
    suspend fun correctSet(setId: Long, correction: WorkoutSetCorrection) =
        correctWorkoutSet(setId, correction)
}
