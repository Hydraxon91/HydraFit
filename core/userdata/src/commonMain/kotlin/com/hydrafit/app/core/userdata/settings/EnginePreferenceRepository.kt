package com.hydrafit.app.core.userdata.settings

import com.hydrafit.app.core.domain.engine.PlannerEngineId

interface EnginePreferenceRepository {
    suspend fun selectedEngine(): PlannerEngineId

    suspend fun setEngine(engine: PlannerEngineId)
}
