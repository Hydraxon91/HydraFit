package com.hydrafit.app.core.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.userdata.settings.WeightUnitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SqlDelightWeightUnitRepository(database: HydraFitDatabase) : WeightUnitRepository {
    private val queries = database.plannerEngineQueries

    override suspend fun selectedUnit(): WeightUnit =
        queries.selectWeightUnit().executeAsOneOrNull()?.toWeightUnit() ?: DEFAULT_UNIT

    override fun unitFlow(): Flow<WeightUnit> = queries.selectWeightUnit()
        .asFlow()
        .mapToOneOrNull(Dispatchers.Default)
        .map { stored -> stored?.toWeightUnit() ?: DEFAULT_UNIT }

    override suspend fun setUnit(unit: WeightUnit) {
        queries.insertIgnoreRow(PlannerEngineId.DETERMINISTIC.name)
        queries.updateWeightUnit(unit.name)
    }

    private fun String.toWeightUnit(): WeightUnit =
        WeightUnit.entries.firstOrNull { it.name == this } ?: DEFAULT_UNIT

    private companion object {
        val DEFAULT_UNIT = WeightUnit.KG
    }
}
