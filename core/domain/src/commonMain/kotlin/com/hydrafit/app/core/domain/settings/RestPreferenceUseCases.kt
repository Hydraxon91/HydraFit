package com.hydrafit.app.core.domain.settings

import com.hydrafit.app.core.domain.engine.TrainingGoal
import kotlinx.coroutines.flow.Flow

class ObserveGlobalRestDurationUseCase(private val repository: RestPreferenceRepository) {
    operator fun invoke(): Flow<Long> = repository.globalDefaultSecondsFlow()
}

class SetGlobalRestDurationUseCase(private val repository: RestPreferenceRepository) {
    suspend operator fun invoke(seconds: Long) {
        require(seconds in MIN_REST_SECONDS..MAX_REST_SECONDS)
        repository.setGlobalDefaultSeconds(seconds)
    }
}

class ResolveRestDurationUseCase(private val repository: RestPreferenceRepository) {
    suspend operator fun invoke(exerciseId: String): Long =
        repository.exerciseOverrideSeconds(exerciseId) ?: repository.globalDefaultSeconds()
}

class SetExerciseRestDurationUseCase(private val repository: RestPreferenceRepository) {
    suspend operator fun invoke(exerciseId: String, seconds: Long) {
        require(exerciseId.isNotBlank())
        require(seconds in MIN_REST_SECONDS..MAX_REST_SECONDS)
        repository.setExerciseOverrideSeconds(exerciseId, seconds)
    }
}

class ClearExerciseRestDurationUseCase(private val repository: RestPreferenceRepository) {
    suspend operator fun invoke(exerciseId: String) {
        require(exerciseId.isNotBlank())
        repository.clearExerciseOverride(exerciseId)
    }
}

const val DEFAULT_REST_SECONDS = 120L

/**
 * The global rest a training goal starts with until the user saves their own. These are guidance, not
 * a prescription: heavy low-rep compounds are best supported with longer rests (about three minutes),
 * moderate loads with about two, and light high-rep work can recover in about a minute.
 */
fun defaultRestSecondsFor(goal: TrainingGoal): Long = when (goal) {
    TrainingGoal.STRENGTH -> 180L
    TrainingGoal.BALANCED, TrainingGoal.HYPERTROPHY -> DEFAULT_REST_SECONDS
    TrainingGoal.ENDURANCE -> 60L
}
const val MIN_REST_SECONDS = 1L
const val MAX_REST_SECONDS = 86_400L
