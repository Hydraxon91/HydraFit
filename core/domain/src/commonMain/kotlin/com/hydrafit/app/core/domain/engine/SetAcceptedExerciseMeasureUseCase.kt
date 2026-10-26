package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.routine.PrescriptionBounds
import com.hydrafit.app.core.domain.workout.requireValidSetMeasurement

/**
 * Sets one accepted-plan slot's measure in place (the plan keeps its id, acceptance time and week):
 * an UPDATE, not a re-accept. The slot's exercise, sets and load are preserved — this changes only
 * how the work is measured: a positive rep count or positive elapsed whole seconds, exactly one of
 * them, never both and never neither.
 *
 * Bounds are validated before anything is written. [reps] must be in
 * [PrescriptionBounds.MIN_REPS]..[PrescriptionBounds.MAX_REPS] and [durationSeconds] must be positive
 * whole seconds. A missing day/position is a no-op that returns null, mirroring
 * [SubstituteExerciseUseCase].
 */
class SetAcceptedExerciseMeasureUseCase(private val planHistory: PlanHistoryRepository) {

    /**
     * Persists [reps] or [durationSeconds] on one slot of [plan] and returns the updated slot, or null
     * when the day/position does not exist. Throws [IllegalArgumentException] when the measure is out
     * of bounds or is not exactly one of the two.
     */
    suspend operator fun invoke(
        plan: AcceptedPlan,
        dayIndex: Int,
        position: Int,
        reps: Int?,
        durationSeconds: Int?,
        distanceMeters: Int? = null
    ): AcceptedExercise? {
        requireValidSetMeasurement(reps, durationSeconds, distanceMeters)
        if (reps != null && reps > PrescriptionBounds.MAX_REPS) {
            throw IllegalArgumentException(
                "Reps must be between ${PrescriptionBounds.MIN_REPS} and ${PrescriptionBounds.MAX_REPS}"
            )
        }
        if (distanceMeters != null &&
            distanceMeters !in
            PrescriptionBounds.MIN_DISTANCE_METERS..PrescriptionBounds.MAX_DISTANCE_METERS
        ) {
            throw IllegalArgumentException("Distance must be within the prescription bounds")
        }
        val day = plan.days.firstOrNull { it.dayIndex == dayIndex } ?: return null
        val entry = day.exercises.getOrNull(position) ?: return null
        planHistory.setMeasure(
            planId = plan.id,
            dayIndex = dayIndex,
            position = position,
            reps = reps,
            durationSeconds = durationSeconds,
            distanceMeters = distanceMeters
        )
        return entry.copy(
            reps = reps,
            durationSeconds = durationSeconds,
            distanceMeters = distanceMeters
        )
    }
}
