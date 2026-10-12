package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.workout.NOTE_MAX_LENGTH
import com.hydrafit.app.core.domain.workout.SessionNoteRepository

class SqlDelightSessionNoteRepository(database: HydraFitDatabase) : SessionNoteRepository {
    private val database = database
    private val queries = database.sessionNoteQueries

    override suspend fun note(sessionId: String): String? =
        queries.selectBySessionId(sessionId).executeAsOneOrNull()?.note

    override suspend fun save(sessionId: String, text: String) {
        require(text.length <= NOTE_MAX_LENGTH) {
            "Session notes must not exceed $NOTE_MAX_LENGTH characters"
        }
        database.transaction {
            val sessionExists = database.workoutSessionQueries.selectSessionById(sessionId)
                .executeAsOneOrNull() != null
            require(sessionExists) {
                "Workout session does not exist: $sessionId"
            }
            queries.upsert(sessionId = sessionId, note = text)
        }
    }

    override suspend fun clear(sessionId: String) {
        save(sessionId, "")
    }
}
