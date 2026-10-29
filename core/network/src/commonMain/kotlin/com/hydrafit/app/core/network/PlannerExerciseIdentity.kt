package com.hydrafit.app.core.network

import com.hydrafit.app.core.domain.engine.WeeklyPlan
import com.hydrafit.app.core.domain.equipment.Exercise

/**
 * Maps model-returned exercise identifiers onto the request's eligible exercises.
 *
 * A provider may echo a display name or change casing. A case-insensitive id match always wins. A
 * display name resolves only when exactly one eligible exercise carries it; a name shared by two or
 * more eligible exercises cannot identify which one the model meant, so it is left untouched and the
 * sanitizer drops that slot rather than substituting an exercise the user did not get.
 */
internal fun resolveExerciseIds(plan: WeeklyPlan, eligibleExercises: List<Exercise>): WeeklyPlan {
    if (eligibleExercises.isEmpty()) return plan
    val byId = eligibleExercises.associateBy { it.id.lowercase() }
    val byName = eligibleExercises
        .groupBy { it.name.lowercase() }
        .mapNotNull { (name, matches) -> matches.singleOrNull()?.let { name to it.id } }
        .toMap()
    return plan.copy(
        days = plan.days.map { day ->
            day.copy(
                exercises = day.exercises.map { planned ->
                    val raw = planned.exerciseId
                    val resolved = byId[raw.lowercase()]?.id
                        ?: byName[raw.lowercase()]
                        ?: raw
                    planned.copy(exerciseId = resolved)
                }
            )
        }
    )
}
