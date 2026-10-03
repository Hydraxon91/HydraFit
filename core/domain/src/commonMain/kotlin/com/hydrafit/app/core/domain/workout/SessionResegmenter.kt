package com.hydrafit.app.core.domain.workout

/**
 * Applies a time correction and the session re-segmentation it implies in one atomic unit, so the
 * stored sessions can never be left interleaved or with bounds inconsistent with their sets.
 */
interface SessionResegmenter {
    suspend fun resegmentAfterTimeCorrection(
        setId: Long,
        performedAtMillis: Long,
        utcOffsetMillis: Long
    )
}
