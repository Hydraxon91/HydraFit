package com.hydrafit.app.core.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.TrainingGoal
import com.hydrafit.app.core.userdata.settings.TrainingGoalRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SqlDelightTrainingGoalRepository(database: HydraFitDatabase) : TrainingGoalRepository {
    private val queries = database.plannerEngineQueries

    override suspend fun selectedGoal(): TrainingGoal =
        queries.selectTrainingGoal().executeAsOneOrNull()?.toTrainingGoal() ?: DEFAULT_GOAL

    override fun goalFlow(): Flow<TrainingGoal> = queries.selectTrainingGoal()
        .asFlow()
        .mapToOneOrNull(Dispatchers.Default)
        .map { stored -> stored?.toTrainingGoal() ?: DEFAULT_GOAL }

    override suspend fun setGoal(goal: TrainingGoal) {
        queries.insertIgnoreRow(PlannerEngineId.DETERMINISTIC.name)
        queries.updateTrainingGoal(goal.name)
    }

    private fun String.toTrainingGoal(): TrainingGoal =
        TrainingGoal.entries.firstOrNull { it.name == this } ?: DEFAULT_GOAL

    private companion object {
        val DEFAULT_GOAL = TrainingGoal.BALANCED
    }
}
