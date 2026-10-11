package com.hydrafit.app.core.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.hydrafit.app.core.userdata.settings.GuidedWorkoutPreferenceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SqlDelightGuidedWorkoutPreferenceRepository(database: HydraFitDatabase) :
    GuidedWorkoutPreferenceRepository {
    private val queries = database.plannerEngineQueries

    override suspend fun isGuidedWorkoutEnabled(): Boolean =
        queries.selectGuidedWorkoutEnabled().executeAsOneOrNull()?.let { it != 0L } ?: false

    override fun guidedWorkoutFlow(): Flow<Boolean> = queries.selectGuidedWorkoutEnabled()
        .asFlow()
        .mapToOneOrNull(Dispatchers.Default)
        .map { stored -> stored?.let { it != 0L } ?: false }

    override suspend fun setGuidedWorkoutEnabled(enabled: Boolean) {
        queries.insertIgnoreRow("DETERMINISTIC")
        queries.updateGuidedWorkoutEnabled(if (enabled) 1L else 0L)
    }
}
