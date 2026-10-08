package com.hydrafit.app.core.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.hydrafit.app.core.domain.equipment.ExerciseExclusion
import com.hydrafit.app.core.userdata.equipment.ExerciseExclusionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SqlDelightExerciseExclusionRepository(database: HydraFitDatabase) :
    ExerciseExclusionRepository {
    private val queries = database.exerciseExclusionQueries

    override fun observe(): Flow<List<ExerciseExclusion>> = queries.selectAll()
        .asFlow()
        .mapToList(Dispatchers.Default)
        .map { rows -> rows.map { ExerciseExclusion(it.exerciseId, it.expiresAt) } }

    override suspend fun exclusion(exerciseId: String): ExerciseExclusion? =
        queries.selectById(exerciseId).executeAsOneOrNull()
            ?.let { ExerciseExclusion(it.exerciseId, it.expiresAt) }

    override suspend fun set(exclusion: ExerciseExclusion) {
        queries.upsert(
            exerciseId = exclusion.exerciseId,
            expiresAt = exclusion.expiresAtMillis
        )
    }

    override suspend fun clear(exerciseId: String) {
        queries.deleteById(exerciseId)
    }
}
