package com.hydrafit.app.core.domain.schedule

/**
 * Narrow write seam that carries one accepted-plan slot edit into the active block scheduled from
 * that plan. It never re-prescribes a started, resolved or recorded workout.
 */
interface PlanSlotBlockRepository {
    /**
     * In one transaction, and only while the active block was scheduled from [sourcePlanId]:
     * rewrites the block's master slot at [workoutPosition] / [prescription]`.position`, and the same
     * slot of every pending workout of that day with no recorded sets. Each slot changes only while
     * it still holds [previousExerciseId], so a slot already replaced from the logger keeps that
     * choice. The slot keeps its pairing and cluster; its guided targets are cleared when the
     * prescription changes. Every rewritten workout's revision advances. Returns how many workouts
     * changed.
     */
    suspend fun applyPlanSlotEdit(
        sourcePlanId: Long,
        workoutPosition: Int,
        previousExerciseId: String,
        prescription: ActivationEntry
    ): Int
}
