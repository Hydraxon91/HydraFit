package com.hydrafit.app.core.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.hydrafit.app.core.domain.equipment.ExercisePreference
import com.hydrafit.app.core.userdata.equipment.ExercisePreferenceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SqlDelightExercisePreferenceRepository(database: HydraFitDatabase) :
    ExercisePreferenceRepository {
    private val queries = database.exercisePreferenceQueries

    override fun observe(): Flow<Map<String, ExercisePreference>> = queries.selectAll()
        .asFlow()
        .mapToList(Dispatchers.Default)
        .map { rows -> rows.associate { it.exerciseId to it.preference.toPreference() } }

    override suspend fun preference(exerciseId: String): ExercisePreference =
        queries.selectById(exerciseId).executeAsOneOrNull()?.toPreference()
            ?: ExercisePreference.NEUTRAL

    override suspend fun set(exerciseId: String, preference: ExercisePreference) {
        queries.upsert(exerciseId = exerciseId, preference = preference.name)
    }

    private fun String.toPreference(): ExercisePreference =
        ExercisePreference.entries.firstOrNull { it.name == this } ?: ExercisePreference.NEUTRAL
}
