package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.fatigue.MuscleGroup

class DeterministicWorkoutPlannerEngine(private val catalog: ExerciseCatalog) :
    WorkoutPlannerEngine {

    override val id: PlannerEngineId = PlannerEngineId.DETERMINISTIC

    override suspend fun generatePlan(request: PlanRequest): WeeklyPlan =
        plan(request, catalog.all())

    internal fun plan(request: PlanRequest, exercises: List<Exercise>): WeeklyPlan {
        require(request.daysPerWeek in MIN_DAYS..MAX_DAYS) {
            "daysPerWeek must be between $MIN_DAYS and $MAX_DAYS"
        }

        val focusCycle =
            focusCycleFor(resolveSplitType(request.splitPreference, request.daysPerWeek))
        val availableExercises = exercises.filter { it.isAvailableWith(request.availableEquipment) }

        val days = List(request.daysPerWeek) { index ->
            val focus = focusCycle[index % focusCycle.size]
            WorkoutDay(
                dayIndex = index,
                focus = focus,
                exercises = selectExercises(focus, availableExercises, request.muscleFatigue)
            )
        }

        return WeeklyPlan(engine = id, days = days)
    }

    private fun selectExercises(
        focus: SplitFocus,
        exercises: List<Exercise>,
        fatigue: Map<MuscleGroup, Double>
    ): List<PlannedExercise> {
        val focusMuscles = musclesFor(focus)
        return exercises
            .filter { exercise -> exercise.primaryMuscles.any { it in focusMuscles } }
            .sortedWith(
                compareBy(
                    { exercise ->
                        exercise.primaryMuscles.minOfOrNull { fatigue[it] ?: 0.0 } ?: 0.0
                    },
                    { exercise -> exercise.id }
                )
            )
            .take(MAX_EXERCISES_PER_DAY)
            .map { PlannedExercise(exerciseId = it.id, sets = DEFAULT_SETS, reps = DEFAULT_REPS) }
    }

    private fun resolveSplitType(preference: SplitType, daysPerWeek: Int): SplitType =
        when (preference) {
            SplitType.AUTO -> when (daysPerWeek) {
                2, 3 -> SplitType.FULL_BODY
                4 -> SplitType.UPPER_LOWER
                else -> SplitType.PUSH_PULL_LEGS
            }
            else -> preference
        }

    private fun focusCycleFor(splitType: SplitType): List<SplitFocus> = when (splitType) {
        SplitType.FULL_BODY -> listOf(SplitFocus.FULL_BODY)
        SplitType.UPPER_LOWER -> listOf(SplitFocus.UPPER, SplitFocus.LOWER)
        SplitType.PUSH_PULL_LEGS -> listOf(SplitFocus.PUSH, SplitFocus.PULL, SplitFocus.LEGS)
        SplitType.AUTO -> listOf(SplitFocus.FULL_BODY)
    }

    private fun musclesFor(focus: SplitFocus): Set<MuscleGroup> = when (focus) {
        SplitFocus.PUSH -> setOf(MuscleGroup.CHEST, MuscleGroup.SHOULDERS, MuscleGroup.TRICEPS)
        SplitFocus.PULL -> setOf(MuscleGroup.BACK, MuscleGroup.BICEPS)
        SplitFocus.LEGS -> setOf(
            MuscleGroup.QUADS,
            MuscleGroup.HAMSTRINGS,
            MuscleGroup.GLUTES,
            MuscleGroup.CALVES
        )
        SplitFocus.UPPER -> setOf(
            MuscleGroup.CHEST,
            MuscleGroup.BACK,
            MuscleGroup.SHOULDERS,
            MuscleGroup.BICEPS,
            MuscleGroup.TRICEPS
        )
        SplitFocus.LOWER -> setOf(
            MuscleGroup.QUADS,
            MuscleGroup.HAMSTRINGS,
            MuscleGroup.GLUTES,
            MuscleGroup.CALVES,
            MuscleGroup.CORE
        )
        SplitFocus.FULL_BODY -> MuscleGroup.entries.toSet()
    }

    companion object {
        const val MIN_DAYS = 2
        const val MAX_DAYS = 6
        const val DEFAULT_SETS = 3
        const val DEFAULT_REPS = 10
        const val MAX_EXERCISES_PER_DAY = 5
    }
}
