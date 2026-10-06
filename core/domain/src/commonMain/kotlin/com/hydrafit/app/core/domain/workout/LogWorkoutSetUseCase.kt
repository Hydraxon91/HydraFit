package com.hydrafit.app.core.domain.workout

import com.hydrafit.app.core.domain.time.localEpochDay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Persists a logged set, stamping it with the explicit session it belongs to.
 *
 * The session lifecycle lives here (rather than as a second logger dependency) because every
 * boundary is driven by logging: with no open session the first set auto-starts one, a set on a new
 * local day or beyond [SessionConfig.sessionInactivityWindow] closes the prior session and starts a
 * new one, and the manual End/New controls close and/or open sessions. All mutating entry points
 * share a [Mutex] so a concurrent log and control (or a draft batch) cannot create two sessions for
 * one boundary.
 */
class LogWorkoutSetUseCase(
    private val repository: WorkoutLogRepository,
    private val startWorkoutSession: StartWorkoutSessionUseCase,
    private val endWorkoutSession: EndWorkoutSessionUseCase,
    private val observeOpenWorkoutSession: ObserveOpenWorkoutSessionUseCase,
    private val config: SessionConfig
) {
    private val sessionLock = Mutex()

    /** Streams the open session (or null) so the logger can show active-session state. */
    fun observeOpenSession(): Flow<WorkoutSession?> = observeOpenWorkoutSession()

    /** Resolves the set's session, stamps it, and persists the set. */
    suspend operator fun invoke(set: WorkoutSet, utcOffsetMillis: Long) {
        sessionLock.withLock {
            val session = resolveSession(set.performedAtMillis, utcOffsetMillis)
            repository.add(set.copy(sessionId = session.id))
        }
    }

    /**
     * Persists a backdated set, choosing its session from the chosen time: the open session when the
     * time is on its local day and not before it began (and [forceNewSession] is false), otherwise a
     * new session anchored at the set's time that is written already closed. The open session is
     * never closed or replaced. Returns the session the set was written into so a draft batch can
     * reuse it through [logInto].
     */
    suspend fun logBackdated(
        set: WorkoutSet,
        utcOffsetMillis: Long,
        forceNewSession: Boolean
    ): WorkoutSession = sessionLock.withLock {
        val target = backdatedAttachTarget(set.performedAtMillis, utcOffsetMillis, forceNewSession)
            ?: startWorkoutSession(
                set.performedAtMillis,
                localEpochDay(set.performedAtMillis, utcOffsetMillis),
                endedAtMillis = set.performedAtMillis
            )
        repository.add(set.copy(sessionId = target.id))
        target
    }

    /** Persists [set] into an explicit session, bypassing auto-resolution. */
    suspend fun logInto(set: WorkoutSet, sessionId: String) {
        sessionLock.withLock {
            repository.add(set.copy(sessionId = sessionId))
        }
    }

    /**
     * The open session a backdated set would attach to, or null when it must start a new one: the
     * chosen time is on the open session's local day and not before that session began.
     * [forceNewSession] always starts a new session, and a time before the open session's start is
     * rejected so the negative gap is never read as "within the window".
     */
    private suspend fun backdatedAttachTarget(
        performedAtMillis: Long,
        utcOffsetMillis: Long,
        forceNewSession: Boolean
    ): WorkoutSession? {
        if (forceNewSession) return null
        val open = observeOpenWorkoutSession.current() ?: return null
        if (localEpochDay(performedAtMillis, utcOffsetMillis) != open.localEpochDay) return null
        if (performedAtMillis < open.startedAtMillis) return null
        return open
    }

    /** Closes the open session at [endedAtMillis] if one exists; the next set auto-starts a new one. */
    suspend fun endSession(endedAtMillis: Long) {
        sessionLock.withLock {
            observeOpenWorkoutSession.current()?.let { endWorkoutSession(it.id, endedAtMillis) }
        }
    }

    /** Closes the current session and immediately opens a fresh one anchored at [startedAtMillis]. */
    suspend fun startNewSession(startedAtMillis: Long, utcOffsetMillis: Long): WorkoutSession =
        sessionLock.withLock {
            observeOpenWorkoutSession.current()?.let { endWorkoutSession(it.id, startedAtMillis) }
            startWorkoutSession(startedAtMillis, localEpochDay(startedAtMillis, utcOffsetMillis))
        }

    /**
     * Closes an open session that has rolled into a new local day or sat idle past the inactivity
     * window, so a stale session is not shown as active when the logger reopens. No session is
     * started here; the next logged set auto-starts one.
     */
    suspend fun expireOpenSession(nowMillis: Long, utcOffsetMillis: Long) {
        sessionLock.withLock {
            val open = observeOpenWorkoutSession.current() ?: return@withLock
            val lastSetAt = lastSetAt(open)
            val rolled = localEpochDay(nowMillis, utcOffsetMillis) != open.localEpochDay
            val idle = nowMillis - lastSetAt > config.sessionInactivityWindow.inWholeMilliseconds
            if (rolled || idle) endWorkoutSession(open.id, lastSetAt)
        }
    }

    private suspend fun resolveSession(
        performedAtMillis: Long,
        utcOffsetMillis: Long
    ): WorkoutSession {
        val open = observeOpenWorkoutSession.current()
            ?: return startWorkoutSession(
                performedAtMillis,
                localEpochDay(performedAtMillis, utcOffsetMillis)
            )
        val lastSetAt = lastSetAt(open)
        val rolled = localEpochDay(performedAtMillis, utcOffsetMillis) != open.localEpochDay
        val idle = performedAtMillis - lastSetAt >
            config.sessionInactivityWindow.inWholeMilliseconds
        return if (rolled || idle) {
            endWorkoutSession(open.id, lastSetAt)
            startWorkoutSession(
                performedAtMillis,
                localEpochDay(performedAtMillis, utcOffsetMillis)
            )
        } else {
            open
        }
    }

    /** The latest set already attached to [session], or the session start when it has none yet. */
    private suspend fun lastSetAt(session: WorkoutSession): Long =
        repository.lastSetBySession(session.id)?.performedAtMillis
            ?: session.startedAtMillis
}
