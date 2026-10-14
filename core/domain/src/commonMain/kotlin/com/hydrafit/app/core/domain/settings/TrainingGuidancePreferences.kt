package com.hydrafit.app.core.domain.settings

import kotlinx.coroutines.flow.Flow

data class TrainingGuidancePreferences(
    val restAlertsEnabled: Boolean = false,
    val shortRestCoachingEnabled: Boolean = false,
    val restCountdownEnabled: Boolean = false,
    val restCompletionSignalEnabled: Boolean = false
)

interface TrainingGuidancePreferenceRepository {
    fun observe(): Flow<TrainingGuidancePreferences>

    suspend fun get(): TrainingGuidancePreferences

    suspend fun setRestAlertsEnabled(enabled: Boolean)

    suspend fun setShortRestCoachingEnabled(enabled: Boolean)

    suspend fun setRestCountdownEnabled(enabled: Boolean)

    suspend fun setRestCompletionSignalEnabled(enabled: Boolean)
}
