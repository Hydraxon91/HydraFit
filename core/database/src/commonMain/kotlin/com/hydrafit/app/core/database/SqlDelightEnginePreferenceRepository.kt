package com.hydrafit.app.core.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.userdata.settings.EnginePreferenceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SqlDelightEnginePreferenceRepository(database: HydraFitDatabase) :
    EnginePreferenceRepository {
    private val queries = database.plannerEngineQueries

    override suspend fun selectedEngine(): PlannerEngineId {
        val stored = queries.selectEngine().executeAsOneOrNull() ?: return DEFAULT_ENGINE
        return stored.toEngineId()
    }

    override fun engineFlow(): Flow<PlannerEngineId> = queries.selectEngine()
        .asFlow()
        .mapToOneOrNull(Dispatchers.Default)
        .map { stored -> stored?.toEngineId() ?: DEFAULT_ENGINE }

    override suspend fun setEngine(engine: PlannerEngineId) {
        queries.insertIgnoreRow(DEFAULT_ENGINE.name)
        queries.updateEngine(engine.name)
    }

    override suspend fun selectedDaysPerWeek(): Int {
        val stored = queries.selectDaysPerWeek().executeAsOneOrNull()?.toInt() ?: DEFAULT_DAYS
        return stored.coerceIn(MIN_DAYS, MAX_DAYS)
    }

    override fun daysPerWeekFlow(): Flow<Int> = queries.selectDaysPerWeek()
        .asFlow()
        .mapToOneOrNull(Dispatchers.Default)
        .map { stored -> (stored?.toInt() ?: DEFAULT_DAYS).coerceIn(MIN_DAYS, MAX_DAYS) }

    override suspend fun setDaysPerWeek(daysPerWeek: Int) {
        queries.insertIgnoreRow(DEFAULT_ENGINE.name)
        queries.updateDaysPerWeek(daysPerWeek.coerceIn(MIN_DAYS, MAX_DAYS).toLong())
    }

    private fun String.toEngineId(): PlannerEngineId =
        PlannerEngineId.entries.firstOrNull { it.name == this } ?: DEFAULT_ENGINE

    private companion object {
        val DEFAULT_ENGINE = PlannerEngineId.DETERMINISTIC
        const val DEFAULT_DAYS = 4
        const val MIN_DAYS = 2
        const val MAX_DAYS = 6
    }
}
