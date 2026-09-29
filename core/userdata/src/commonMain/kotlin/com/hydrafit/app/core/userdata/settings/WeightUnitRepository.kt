package com.hydrafit.app.core.userdata.settings

import com.hydrafit.app.core.domain.unit.WeightUnit
import kotlinx.coroutines.flow.Flow

/** The user's preferred weight unit for display and entry. Weights are stored in kilograms. */
interface WeightUnitRepository {
    suspend fun selectedUnit(): WeightUnit

    fun unitFlow(): Flow<WeightUnit>

    suspend fun setUnit(unit: WeightUnit)
}
