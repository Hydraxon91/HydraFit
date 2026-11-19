package com.hydrafit.app.core.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.hydrafit.app.core.domain.engine.TrainingGoal
import com.hydrafit.app.core.domain.settings.RestPreferenceRepository
import com.hydrafit.app.core.domain.settings.defaultRestSecondsFor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class SqlDelightRestPreferenceRepository(database: HydraFitDatabase) : RestPreferenceRepository {
    private val queries = database.restPreferenceQueries
    private val goalQueries = database.plannerEngineQueries

    /** A saved default wins; until one is saved the default follows the selected training goal. */
    override fun globalDefaultSecondsFlow(): Flow<Long> = combine(
        queries.selectGlobalDefault().asFlow().mapToOneOrNull(Dispatchers.Default),
        goalQueries.selectTrainingGoal().asFlow().mapToOneOrNull(Dispatchers.Default)
    ) { saved, goalName -> saved ?: defaultRestSecondsFor(goalName.toTrainingGoal()) }

    override suspend fun globalDefaultSeconds(): Long =
        queries.selectGlobalDefault().executeAsOneOrNull() ?: goalRestDefaultSeconds(goalQueries)

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

private fun String?.toTrainingGoal(): TrainingGoal =
    TrainingGoal.entries.firstOrNull { it.name == this } ?: TrainingGoal.BALANCED

/** The rest default for the selected training goal, used while no global default is saved. */
internal fun goalRestDefaultSeconds(goalQueries: PlannerEngineQueries): Long =
    defaultRestSecondsFor(goalQueries.selectTrainingGoal().executeAsOneOrNull().toTrainingGoal())
