package com.hydrafit.app.core.domain.routine

import com.hydrafit.app.core.domain.engine.AcceptedPlan
import com.hydrafit.app.core.domain.engine.WeeklyPlan

/**
 * Builds an editable routine template from a generated plan without persisting the plan itself. Both
 * overloads are pure conversions: the caller can edit the draft and save it, and the source plan is
 * never modified or accepted. Provenance is recorded only for an already-persisted accepted plan.
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

    /** Converts a draft (not-yet-accepted) plan without writing accepted-plan history. */
    operator fun invoke(plan: WeeklyPlan, name: String): RoutineTemplate = RoutineTemplate(
        name = name,
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
