package com.hydrafit.app.core.network

import com.hydrafit.app.core.domain.engine.WeeklyPlan
import com.hydrafit.app.core.domain.equipment.Exercise

/**
 * Maps model-returned exercise identifiers onto the request's eligible ids.
 *
 * A provider may echo a display name or change casing. Only a case-insensitive id match, or a
 * catalog name that resolves to an eligible id, is accepted; anything else is left untouched so the
 * sanitizer can drop it instead of silently substituting a different exercise.
 */
internal fun resolveExerciseIds(
    plan: WeeklyPlan,
    availableIds: List<String>,
    catalogExercises: List<Exercise>
): WeeklyPlan {
    if (availableIds.isEmpty()) return plan
    val byId = availableIds.associateBy { it.lowercase() }
    val byName = catalogExercises.associateBy { it.name.lowercase() }
    return plan.copy(
        days = plan.days.map { day ->
            day.copy(
                exercises = day.exercises.map { planned ->
                    val raw = planned.exerciseId
                    val resolved = byId[raw.lowercase()]
                        ?: byName[raw.lowercase()]?.id
                        ?: raw
                    planned.copy(exerciseId = resolved)
                }
            )
        }
    )
}
