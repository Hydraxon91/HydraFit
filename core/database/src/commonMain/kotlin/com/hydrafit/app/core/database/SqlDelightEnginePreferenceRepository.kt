package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.userdata.settings.EnginePreferenceRepository

class SqlDelightEnginePreferenceRepository(database: HydraFitDatabase) :
    EnginePreferenceRepository {
    private val queries = database.plannerEngineQueries

    override suspend fun selectedEngine(): PlannerEngineId {
        val stored = queries.selectEngine().executeAsOneOrNull() ?: return DEFAULT_ENGINE
        return PlannerEngineId.entries.firstOrNull { it.name == stored } ?: DEFAULT_ENGINE
    }

    override suspend fun setEngine(engine: PlannerEngineId) {
        queries.upsertEngine(engine.name)
    }

    private companion object {
        val DEFAULT_ENGINE = PlannerEngineId.DETERMINISTIC
    }
}
