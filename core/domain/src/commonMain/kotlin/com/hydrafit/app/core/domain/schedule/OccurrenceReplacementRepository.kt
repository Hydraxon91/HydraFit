package com.hydrafit.app.core.domain.schedule

/** Narrow write seam: does not authorize general re-prescription of a started workout. */
interface OccurrenceReplacementRepository {
    suspend fun replaceUntouchedEntry(
        occurrenceId: Long,
        expectedRevision: Int,
        replacement: OccurrenceEntry,
        nowMillis: Long
    )
}
