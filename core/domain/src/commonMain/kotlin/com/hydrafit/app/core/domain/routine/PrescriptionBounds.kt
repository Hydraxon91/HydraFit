package com.hydrafit.app.core.domain.routine

import com.hydrafit.app.core.domain.workout.requireValidSetMeasurement

/**
 * Shared set/metric/load bounds for every editable prescription (routine drafts and occurrences).
 *
 * A slot is measured by a positive rep count or by positive whole seconds, never both and never
 * neither: the exactly-one rule is the same one the performed-set write boundary uses, so a
 * prescription and the set that fulfils it agree on what a valid measurement is.
 */
object PrescriptionBounds {
    const val MIN_SETS = 1
    const val MAX_SETS = 8
    const val MIN_REPS = 1
    const val MAX_REPS = 100

    /**
     * Throws [RoutineTemplateException] when a slot is outside the bounds, carries both or neither
     * measurement, or has a bad load. [reps] is the counted metric and [durationSeconds] the timed
     * one; at most one is non-null.
     */
    fun validate(sets: Int, reps: Int?, durationSeconds: Int?, weightKg: Double?) {
        if (sets !in MIN_SETS..MAX_SETS) {
            throw RoutineTemplateException("Sets must be between $MIN_SETS and $MAX_SETS")
        }
        try {
            requireValidSetMeasurement(reps, durationSeconds)
        } catch (e: IllegalArgumentException) {
            throw RoutineTemplateException(
                e.message ?: "A slot needs a positive rep count or positive whole seconds"
            )
        }
        if (reps != null && reps !in MIN_REPS..MAX_REPS) {
            throw RoutineTemplateException("Reps must be between $MIN_REPS and $MAX_REPS")
        }
        if (weightKg != null && (!weightKg.isFinite() || weightKg < 0.0)) {
            throw RoutineTemplateException("Weight must be zero or positive")
        }
    }
}
