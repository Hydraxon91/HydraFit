package com.hydrafit.app.core.domain.routine

/** A routine draft the user submitted is not a usable routine. */
class RoutineTemplateException(message: String) : IllegalArgumentException(message)

/**
 * Validates and normalizes a routine draft before it is persisted: a non-blank name, at least one
 * workout, ordered positions with no gaps, and every slot within the shared set/rep bounds. A
 * workout may be empty while the user is still building it; a slot may not carry invalid work.
 */
internal object RoutineTemplateValidator {
    const val MIN_SETS = 1
    const val MAX_SETS = 8
    const val MIN_REPS = 1
    const val MAX_REPS = 100

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
        if (entry.sets !in MIN_SETS..MAX_SETS) {
            throw RoutineTemplateException("Sets must be between $MIN_SETS and $MAX_SETS")
        }
        if (entry.reps !in MIN_REPS..MAX_REPS) {
            throw RoutineTemplateException("Reps must be between $MIN_REPS and $MAX_REPS")
        }
        val weight = entry.weightKg
        if (weight != null && (!weight.isFinite() || weight < 0.0)) {
            throw RoutineTemplateException("Weight must be zero or positive")
        }
        return entry
    }
}
