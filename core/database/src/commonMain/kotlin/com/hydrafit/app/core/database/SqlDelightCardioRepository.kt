package com.hydrafit.app.core.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.hydrafit.app.core.domain.cardio.CardioActivity
import com.hydrafit.app.core.domain.cardio.CardioActivityType
import com.hydrafit.app.core.domain.cardio.CardioRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Manual cardio storage. It shares no table, transaction or read with resistance logging, so a cardio
 * write can never change a set count, session boundary or fatigue input.
 */
class SqlDelightCardioRepository(private val database: HydraFitDatabase) : CardioRepository {
    private val queries = database.cardioActivityQueries

    override fun activitiesFlow(): Flow<List<CardioActivity>> =
        queries.selectAll().asFlow().mapToList(Dispatchers.Default)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun all(): List<CardioActivity> = withContext(Dispatchers.Default) {
        queries.selectAll().executeAsList().map { it.toDomain() }
    }

    override suspend fun add(activity: CardioActivity): Long = withContext(Dispatchers.Default) {
        activity.requireValid()
        require(queries.selectByIdentity(activity.identity).executeAsOneOrNull() == null) {
            "That cardio activity is already recorded"
        }
        queries.transactionWithResult {
            queries.insertActivity(
                identity = activity.identity,
                type = activity.type.name,
                startedAt = activity.startedAtMillis,
                durationSeconds = activity.durationSeconds.toLong(),
                distanceMeters = activity.distanceMeters?.toLong(),
                note = activity.note
            )
            queries.lastInsertedId().executeAsOne()
        }
    }

    override suspend fun update(activity: CardioActivity): Unit = withContext(Dispatchers.Default) {
        activity.requireValid()
        require(activity.id > 0) { "Editing a cardio activity needs its stored id" }
        queries.updateActivity(
            type = activity.type.name,
            startedAt = activity.startedAtMillis,
            durationSeconds = activity.durationSeconds.toLong(),
            distanceMeters = activity.distanceMeters?.toLong(),
            note = activity.note,
            id = activity.id
        )
    }

    override suspend fun delete(id: Long): Unit = withContext(Dispatchers.Default) {
        queries.deleteActivity(id)
    }

    override suspend fun byIdentity(identity: String): CardioActivity? =
        withContext(Dispatchers.Default) {
            queries.selectByIdentity(identity).executeAsOneOrNull()?.toDomain()
        }

    private fun com.hydrafit.app.core.database.CardioActivity.toDomain(): CardioActivity =
        CardioActivity(
            id = id,
            identity = identity,
            type = CardioActivityType.valueOf(type),
            startedAtMillis = startedAt,
            durationSeconds = durationSeconds.toInt(),
            distanceMeters = distanceMeters?.toInt(),
            note = note
        )
}
