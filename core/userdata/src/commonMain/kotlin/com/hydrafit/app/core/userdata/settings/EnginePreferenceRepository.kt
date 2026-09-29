package com.hydrafit.app.core.userdata.settings

import com.hydrafit.app.core.domain.engine.PlannerEngineId
import kotlinx.coroutines.flow.Flow

interface EnginePreferenceRepository {
    suspend fun selectedEngine(): PlannerEngineId

    fun engineFlow(): Flow<PlannerEngineId>

    suspend fun setEngine(engine: PlannerEngineId)

    suspend fun selectedDaysPerWeek(): Int

    fun daysPerWeekFlow(): Flow<Int>

    suspend fun setDaysPerWeek(daysPerWeek: Int)

    suspend fun isWorkoutDataSharingEnabled(): Boolean

    fun workoutDataSharingFlow(): Flow<Boolean>

    suspend fun setWorkoutDataSharingEnabled(enabled: Boolean)
}
