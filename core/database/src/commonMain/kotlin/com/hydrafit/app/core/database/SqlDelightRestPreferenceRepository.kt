package com.hydrafit.app.core.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.hydrafit.app.core.domain.settings.DEFAULT_REST_SECONDS
import com.hydrafit.app.core.domain.settings.RestPreferenceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SqlDelightRestPreferenceRepository(database: HydraFitDatabase) : RestPreferenceRepository {
    private val queries = database.restPreferenceQueries

    override fun globalDefaultSecondsFlow(): Flow<Long> = queries.selectGlobalDefault()
        .asFlow()
        .mapToOneOrNull(Dispatchers.Default)
        .map { it ?: DEFAULT_REST_SECONDS }

    override suspend fun globalDefaultSeconds(): Long =
        queries.selectGlobalDefault().executeAsOneOrNull() ?: DEFAULT_REST_SECONDS

    override suspend fun setGlobalDefaultSeconds(seconds: Long) {
        queries.upsertGlobalDefault(seconds)
    }

    override suspend fun exerciseOverrideSeconds(exerciseId: String): Long? =
        queries.selectExerciseOverride(exerciseId).executeAsOneOrNull()

    override suspend fun setExerciseOverrideSeconds(exerciseId: String, seconds: Long) {
        queries.upsertExerciseOverride(exerciseId, exerciseId, seconds)
    }

    override suspend fun clearExerciseOverride(exerciseId: String) {
        queries.deleteExerciseOverride(exerciseId)
    }
}
