package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.routine.PrescriptionBounds
import com.hydrafit.app.core.domain.workout.requireValidSetMeasurement

/**
 * Sets one accepted-plan slot's measure in place (the plan keeps its id, acceptance time and week):
 * an UPDATE, not a re-accept. The slot's exercise, sets and load kind are preserved — this changes
 * only how the work is measured: a positive rep count, positive elapsed whole seconds, positive whole
 * metres or positive measured newtons, exactly one of them, never more and never none. Because force
 * is load-free, switching to force clears the slot's numeric load, and because ordered per-set targets
 * describe rep work, any non-reps measure clears those targets as well.
 *
 * Bounds are validated before anything is written. [reps] must be in
 * [PrescriptionBounds.MIN_REPS]..[PrescriptionBounds.MAX_REPS], [durationSeconds] must be positive
 * whole seconds and force must be within the prescription bounds. A missing day/position is a no-op
 * that returns null, mirroring [SubstituteExerciseUseCase].
 */
class SetAcceptedExerciseMeasureUseCase(private val planHistory: PlanHistoryRepository) {

    /**
     * Persists one of [reps], [durationSeconds], [distanceMeters] or [forceNewtons] on one slot of
     * [plan] and returns the updated slot, or null when the day/position does not exist. Throws
     * [IllegalArgumentException] when the measure is out of bounds or is not exactly one of them.
     */
    suspend operator fun invoke(
        plan: AcceptedPlan,
        dayIndex: Int,
        position: Int,
        reps: Int?,
        durationSeconds: Int?,
        distanceMeters: Int? = null,
        forceNewtons: Double? = null
    ): AcceptedExercise? {
        requireValidSetMeasurement(reps, durationSeconds, distanceMeters, forceNewtons)
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
        if (forceNewtons != null &&
            forceNewtons !in
            PrescriptionBounds.MIN_FORCE_NEWTONS..PrescriptionBounds.MAX_FORCE_NEWTONS
        ) {
            throw IllegalArgumentException("Force must be within the prescription bounds")
        }
        val day = plan.days.firstOrNull { it.dayIndex == dayIndex } ?: return null
        val entry = day.exercises.getOrNull(position) ?: return null
        planHistory.setMeasure(
            planId = plan.id,
            dayIndex = dayIndex,
            position = position,
            reps = reps,
            durationSeconds = durationSeconds,
            distanceMeters = distanceMeters,
            forceNewtons = forceNewtons
        )
        return entry.copy(
            reps = reps,
            durationSeconds = durationSeconds,
            distanceMeters = distanceMeters,
            forceNewtons = forceNewtons,
            // Force is load-free, so the stored numeric load cannot survive the change; ordered
            // per-set targets describe rep work, so any other measure clears them too.
            suggestedWeightKg = if (forceNewtons != null) null else entry.suggestedWeightKg,
            setTargets = if (reps != null) entry.setTargets else null
        )
    }
}
