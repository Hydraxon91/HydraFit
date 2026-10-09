package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.workout.SessionResegmenter
import com.hydrafit.app.core.domain.workout.WorkoutLoadPolicy
import com.hydrafit.app.core.domain.workout.WorkoutSetCorrection
import com.hydrafit.app.core.domain.workout.resegmentSessions

class SqlDelightSessionResegmenter(private val database: HydraFitDatabase) : SessionResegmenter {
    private val setQueries = database.workoutLogQueries
    private val sessionQueries = database.workoutSessionQueries

    override suspend fun resegmentAfterSetCorrection(
        setId: Long,
        correction: WorkoutSetCorrection,
        utcOffsetMillis: Long
    ) {
        database.transaction {
            val set = requireNotNull(setQueries.selectSetById(setId).executeAsOneOrNull()) {
                "That set no longer exists"
            }.toDomain()
            WorkoutLoadPolicy.validateShape(set.loadKind, correction.weightKg)
            if (set.performedAtMillis != correction.performedAtMillis) {
                resegmentTime(setId, correction.performedAtMillis, utcOffsetMillis)
            }
            setQueries.updateSetValues(
                reps = correction.reps.toLong(),
                weightKg = correction.weightKg,
                rir = correction.rir?.toLong(),
                id = setId
            )
        }
    }

    override suspend fun resegmentAfterTimeCorrection(
        setId: Long,
        performedAtMillis: Long,
        utcOffsetMillis: Long
    ) {
        // Read, recompute, and write inside one transaction so the stored sessions can never be
        // observed interleaved or inconsistent with their sets.
        database.transaction {
            resegmentTime(setId, performedAtMillis, utcOffsetMillis)
        }
    }

    private fun resegmentTime(setId: Long, performedAtMillis: Long, utcOffsetMillis: Long) {
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
