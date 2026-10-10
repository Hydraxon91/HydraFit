package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.settings.RestPreferenceRepository
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.userdata.settings.GuidedWorkoutPreferenceRepository
import com.hydrafit.app.core.userdata.settings.WeightUnitRepository
import kotlinx.coroutines.flow.Flow

/**
 * The read-only user preferences the Logger observes: display units and whether guided mode is on.
 * Pure delegation grouped by responsibility so the ViewModel stays within its dependency budget; it
 * owns no state of its own.
 */
class WorkoutLoggerSettings(
    private val weightUnitRepository: WeightUnitRepository,
    private val guidedWorkoutPreferenceRepository: GuidedWorkoutPreferenceRepository,
    private val restPreferenceRepository: RestPreferenceRepository
) {
    fun weightUnitFlow(): Flow<WeightUnit> = weightUnitRepository.unitFlow()

    fun guidedWorkoutFlow(): Flow<Boolean> = guidedWorkoutPreferenceRepository.guidedWorkoutFlow()

    suspend fun restDurationSeconds(exerciseId: String): Long =
        restPreferenceRepository.exerciseOverrideSeconds(exerciseId)
            ?: restPreferenceRepository.globalDefaultSeconds()

    suspend fun globalRestDurationSeconds(): Long = restPreferenceRepository.globalDefaultSeconds()

    suspend fun hasExerciseRestDurationOverride(exerciseId: String): Boolean =
        restPreferenceRepository.exerciseOverrideSeconds(exerciseId) != null

    suspend fun setExerciseRestDuration(exerciseId: String, seconds: Long) =
        restPreferenceRepository.setExerciseOverrideSeconds(exerciseId, seconds)

    suspend fun clearExerciseRestDuration(exerciseId: String) =
        restPreferenceRepository.clearExerciseOverride(exerciseId)
}
