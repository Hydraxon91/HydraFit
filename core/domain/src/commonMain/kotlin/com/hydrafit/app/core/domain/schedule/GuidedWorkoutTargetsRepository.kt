package com.hydrafit.app.core.domain.schedule

import com.hydrafit.app.core.domain.engine.SetTarget

/** Transactional write boundary for a guided occurrence's working targets and measure. */
interface GuidedWorkoutTargetsRepository {
    suspend fun applyTargets(request: GuidedWorkoutTargetsRequest)

    /**
     * Atomically replaces one unstarted occurrence entry's measure and clears its guided overrides.
     * Implementations that do not support measure correction reject it; the production store
     * overrides this, while lightweight test doubles may leave it unsupported.
     */
    suspend fun changeEntryMeasure(request: OccurrenceEntryMeasureRequest): Unit =
        throw ScheduleException("Changing a slot's measurement is unavailable")
}

/**
 * One revision-gated measure correction on the per-occurrence copy. [target] carries exactly one of
 * reps, seconds, metres or newtons and the load the chosen measure may keep.
 */
data class OccurrenceEntryMeasureRequest(
    val occurrenceId: Long,
    val occurrenceRevision: Int,
    val entryId: Long,
    val target: SetTarget
)

data class GuidedWorkoutTargetsRequest(
    val occurrenceId: Long,
    val occurrenceRevision: Int,
    val entryId: Long,
    val exerciseId: String,
    val entrySets: Int,
    val completedSetCount: Int,
    val resetRemaining: Boolean,
    val patches: Map<Int, GuidedTargetPatch>
)
