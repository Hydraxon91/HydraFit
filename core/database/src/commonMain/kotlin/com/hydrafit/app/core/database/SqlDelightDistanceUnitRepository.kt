package com.hydrafit.app.core.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.unit.DistanceUnit
import com.hydrafit.app.core.userdata.settings.DistanceUnitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SqlDelightDistanceUnitRepository(database: HydraFitDatabase) : DistanceUnitRepository {
    private val queries = database.plannerEngineQueries

    override suspend fun selectedUnit(): DistanceUnit =
        queries.selectDistanceUnit().executeAsOneOrNull()?.toDistanceUnit() ?: DEFAULT_UNIT

    override fun unitFlow(): Flow<DistanceUnit> = queries.selectDistanceUnit()
        .asFlow()
        .mapToOneOrNull(Dispatchers.Default)
        .map { stored -> stored?.toDistanceUnit() ?: DEFAULT_UNIT }

    override suspend fun setUnit(unit: DistanceUnit) {
        queries.insertIgnoreRow(PlannerEngineId.DETERMINISTIC.name)
        queries.updateDistanceUnit(unit.name)
    }

    private fun String.toDistanceUnit(): DistanceUnit =
        DistanceUnit.entries.firstOrNull { it.name == this } ?: DEFAULT_UNIT

    private companion object {
        val DEFAULT_UNIT = DistanceUnit.METERS
    }
}
