package com.hydrafit.app.core.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.hydrafit.app.core.domain.workout.PersistedRestCountdown
import com.hydrafit.app.core.domain.workout.RestCountdownRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map

class SqlDelightRestCountdownRepository(database: HydraFitDatabase) : RestCountdownRepository {
    private val queries = database.restCountdownQueries

    override fun observe() = queries.selectPersisted().asFlow()
        .mapToOneOrNull(Dispatchers.Default)
        .map { it?.toCountdown() }

    override suspend fun load(): PersistedRestCountdown? =
        queries.selectPersisted().executeAsOneOrNull()?.toCountdown()

    private fun RestCountdown.toCountdown() = PersistedRestCountdown(
        deadlineElapsedMillis = deadlineElapsedMillis,
        durationMillis = durationMillis,
        bootIdentity = bootIdentity,
        sessionId = sessionId,
        occurrenceId = occurrenceId,
        exerciseId = exerciseId
    )

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
