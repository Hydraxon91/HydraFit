package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.settings.ClearExerciseRestDurationUseCase
import com.hydrafit.app.core.domain.settings.ResolveRestDurationUseCase
import com.hydrafit.app.core.domain.settings.RestPreferenceRepository
import com.hydrafit.app.core.domain.settings.SetExerciseRestDurationUseCase
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.userdata.settings.GuidedWorkoutPreferenceRepository
import com.hydrafit.app.core.userdata.settings.WeightUnitRepository
import kotlinx.coroutines.flow.Flow

/**
 * The user preferences the Logger observes (display units and whether guided mode is on) plus
 * rest-duration resolution: resolve/set/clear of per-exercise overrides is forwarded to the domain
 * use cases. Grouped by responsibility so the ViewModel stays within its dependency budget; it owns
 * no state of its own.
 */
class WorkoutLoggerSettings(
    private val weightUnitRepository: WeightUnitRepository,
    private val guidedWorkoutPreferenceRepository: GuidedWorkoutPreferenceRepository,
    private val restPreferenceRepository: RestPreferenceRepository,
    private val resolveRestDurationUseCase: ResolveRestDurationUseCase =
        ResolveRestDurationUseCase(restPreferenceRepository),
    private val setExerciseRestDurationUseCase: SetExerciseRestDurationUseCase =
        SetExerciseRestDurationUseCase(restPreferenceRepository),
    private val clearExerciseRestDurationUseCase: ClearExerciseRestDurationUseCase =
        ClearExerciseRestDurationUseCase(restPreferenceRepository)
) {
    fun weightUnitFlow(): Flow<WeightUnit> = weightUnitRepository.unitFlow()

    fun guidedWorkoutFlow(): Flow<Boolean> = guidedWorkoutPreferenceRepository.guidedWorkoutFlow()

    suspend fun restDurationSeconds(exerciseId: String): Long =
        resolveRestDurationUseCase(exerciseId)

    suspend fun globalRestDurationSeconds(): Long = restPreferenceRepository.globalDefaultSeconds()

    suspend fun hasExerciseRestDurationOverride(exerciseId: String): Boolean =
        restPreferenceRepository.exerciseOverrideSeconds(exerciseId) != null

    suspend fun setExerciseRestDuration(exerciseId: String, seconds: Long) =
        setExerciseRestDurationUseCase(exerciseId, seconds)

    suspend fun clearExerciseRestDuration(exerciseId: String) =
        clearExerciseRestDurationUseCase(exerciseId)
}
