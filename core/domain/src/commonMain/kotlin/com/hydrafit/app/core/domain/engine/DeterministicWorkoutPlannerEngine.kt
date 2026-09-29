package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.MovementPattern
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
        require(request.setsPerExercise in MIN_SETS..MAX_SETS) {
            "setsPerExercise must be between $MIN_SETS and $MAX_SETS"
        }

        val focusCycle =
            focusCycleFor(resolveSplitType(request.splitPreference, request.daysPerWeek))
        val availableExercises = exercises.filter { it.isAvailableWith(request.availableEquipment) }

        val days = List(request.daysPerWeek) { index ->
            val focus = focusCycle[index % focusCycle.size]
            WorkoutDay(
                dayIndex = index,
                focus = focus,
                exercises = selectExercises(
                    templateFor(focus, index),
                    availableExercises,
                    request.muscleFatigue,
                    request.setsPerExercise
                )
            )
        }

        return WeeklyPlan(engine = id, days = days)
    }

    private fun selectExercises(
        template: List<MovementPattern>,
        exercises: List<Exercise>,
        fatigue: Map<MuscleGroup, Double>,
        setsPerExercise: Int
    ): List<PlannedExercise> {
        val used = mutableSetOf<String>()
        val picks = mutableListOf<PlannedExercise>()

        for (pattern in template) {
            val candidate = exercises
                .filter { it.movementPattern == pattern && it.id !in used }
                // Recovery comes first: a fresh less-preferred exercise outranks a sore preferred
                // one. Equipment preference only breaks ties between equally fresh candidates.
                .minWithOrNull(
                    compareBy(
                        { fatigueOf(it, fatigue) },
                        { equipmentRank(it) },
                        { it.id }
                    )
                )
                ?: continue

            val soreness = fatigueOf(candidate, fatigue)
            if (soreness >= FATIGUE_SKIP_THRESHOLD) continue

            used += candidate.id
            picks += PlannedExercise(
                exerciseId = candidate.id,
                sets = (setsPerExercise - if (soreness >= FATIGUE_REDUCE_THRESHOLD) 1 else 0)
                    .coerceAtLeast(1),
                reps = if (candidate.movementPattern.isCompound) COMPOUND_REPS else ISOLATION_REPS
            )
        }

        return picks
    }

    private fun fatigueOf(exercise: Exercise, fatigue: Map<MuscleGroup, Double>): Double =
        exercise.primaryMuscles.maxOfOrNull { fatigue[it] ?: 0.0 } ?: 0.0

    /** Prefers barbell > dumbbell > machine/kettlebell > band > pull-up bar > bodyweight. */
    private fun equipmentRank(exercise: Exercise): Int = exercise.requiredEquipment
        .filterNot { it == EquipmentTag.BODYWEIGHT }
        .minOfOrNull { EQUIPMENT_RANK[it] ?: Int.MAX_VALUE }
        ?: Int.MAX_VALUE

    private fun templateFor(focus: SplitFocus, dayIndex: Int): List<MovementPattern> =
        when (focus) {
            SplitFocus.PUSH -> listOf(
                MovementPattern.HORIZONTAL_PUSH,
                MovementPattern.VERTICAL_PUSH,
                MovementPattern.TRICEPS_ISOLATION,
                MovementPattern.SHOULDER_ISOLATION
            )
            SplitFocus.PULL -> listOf(
                MovementPattern.VERTICAL_PULL,
                MovementPattern.HORIZONTAL_PULL,
                MovementPattern.BICEPS_ISOLATION
            )
            SplitFocus.LEGS -> listOf(
                MovementPattern.SQUAT,
                MovementPattern.HINGE,
                MovementPattern.LEG_ISOLATION,
                MovementPattern.CALF_RAISE,
                MovementPattern.CORE
            )
            SplitFocus.UPPER -> listOf(
                MovementPattern.HORIZONTAL_PUSH,
                MovementPattern.HORIZONTAL_PULL,
                MovementPattern.VERTICAL_PUSH,
                MovementPattern.VERTICAL_PULL,
                MovementPattern.BICEPS_ISOLATION,
                MovementPattern.TRICEPS_ISOLATION
            )
            SplitFocus.LOWER -> listOf(
                MovementPattern.SQUAT,
                MovementPattern.HINGE,
                MovementPattern.LEG_ISOLATION,
                MovementPattern.CALF_RAISE,
                MovementPattern.CORE
            )
            SplitFocus.FULL_BODY -> FULL_BODY_TEMPLATES[dayIndex % FULL_BODY_TEMPLATES.size]
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

    companion object {
        const val MIN_DAYS = 2
        const val MAX_DAYS = 6
        const val MIN_SETS = 1
        const val MAX_SETS = 8
        const val DEFAULT_SETS = DEFAULT_SETS_PER_EXERCISE
        const val COMPOUND_REPS = 6
        const val ISOLATION_REPS = 12
        const val FATIGUE_REDUCE_THRESHOLD = 0.5
        const val FATIGUE_SKIP_THRESHOLD = 0.85

        private val FULL_BODY_TEMPLATES = listOf(
            listOf(
                MovementPattern.SQUAT,
                MovementPattern.HORIZONTAL_PUSH,
                MovementPattern.HORIZONTAL_PULL,
                MovementPattern.CORE
            ),
            listOf(
                MovementPattern.HINGE,
                MovementPattern.VERTICAL_PUSH,
                MovementPattern.VERTICAL_PULL,
                MovementPattern.CALF_RAISE
            ),
            listOf(
                MovementPattern.LUNGE,
                MovementPattern.HORIZONTAL_PUSH,
                MovementPattern.HORIZONTAL_PULL,
                MovementPattern.BICEPS_ISOLATION
            )
        )

        private val EQUIPMENT_RANK = mapOf(
            EquipmentTag.BARBELL to 0,
            EquipmentTag.DUMBBELL to 1,
            EquipmentTag.CABLE_MACHINE to 2,
            EquipmentTag.KETTLEBELL to 3,
            EquipmentTag.RESISTANCE_BAND to 4,
            EquipmentTag.PULL_UP_BAR to 5,
            EquipmentTag.BENCH to 6,
            EquipmentTag.BODYWEIGHT to 7
        )
    }
}
