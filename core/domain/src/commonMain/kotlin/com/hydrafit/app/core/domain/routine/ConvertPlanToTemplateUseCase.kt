package com.hydrafit.app.core.domain.routine

import com.hydrafit.app.core.domain.engine.AcceptedPlan

/**
 * Builds an editable routine template from an accepted generated plan without persisting it. This
 * is a pure conversion: the caller can edit the draft and save it, and the accepted plan itself is
 * never modified or re-accepted. Provenance is recorded only when the plan was actually persisted.
 */
class ConvertPlanToTemplateUseCase {
    operator fun invoke(plan: AcceptedPlan, name: String): RoutineTemplate = RoutineTemplate(
        name = name,
        sourcePlanId = plan.id.takeIf { it != 0L },
        workouts = plan.days.sortedBy { it.dayIndex }.mapIndexed { index, day ->
            RoutineWorkout(
                position = index,
                name = "Day ${index + 1}",
                focus = day.focus,
                entries = day.exercises.mapIndexed { entryIndex, exercise ->
                    RoutineEntry(
                        position = entryIndex,
                        exerciseId = exercise.exerciseId,
                        sets = exercise.sets,
                        reps = exercise.reps,
                        weightKg = exercise.suggestedWeightKg
                    )
                }
            )
        }
    )
}
