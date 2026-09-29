package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.Exercise

/**
 * Shared guard for model-backed plans. It drops exercises that are unknown or need unselected
 * equipment, applies the requested set count and the compound/isolation rep scheme, and rejects
 * a plan that is not a complete week. Returns null when the plan should be discarded.
 */
class WeeklyPlanSanitizer(
    private val catalog: ExerciseCatalog,
    private val volumeAwareReps: VolumeAwareReps = VolumeAwareReps()
) {

    suspend fun sanitize(plan: WeeklyPlan, request: PlanRequest): WeeklyPlan? {
        val usable = catalog.all()
            .filter { it.isAvailableWith(request.availableEquipment) }
            .associateBy { it.id }

        val days = plan.days.map { day ->
            day.copy(
                exercises = day.exercises.mapNotNull { planned ->
                    val exercise = usable[planned.exerciseId] ?: return@mapNotNull null
                    val sets = setsFor(exercise, request)
                    PlannedExercise(
                        exerciseId = exercise.id,
                        sets = sets,
                        reps = volumeAwareReps.repsFor(
                            request.goal,
                            exercise.movementPattern.isCompound,
                            sets
                        ),
                        suggestedWeightKg = planned.suggestedWeightKg?.takeIf {
                            request.includeWorkoutData && it > 0.0 && it <= MAX_SUGGESTED_WEIGHT_KG
                        }
                    )
                }
            )
        }

        if (days.size < request.daysPerWeek) return null
        if (days.any { it.exercises.size < MIN_EXERCISES_PER_DAY }) return null
        return plan.copy(
            days = days.take(request.daysPerWeek)
                .mapIndexed { index, day -> day.copy(dayIndex = index) }
        )
    }

    private fun setsFor(exercise: Exercise, request: PlanRequest): Int =
        if (exercise.movementPattern.isCompound) {
            request.setsPerExercise
        } else {
            request.accessorySetsPerExercise
        }

    companion object {
        const val MIN_EXERCISES_PER_DAY = 2
        const val MAX_SUGGESTED_WEIGHT_KG = 1_000.0
    }
}
