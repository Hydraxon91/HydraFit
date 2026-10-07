package com.hydrafit.app.core.domain.routine

/** Shared set/rep/load bounds for every editable prescription (routine drafts and occurrences). */
object PrescriptionBounds {
    const val MIN_SETS = 1
    const val MAX_SETS = 8
    const val MIN_REPS = 1
    const val MAX_REPS = 100

    /** Throws [RoutineTemplateException] when a slot is outside the bounds or has a bad load. */
    fun validate(sets: Int, reps: Int, weightKg: Double?) {
        if (sets !in MIN_SETS..MAX_SETS) {
            throw RoutineTemplateException("Sets must be between $MIN_SETS and $MAX_SETS")
        }
        if (reps !in MIN_REPS..MAX_REPS) {
            throw RoutineTemplateException("Reps must be between $MIN_REPS and $MAX_REPS")
        }
        if (weightKg != null && (!weightKg.isFinite() || weightKg < 0.0)) {
            throw RoutineTemplateException("Weight must be zero or positive")
        }
    }
}
