package com.hydrafit.app.core.domain.workout

import com.hydrafit.app.core.domain.time.TimeProvider

/**
 * Corrects the performed-at time of an already-logged set and re-segments the sessions it affects,
 * so sessions stay non-interleaved and each session's bounds and local day stay consistent with its
 * sets. The set keeps its reps, weight, warm-up flag, RIR, plan snapshot, and muscle snapshot.
 */
class CorrectWorkoutSetTimeUseCase(
    private val resegmenter: SessionResegmenter,
    private val timeProvider: TimeProvider
) {
    suspend operator fun invoke(setId: Long, performedAtMillis: Long) =
        resegmenter.resegmentAfterTimeCorrection(
            setId = setId,
            performedAtMillis = performedAtMillis,
            utcOffsetMillis = timeProvider.utcOffsetMillis()
        )
}
