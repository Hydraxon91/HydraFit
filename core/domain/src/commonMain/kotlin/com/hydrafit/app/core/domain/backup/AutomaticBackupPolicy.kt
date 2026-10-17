package com.hydrafit.app.core.domain.backup

/**
 * The fixed automatic-backup schedule and retention contract: approximately daily snapshots, at
 * most [RETAINED_SNAPSHOTS] verified files, pruned only after a newer snapshot verified. These are
 * behavior constants, not user-facing knobs; a configurable interval or retention count would be a
 * separate decision.
 */
object AutomaticBackupPolicy {
    /** Minimum time between a verified snapshot and the next automatic attempt. */
    const val INTERVAL_MILLIS: Long = 24L * 60 * 60 * 1000

    /** Verified snapshots kept in the chosen folder; older ones are pruned after a new success. */
    const val RETAINED_SNAPSHOTS: Int = 7

    /**
     * Whether a new snapshot is due: automation is on and there is no verified snapshot yet, or the
     * recorded success is at least [INTERVAL_MILLIS] old. A failed attempt never counts as success,
     * so a failure is retried at the next opportunity.
     */
    fun isDue(state: AutomaticBackupState, nowMillis: Long): Boolean {
        if (!state.enabled) return false
        val lastSuccess = state.lastSuccessMillis ?: return true
        return nowMillis - lastSuccess >= INTERVAL_MILLIS
    }

    /**
     * The recorded document ids beyond the retention window, oldest first. Only verified documents
     * are eligible: an unverified write's outcome is still unknown, so it is never pruned blind.
     */
    fun documentsToPrune(state: AutomaticBackupState): List<String> = state.documents
        .filter { it.verified }
        .sortedWith(
            compareByDescending<RecordedBackupDocument> { it.createdAtMillis }
                .thenBy { it.documentId }
        )
        .drop(RETAINED_SNAPSHOTS)
        .map { it.documentId }
        .asReversed()
}
