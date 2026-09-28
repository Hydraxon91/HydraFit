package com.hydrafit.app.core.domain.engine

interface EngineAvailability {
    fun availableEngines(): List<PlannerEngineId>
}
