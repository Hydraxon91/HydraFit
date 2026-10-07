package com.hydrafit.app.core.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.hydrafit.app.core.domain.workout.WorkoutSession
import com.hydrafit.app.core.domain.workout.WorkoutSessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SqlDelightWorkoutSessionRepository(private val database: HydraFitDatabase) :
    WorkoutSessionRepository {
    private val sessionQueries = database.workoutSessionQueries

    override suspend fun create(session: WorkoutSession) {
        sessionQueries.transaction {
            sessionQueries.insertSession(
                id = session.id,
                startedAtMillis = session.startedAtMillis,
                endedAtMillis = session.endedAtMillis,
                localEpochDay = session.localEpochDay
            )
            session.occurrenceId?.let { occurrenceId ->
                sessionQueries.assignOccurrence(occurrenceId = occurrenceId, id = session.id)
            }
        }
    }

    override suspend fun end(id: String, endedAtMillis: Long) {
        sessionQueries.endSession(endedAtMillis = endedAtMillis, id = id)
    }

    override suspend fun open(): WorkoutSession? =
        sessionQueries.selectOpenSession().executeAsOneOrNull()?.toDomain()

    override fun openFlow(): Flow<WorkoutSession?> =
        sessionQueries.selectOpenSession().asFlow().mapToOneOrNull(Dispatchers.Default)
            .map { it?.toDomain() }

    override suspend fun all(): List<WorkoutSession> =
        sessionQueries.selectAllSessions().executeAsList().map { it.toDomain() }
}
