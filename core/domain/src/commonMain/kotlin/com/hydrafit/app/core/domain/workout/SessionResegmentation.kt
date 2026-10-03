package com.hydrafit.app.core.domain.workout

import com.hydrafit.app.core.domain.time.localEpochDay

/** A session's bounds and local day, recomputed from the sets it ends up containing. */
data class SessionBounds(
    val startedAtMillis: Long,
    val endedAtMillis: Long?,
    val localEpochDay: Long
)

/** The set reassignments and session-bound updates a time correction must apply atomically. */
data class SessionResegmentation(
    val assignments: Map<Long, String>,
    val sessionBounds: Map<String, SessionBounds>
) {
    companion object {
        val EMPTY = SessionResegmentation(emptyMap(), emptyMap())
    }
}

/**
 * Re-segments the sessions touched by a time correction so they stay non-interleaved and consistent.
 *
 * The corrected set snaps to the nearest existing session (by distance to its set-time range, with
 * no same-day restriction and no session creation or deletion), then every session that lost or
 * gained a set has its `startedAt`/`endedAt`/`localEpochDay` recomputed from its sets. Open sessions
 * stay open; a session left with no sets keeps its existing bounds.
 */
fun resegmentSessions(
    sets: List<WorkoutSet>,
    sessions: List<WorkoutSession>,
    correctedSetId: Long,
    performedAtMillis: Long,
    utcOffsetMillis: Long
): SessionResegmentation {
    val corrected = sets.firstOrNull { it.id == correctedSetId }
    if (corrected == null || sessions.isEmpty()) return SessionResegmentation.EMPTY

    // The corrected set carries its new time from here on, so ranges and bounds reflect the edit.
    val updatedSets = sets.map { set ->
        if (set.id == correctedSetId) set.copy(performedAtMillis = performedAtMillis) else set
    }
    // The moved set must not count toward its old session's range, or that session would always be
    // distance 0 and win the snapshot that is supposed to move it.
    val referenceSets = updatedSets.filterNot { it.id == correctedSetId }
    val target = nearestSession(sessions, referenceSets, performedAtMillis, corrected.sessionId)
    val assignments = if (corrected.sessionId == target.id) {
        emptyMap()
    } else {
        mapOf(correctedSetId to target.id)
    }

    val membership = updatedSets.associate { set ->
        set.id to if (set.id == correctedSetId) target.id else set.sessionId
    }
    val affected = setOfNotNull(corrected.sessionId, target.id)
    val bounds = affected.mapNotNull { sessionId ->
        val session = sessions.firstOrNull { it.id == sessionId } ?: return@mapNotNull null
        val sessionSets = updatedSets.filter { membership[it.id] == sessionId }
        if (sessionSets.isEmpty()) return@mapNotNull null
        val startedAt = sessionSets.minOf { it.performedAtMillis }
        sessionId to SessionBounds(
            startedAtMillis = startedAt,
            endedAtMillis = if (session.endedAtMillis == null) {
                null
            } else {
                sessionSets.maxOf { it.performedAtMillis }
            },
            localEpochDay = localEpochDay(startedAt, utcOffsetMillis)
        )
    }.toMap()

    return SessionResegmentation(assignments, bounds)
}

private fun nearestSession(
    sessions: List<WorkoutSession>,
    sets: List<WorkoutSet>,
    performedAtMillis: Long,
    preferredSessionId: String?
): WorkoutSession = sessions.minWith(
    compareBy<WorkoutSession>(
        { session -> distanceToSession(session, sets, performedAtMillis) },
        { session -> if (session.id == preferredSessionId) 0 else 1 }
    )
)

private fun distanceToSession(
    session: WorkoutSession,
    sets: List<WorkoutSet>,
    performedAtMillis: Long
): Long {
    val times = sets.filter { it.sessionId == session.id }.map { it.performedAtMillis }
    val first = times.minOrNull() ?: session.startedAtMillis
    val last = times.maxOrNull() ?: (session.endedAtMillis ?: session.startedAtMillis)
    return when {
        performedAtMillis < first -> first - performedAtMillis
        performedAtMillis > last -> performedAtMillis - last
        else -> 0L
    }
}
