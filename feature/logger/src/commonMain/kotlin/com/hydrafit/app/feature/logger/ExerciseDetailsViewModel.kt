package com.hydrafit.app.feature.logger

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.workout.ExercisePerformance
import com.hydrafit.app.core.domain.workout.GetExercisePerformanceUseCase
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** An exercise's muscles and recent history for the details card. */
data class ExerciseDetails(
    val primaryMuscles: List<MuscleGroup>,
    val secondaryMuscles: List<MuscleGroup>,
    val performance: ExercisePerformance
)

/**
 * Read-only state behind the exercise details card. It writes nothing, so opening or dismissing
 * the card cannot change a draft, a timer or any stored data. A failed read leaves no details and
 * the card still shows the instructions and demo.
 */
class ExerciseDetailsViewModel(
    private val getPerformance: GetExercisePerformanceUseCase,
    private val exerciseCatalog: ExerciseCatalog
) : ViewModel() {
    private val _details = MutableStateFlow<ExerciseDetails?>(null)
    val details: StateFlow<ExerciseDetails?> = _details.asStateFlow()

    private var loadedFor: String? = null

    fun load(exerciseId: String) {
        if (loadedFor == exerciseId) return
        loadedFor = exerciseId
        _details.value = null
        viewModelScope.launch {
            try {
                val exercise = exerciseCatalog.all().firstOrNull { it.id == exerciseId }
                val performance = getPerformance(exerciseId)
                if (loadedFor == exerciseId) {
                    _details.value = ExerciseDetails(
                        primaryMuscles = exercise?.primaryMuscles?.toList().orEmpty(),
                        secondaryMuscles = exercise?.secondaryMuscles?.toList().orEmpty(),
                        performance = performance
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                // No details: the card falls back to instructions and the demo.
            }
        }
    }
}
