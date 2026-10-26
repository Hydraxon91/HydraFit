package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.workout.LoadKind
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
        newWeightKg: Double?,
        newLoadCapability: ExerciseLoadCapability,
        newLoadKind: LoadKind
    )

    /**
     * Replaces one slot and writes its new ordered targets and recorded reasoning in the same
     * transaction as the slot update, so a slot is never persisted without the provenance its load
     * was derived from. The default keeps simple test doubles source-compatible by delegating to
     * [substitute]; a real store must override it.
     */
    suspend fun substituteWithProvenance(
        planId: Long,
        dayIndex: Int,
        position: Int,
        newExerciseId: String,
        newExerciseName: String,
        newWeightKg: Double?,
        newLoadCapability: ExerciseLoadCapability,
        newLoadKind: LoadKind,
        setTargets: SlotSetTargets?,
        explanation: ExerciseTargetExplanation?
    ) {
        substitute(
            planId = planId,
            dayIndex = dayIndex,
            position = position,
            newExerciseId = newExerciseId,
            newExerciseName = newExerciseName,
            newWeightKg = newWeightKg,
            newLoadCapability = newLoadCapability,
            newLoadKind = newLoadKind
        )
    }

    /**
     * Replaces one slot's measure in place, keeping the plan's id, acceptance time, exercise and sets:
     * an UPDATE, not a re-accept. [reps], [durationSeconds], [distanceMeters] and [forceNewtons] are
     * mutually exclusive — exactly one is non-null — and a missing day/position is a no-op that leaves
     * the plan untouched.
     */
    suspend fun setMeasure(
        planId: Long,
        dayIndex: Int,
        position: Int,
        reps: Int?,
        durationSeconds: Int?,
        distanceMeters: Int? = null,
        forceNewtons: Double? = null
    )

    suspend fun delete(planId: Long)

    suspend fun clear()
}
