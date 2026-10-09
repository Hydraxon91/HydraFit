package com.hydrafit.app.core.domain.workout

import com.hydrafit.app.core.domain.time.TimeProvider

/** Corrects one stored set atomically, including session bounds when its time changes. */
class CorrectWorkoutSetUseCase(
    private val resegmenter: SessionResegmenter,
    private val timeProvider: TimeProvider
) {
    suspend operator fun invoke(setId: Long, correction: WorkoutSetCorrection) {
        require(correction.reps > 0) { "Reps must be positive" }
        require(correction.rir == null || correction.rir in 0..10) {
            "RIR must be between 0 and 10"
        }
        require(correction.performedAtMillis <= timeProvider.nowMillis()) {
            "That time is in the future"
        }
        WorkoutLoadPolicy.validateShape(LoadKind.LEGACY_UNSPECIFIED, correction.weightKg)
        resegmenter.resegmentAfterSetCorrection(setId, correction, timeProvider.utcOffsetMillis())
    }
}
