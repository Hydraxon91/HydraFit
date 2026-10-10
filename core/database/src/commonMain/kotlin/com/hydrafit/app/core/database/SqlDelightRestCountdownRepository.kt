package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.workout.PersistedRestCountdown
import com.hydrafit.app.core.domain.workout.RestCountdownRepository

class SqlDelightRestCountdownRepository(database: HydraFitDatabase) : RestCountdownRepository {
    private val queries = database.restCountdownQueries

    override suspend fun load(): PersistedRestCountdown? =
        queries.selectPersisted().executeAsOneOrNull()?.let {
            PersistedRestCountdown(
                deadlineElapsedMillis = it.deadlineElapsedMillis,
                durationMillis = it.durationMillis,
                bootIdentity = it.bootIdentity,
                sessionId = it.sessionId,
                occurrenceId = it.occurrenceId,
                exerciseId = it.exerciseId
            )
        }

    override suspend fun save(countdown: PersistedRestCountdown) {
        queries.upsertPersisted(
            deadlineElapsedMillis = countdown.deadlineElapsedMillis,
            durationMillis = countdown.durationMillis,
            bootIdentity = countdown.bootIdentity,
            sessionId = countdown.sessionId,
            occurrenceId = countdown.occurrenceId,
            exerciseId = countdown.exerciseId
        )
    }

    override suspend fun clear() {
        queries.deletePersisted()
    }
}
