package com.hydrafit.app.core.userdata.settings

import kotlinx.coroutines.flow.Flow

interface GuidedWorkoutPreferenceRepository {
    suspend fun isGuidedWorkoutEnabled(): Boolean

    fun guidedWorkoutFlow(): Flow<Boolean>

    suspend fun setGuidedWorkoutEnabled(enabled: Boolean)
}
