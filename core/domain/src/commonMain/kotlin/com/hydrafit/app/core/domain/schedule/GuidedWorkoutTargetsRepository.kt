package com.hydrafit.app.core.domain.schedule

/** Transactional write boundary for a guided occurrence's per-set working targets. */
interface GuidedWorkoutTargetsRepository {
    suspend fun applyTargets(request: GuidedWorkoutTargetsRequest)
}

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
