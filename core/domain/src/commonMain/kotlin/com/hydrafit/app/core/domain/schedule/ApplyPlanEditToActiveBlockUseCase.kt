package com.hydrafit.app.core.domain.schedule

import com.hydrafit.app.core.domain.engine.AcceptedExercise
import com.hydrafit.app.core.domain.engine.AcceptedPlan
import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.routine.toRoutineEntry

/**
 * Carries a saved accepted-plan slot edit (a swap or a measure change) into the active block built
 * from that plan, so the logger shows it on every workout not yet started. Started, resolved and
 * recorded workouts keep their frozen prescription, and a block from another plan or a routine is
 * left alone. The plan edit is already saved when this runs, so a failure here leaves the block as
 * it was rather than undoing the plan.
 */
class ApplyPlanEditToActiveBlockUseCase(
    private val catalog: ExerciseCatalog,
    private val block: PlanSlotBlockRepository
) {
    /**
     * [plan] is the accepted plan as it was before the edit and [edited] the slot as saved. Returns
     * how many workouts changed.
     */
    suspend operator fun invoke(
        plan: AcceptedPlan,
        dayIndex: Int,
        position: Int,
        edited: AcceptedExercise
    ): Int {
        if (plan.id == 0L) return 0
        // Blocks number their workouts in the plan's sorted day order, as the routine conversion does.
        val days = plan.days.sortedBy { it.dayIndex }
        val workoutPosition = days.indexOfFirst { it.dayIndex == dayIndex }
        if (workoutPosition < 0) return 0
        val previous = days[workoutPosition].exercises.getOrNull(position) ?: return 0
        val exercise = catalog.all().firstOrNull { it.id == edited.exerciseId } ?: return 0
        val prescription = activationEntryFor(edited.toRoutineEntry(position), exercise)
            .copy(explanation = edited.explanation)
        return block.applyPlanSlotEdit(
            sourcePlanId = plan.id,
            workoutPosition = workoutPosition,
            previousExerciseId = previous.exerciseId,
            prescription = prescription
        )
    }
}
