package com.hydrafit.app.core.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.hydrafit.app.core.domain.engine.PersonalRecord
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.userdata.equipment.PersonalRecordRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SqlDelightPersonalRecordRepository(
    database: HydraFitDatabase,
    private val timeProvider: TimeProvider
) : PersonalRecordRepository {
    private val queries = database.personalRecordQueries

    override fun observe(): Flow<List<PersonalRecord>> = queries.selectAll()
        .asFlow()
        .mapToList(Dispatchers.Default)
        .map { rows ->
            rows.map {
                PersonalRecord(
                    it.exerciseId,
                    it.weightKg,
                    it.reps.toInt(),
                    decodeLoadKind(it.loadKind)
                )
            }
        }

    override suspend fun set(record: PersonalRecord) {
        queries.upsert(
            exerciseId = record.exerciseId,
            weightKg = record.weightKg,
            reps = record.reps.toLong(),
            updatedAt = timeProvider.nowMillis(),
            loadKind = record.loadKind.name
        )
    }

    override suspend fun clear(exerciseId: String) {
        queries.deleteById(exerciseId)
    }
}
