package com.hydrafit.app.core.domain.routine

import com.hydrafit.app.core.domain.workout.LoadKind

/** A routine draft the user submitted is not a usable routine. */
class RoutineTemplateException(message: String) : IllegalArgumentException(message)

/**
 * Validates and normalizes a routine draft before it is persisted: a non-blank name, at least one
 * workout, ordered positions with no gaps, and every slot within the shared set/rep bounds. A
 * workout may be empty while the user is still building it; a slot may not carry invalid work.
 */
internal object RoutineTemplateValidator {
    fun validate(template: RoutineTemplate): RoutineTemplate {
        val name = template.name.trim()
        if (name.isEmpty()) throw RoutineTemplateException("Routine name must not be blank")
        if (template.workouts.isEmpty()) {
            throw RoutineTemplateException("A routine needs at least one workout")
        }
        return template.copy(
            name = name,
            workouts = template.workouts.mapIndexed { index, workout ->
                val workoutName = workout.name.trim()
                if (workoutName.isEmpty()) {
                    throw RoutineTemplateException("Workout name must not be blank")
                }
                workout.copy(
                    position = index,
                    name = workoutName,
                    entries = workout.entries.mapIndexed { entryIndex, entry ->
                        validateEntry(entry).copy(position = entryIndex)
                    }
                )
            }
        )
    }

    private fun validateEntry(entry: RoutineEntry): RoutineEntry {
        if (entry.exerciseId.isBlank()) {
            throw RoutineTemplateException("A slot needs an exercise")
        }
        PrescriptionBounds.validate(entry.sets, entry.reps, entry.weightKg)
        // Capability compatibility is checked at activation (which has the catalog); here only the
        // shape that holds regardless of capability is enforced.
        if (entry.loadKind == LoadKind.BODYWEIGHT && entry.weightKg != null) {
            throw RoutineTemplateException("Bodyweight work cannot record a numeric load")
        }
        return entry
    }
}
