package com.hydrafit.app.core.userdata.settings

import com.hydrafit.app.core.domain.unit.DistanceUnit
import kotlinx.coroutines.flow.Flow

/** The user's preferred distance display unit. Distances are stored in whole metres. */
interface DistanceUnitRepository {
    suspend fun selectedUnit(): DistanceUnit

    fun unitFlow(): Flow<DistanceUnit>

    suspend fun setUnit(unit: DistanceUnit)
}
