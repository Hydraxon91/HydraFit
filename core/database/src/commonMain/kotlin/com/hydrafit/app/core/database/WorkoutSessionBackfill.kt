package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.fatigue.FatigueConfig
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.time.localEpochDay

/**
 * One-time, idempotent backfill of explicit session ids for legacy `workoutSet` rows.
 *
 * Only rows whose `sessionId` is null are read and written; rows that already carry a session id
 * are never modified or merged, and a second run is a no-op. Every null row is segmented with the
 * same rule the pre-S3 `FatigueCalculator` used for a runtime reset: working sets only, ordered by
 * `performedAt`, and a new session whenever the gap between consecutive working rows is greater
 * than or equal to [FatigueConfig.sessionGap]. Equal timestamps share a session, the whole log is
 * one timeline (not per exercise), and local midnight is not a boundary.
 *
 * Warm-ups do not affect the gap rule (the calculator ignores them), but each is still attached to
 * a session: the nearest following working set when that is within the gap, otherwise the nearest
 * preceding working set when that is within the gap, otherwise a warm-up-only session (consecutive
 * isolated warm-ups are grouped by the same gap rule). Backfilled sessions are written closed
 * (`endedAt` = the latest assigned set) so no legacy session can capture later live sets. The
 * deterministic id is `backfill-<row id of the session's earliest assigned set>` (ordered by
 * `performedAt`, `id`), which is stable across reruns. `localEpochDay` is derived from the platform
 * UTC offset available now, so it may differ from the offset in effect when the set was logged.
 *
 * Because the Logger does not stamp session ids until S4, later launches may again see null rows.
 * Those null rows are segmented among themselves only and never join or reopen an earlier session,
 * so a null row within the gap of a previous backfilled session becomes its own new session.
 */
class WorkoutSessionBackfill(
    private val database: HydraFitDatabase,
    private val timeProvider: TimeProvider,
    private val config: FatigueConfig = FatigueConfig()
) {
    fun backfill() {
        val rows = database.workoutLogQueries.selectUnsegmentedSets().executeAsList()
        if (rows.isEmpty()) return

        val sessions = segmentWorkingSets(rows.filter { it.isWarmup == 0L })
        attachWarmups(sessions, rows.filter { it.isWarmup != 0L })

        val offsetMillis = timeProvider.utcOffsetMillis()
        database.transaction {
            sessions.forEach { session ->
                val sets = (session.working + session.warmups)
                    .sortedWith(compareBy({ it.performedAt }, { it.id }))
                val first = sets.first()
                val last = sets.last()
                val id = "backfill-${first.id}"
                database.workoutSessionQueries.insertSession(
                    id = id,
                    startedAtMillis = first.performedAt,
                    endedAtMillis = last.performedAt,
                    localEpochDay = localEpochDay(first.performedAt, offsetMillis)
                )
                sets.forEach { row ->
                    database.workoutLogQueries.assignSession(sessionId = id, id = row.id)
                }
            }
        }
    }

    private fun segmentWorkingSets(working: List<WorkoutSet>): MutableList<SessionGroup> {
        val sessions = mutableListOf<SessionGroup>()
        var previousMillis = 0L
        for (row in working) {
            val startsNewSession = sessions.isEmpty() ||
                row.performedAt - previousMillis >= config.sessionGap.inWholeMilliseconds
            if (startsNewSession) sessions.add(SessionGroup())
            sessions.last().working.add(row)
            previousMillis = row.performedAt
        }
        return sessions
    }

    private fun attachWarmups(sessions: MutableList<SessionGroup>, warmups: List<WorkoutSet>) {
        if (warmups.isEmpty()) return
        val gapMillis = config.sessionGap.inWholeMilliseconds
        val working = sessions.flatMap { it.working }
            .sortedWith(compareBy({ it.performedAt }, { it.id }))
        val sessionOfSet = HashMap<Long, SessionGroup>()
        sessions.forEach { session -> session.working.forEach { sessionOfSet[it.id] = session } }

        val isolated = mutableListOf<WorkoutSet>()
        for (warmup in warmups) {
            val next = working.firstOrNull { it.performedAt >= warmup.performedAt }
            val previous = working.lastOrNull { it.performedAt <= warmup.performedAt }
            when {
                next != null && next.performedAt - warmup.performedAt < gapMillis ->
                    sessionOfSet.getValue(next.id).warmups.add(warmup)
                previous != null && warmup.performedAt - previous.performedAt < gapMillis ->
                    sessionOfSet.getValue(previous.id).warmups.add(warmup)
                else -> isolated.add(warmup)
            }
        }
        sessions += segmentWarmupOnly(isolated)
    }

    private fun segmentWarmupOnly(isolated: List<WorkoutSet>): List<SessionGroup> {
        val sessions = mutableListOf<SessionGroup>()
        var previousMillis = 0L
        for (row in isolated) {
            val startsNewSession = sessions.isEmpty() ||
                row.performedAt - previousMillis >= config.sessionGap.inWholeMilliseconds
            if (startsNewSession) sessions.add(SessionGroup())
            sessions.last().warmups.add(row)
            previousMillis = row.performedAt
        }
        return sessions
    }

    private class SessionGroup {
        val working = mutableListOf<WorkoutSet>()
        val warmups = mutableListOf<WorkoutSet>()
    }
}
