package com.hydrafit.app.core.domain.settings

import kotlinx.coroutines.flow.Flow

/** Shared user-owned rest durations: one global default and optional exercise overrides. */
interface RestPreferenceRepository {
    fun globalDefaultSecondsFlow(): Flow<Long>

    suspend fun globalDefaultSeconds(): Long

    suspend fun setGlobalDefaultSeconds(seconds: Long)

    suspend fun exerciseOverrideSeconds(exerciseId: String): Long?

    suspend fun setExerciseOverrideSeconds(exerciseId: String, seconds: Long)

    suspend fun clearExerciseOverride(exerciseId: String)
}
