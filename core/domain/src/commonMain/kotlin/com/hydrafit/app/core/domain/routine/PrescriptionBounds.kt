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
    const val MIN_DISTANCE_METERS = 1
    const val MAX_DISTANCE_METERS = 100_000

    /**
     * Throws [RoutineTemplateException] when a slot is outside the bounds, carries more than one
     * measurement, has none, or has a bad load. [reps], [durationSeconds] and [distanceMeters] are
     * mutually exclusive.
     */
    fun validate(
        sets: Int,
        reps: Int?,
        durationSeconds: Int?,
        weightKg: Double?,
        distanceMeters: Int? = null
    ) {
        if (sets !in MIN_SETS..MAX_SETS) {
            throw RoutineTemplateException("Sets must be between $MIN_SETS and $MAX_SETS")
        }
        try {
            requireValidSetMeasurement(reps, durationSeconds, distanceMeters)
        } catch (e: IllegalArgumentException) {
            throw RoutineTemplateException(
                e.message ?: "A slot needs a positive rep count or positive whole seconds"
            )
        }
        if (reps != null && reps !in MIN_REPS..MAX_REPS) {
            throw RoutineTemplateException("Reps must be between $MIN_REPS and $MAX_REPS")
        }
        if (distanceMeters != null && distanceMeters !in MIN_DISTANCE_METERS..MAX_DISTANCE_METERS) {
            throw RoutineTemplateException(
                "Distance must be between $MIN_DISTANCE_METERS and $MAX_DISTANCE_METERS metres"
            )
        }
        if (weightKg != null && (!weightKg.isFinite() || weightKg < 0.0)) {
            throw RoutineTemplateException("Weight must be zero or positive")
        }
    }
}
