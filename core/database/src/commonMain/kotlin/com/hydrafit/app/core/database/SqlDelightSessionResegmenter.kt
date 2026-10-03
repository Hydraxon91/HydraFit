package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.workout.SessionResegmenter
import com.hydrafit.app.core.domain.workout.resegmentSessions

class SqlDelightSessionResegmenter(private val database: HydraFitDatabase) : SessionResegmenter {
    private val setQueries = database.workoutLogQueries
    private val sessionQueries = database.workoutSessionQueries

    override suspend fun resegmentAfterTimeCorrection(
        setId: Long,
        performedAtMillis: Long,
        utcOffsetMillis: Long
    ) {
        // Read, recompute, and write inside one transaction so the stored sessions can never be
        // observed interleaved or inconsistent with their sets.
        database.transaction {
            val sets = setQueries.selectAllSets().executeAsList().map { it.toDomain() }
            val sessions = sessionQueries.selectAllSessions().executeAsList().map { it.toDomain() }
            val resegmentation = resegmentSessions(
                sets = sets,
                sessions = sessions,
                correctedSetId = setId,
                performedAtMillis = performedAtMillis,
                utcOffsetMillis = utcOffsetMillis
            )
            setQueries.updateSetPerformedAt(performedAt = performedAtMillis, id = setId)
            resegmentation.assignments.forEach { (id, sessionId) ->
                setQueries.assignSession(sessionId = sessionId, id = id)
            }
            resegmentation.sessionBounds.forEach { (sessionId, bounds) ->
                sessionQueries.updateSessionBounds(
                    startedAtMillis = bounds.startedAtMillis,
                    endedAtMillis = bounds.endedAtMillis,
                    localEpochDay = bounds.localEpochDay,
                    id = sessionId
                )
            }
        }
    }
}
