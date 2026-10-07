package com.hydrafit.app.core.domain.engine

import kotlinx.coroutines.flow.Flow

/** Stores plans the user explicitly accepted. Drafts are never persisted. */
interface PlanHistoryRepository {
    fun observeLatest(): Flow<AcceptedPlan?>

    /** Every accepted plan, newest first. */
    fun observeHistory(): Flow<List<AcceptedPlan>>

    suspend fun latest(): AcceptedPlan?

    suspend fun accept(plan: AcceptedPlan)

    /**
     * Replaces one slot's exercise in place, keeping the plan's id, acceptance time and week: an
     * UPDATE, not a re-accept. [newExerciseName] is snapshotted from the catalog and [newWeightKg]
     * is the preserved (and equipment-clamped) working weight, so a later catalog edit cannot
     * silently rewrite the swapped slot.
     */
    suspend fun substitute(
        planId: Long,
        dayIndex: Int,
        position: Int,
        newExerciseId: String,
        newExerciseName: String,
        newWeightKg: Double?
    )

    suspend fun delete(planId: Long)

    suspend fun clear()
}
