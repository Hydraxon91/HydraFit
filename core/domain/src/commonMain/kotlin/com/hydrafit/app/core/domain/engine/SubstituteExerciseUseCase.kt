package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.WorkoutLoadPolicy

/** A ranked replacement for one accepted-plan slot, shown so the user can pick one. */
data class SwapCandidate(
    val exerciseId: String,
    val name: String,
    val movementPattern: MovementPattern,
    val primaryEquipment: EquipmentTag,
    val suggestedWeightKg: Double?,
    /** The meaning of [suggestedWeightKg] after the swap; the slot's kind when compatible. */
    val loadKind: LoadKind = LoadKind.EXTERNAL
)

/**
 * Swaps one exercise inside an already-accepted plan, persisting the change in place (the plan keeps
 * its id and acceptance time). Candidates must train the same movement pattern and be available with
 * the user's equipment; they are ranked by the same fatigue/deficit/freshness order the generator
 * uses, so a substitution reads like a fresh pick rather than an arbitrary swap.
 *
 * The slot's sets, reps and working weight are preserved: substitution replaces the exercise, it does
 * not re-plan the slot. The weight is only lowered to fit the replacement's equipment ceiling.
 */
class SubstituteExerciseUseCase(
    private val catalog: ExerciseCatalog,
    private val planHistory: PlanHistoryRepository,
    private val rankingEngine: DeterministicWorkoutPlannerEngine
) {

    /** Ranked alternatives for one slot, or empty when nothing suitable is available. */
    suspend fun candidates(
        plan: AcceptedPlan,
        dayIndex: Int,
        position: Int,
        request: PlanRequest
    ): List<SwapCandidate> {
        val exercises = catalog.all()
        val context = contextFor(plan, dayIndex, position, request, exercises) ?: return emptyList()
        return rankedCandidates(context, exercises).map { candidate ->
            val (weight, kind) = preservedLoad(
                context.entry,
                candidate,
                request.equipmentMaxWeights
            )
            SwapCandidate(
                exerciseId = candidate.id,
                name = candidate.name,
                movementPattern = candidate.movementPattern,
                primaryEquipment = primaryEquipmentOf(candidate),
                suggestedWeightKg = weight,
                loadKind = kind
            )
        }
    }

    /**
     * Replaces the slot with [selectedExerciseId], or returns null when the plan/slot is missing, the
     * candidate is no longer available/fresh, or it is no longer a valid alternative. Availability
     * and soreness are re-validated here because the candidate list is only a snapshot.
     */
    suspend operator fun invoke(
        plan: AcceptedPlan,
        dayIndex: Int,
        position: Int,
        request: PlanRequest,
        selectedExerciseId: String
    ): AcceptedExercise? {
        val exercises = catalog.all()
        val context = contextFor(plan, dayIndex, position, request, exercises) ?: return null
        val chosen = rankedCandidates(context, exercises)
            .firstOrNull { it.id == selectedExerciseId }
            ?: return null
        if (rankingEngine.pickFirstNonSore(listOf(chosen), request.muscleFatigue) ==
            null
        ) {
            return null
        }

        val (weight, kind) = preservedLoad(context.entry, chosen, request.equipmentMaxWeights)
        planHistory.substitute(
            planId = plan.id,
            dayIndex = dayIndex,
            position = position,
            newExerciseId = chosen.id,
            newExerciseName = chosen.name,
            newWeightKg = weight,
            newLoadCapability = chosen.loadCapability,
            newLoadKind = kind
        )
        return AcceptedExercise(
            exerciseId = chosen.id,
            sets = context.entry.sets,
            reps = context.entry.reps,
            name = chosen.name,
            movementPattern = chosen.movementPattern,
            suggestedWeightKg = weight,
            loadCapability = chosen.loadCapability,
            loadKind = kind
        )
    }

    private fun rankedCandidates(context: SlotContext, exercises: List<Exercise>): List<Exercise> {
        val available = exercises.filter { it.isAvailableWith(context.request.availableEquipment) }
        // Compounds never repeat across days; accessories merely prefer a fresh option. The replaced
        // slot is excluded from the week's used set, so its own exercise is not a "used" penalty.
        val crossDayExclusions = if (context.entry.movementPattern.isCompound) {
            context.weekUsed
        } else {
            emptySet()
        }
        val filtered = available.filter { exercise ->
            exercise.movementPattern == context.entry.movementPattern &&
                exercise.id != context.entry.exerciseId &&
                exercise.id !in context.sameDayIds &&
                exercise.id !in crossDayExclusions
        }
        val ranked = rankingEngine.rankCandidates(
            candidates = filtered,
            fatigue = context.request.muscleFatigue,
            weekUsed = context.weekUsed,
            recentExerciseIdsByPattern = context.request.recentExerciseIdsByPattern,
            weeklyVolume = context.weeklyVolume,
            target = WeeklyVolumeTargets.forGoal(context.request.goal),
            exercisePreferences = context.request.exercisePreferences
        )
        val replacedExercise = exercises.firstOrNull { it.id == context.entry.exerciseId }
        val directMuscle = replacedExercise?.let { DirectArmCoverage.muscleFor(it) }
            ?: return ranked
        return ranked.sortedBy { !DirectArmCoverage.qualifies(it, directMuscle) }
    }

    private fun contextFor(
        plan: AcceptedPlan,
        dayIndex: Int,
        position: Int,
        request: PlanRequest,
        exercises: List<Exercise>
    ): SlotContext? {
        val day = plan.days.firstOrNull { it.dayIndex == dayIndex } ?: return null
        val entry = day.exercises.getOrNull(position) ?: return null
        val exercisesById = exercises.associateBy { it.id }
        // The replaced slot is removed from the volume ledger so the candidate is ranked against the
        // rest of the plan, not against the exercise it replaces.
        val daysWithoutSlot = plan.days.map { planDay ->
            WorkoutDay(
                dayIndex = planDay.dayIndex,
                focus = planDay.focus,
                exercises = planDay.exercises.mapIndexedNotNull { index, exercise ->
                    if (planDay.dayIndex == dayIndex && index == position) {
                        null
                    } else {
                        PlannedExercise(
                            exerciseId = exercise.exerciseId,
                            sets = exercise.sets,
                            reps = exercise.reps,
                            suggestedWeightKg = exercise.suggestedWeightKg,
                            loadKind = exercise.loadKind,
                            loadCapability = exercise.loadCapability
                        )
                    }
                }
            )
        }
        val weekUsed = plan.days
            .flatMap { planDay ->
                planDay.exercises.mapIndexedNotNull { index, exercise ->
                    if (planDay.dayIndex == dayIndex &&
                        index == position
                    ) {
                        null
                    } else {
                        exercise.exerciseId
                    }
                }
            }
            .toSet()
        val sameDayIds = day.exercises.mapIndexedNotNull { index, exercise ->
            if (index == position) null else exercise.exerciseId
        }.toSet()
        return SlotContext(
            entry = entry,
            request = request,
            weekUsed = weekUsed,
            sameDayIds = sameDayIds,
            weeklyVolume = WeeklyVolumeTargets.weightedSetsByMuscle(daysWithoutSlot, exercisesById)
        )
    }

    /**
     * Keeps the slot's working weight and its meaning when the replacement's capability permits
     * that meaning, only lowering the number to the replacement's equipment ceiling. When the
     * meaning is not permitted (e.g. an external slot replaced by a bodyweight-only exercise) the
     * slot falls back to the replacement's default kind with no number, and the preview shows the
     * no-load target before the user confirms.
     */
    private fun preservedLoad(
        entry: AcceptedExercise,
        candidate: Exercise,
        equipmentMaxWeights: Map<EquipmentTag, Double>
    ): Pair<Double?, LoadKind> {
        val capability = candidate.loadCapability
        if (!WorkoutLoadPolicy.permits(capability, entry.loadKind)) {
            return null to WorkoutLoadPolicy.defaultKind(capability)
        }
        val clamped = entry.suggestedWeightKg?.let {
            EquipmentWeightLimit.clamp(
                it,
                EquipmentWeightLimit.ceilingFor(candidate, equipmentMaxWeights)
            )
        }
        return clamped to entry.loadKind
    }

    /**
     * The implement a candidate is shown under. Built-in equipment wins over custom by the catalog's
     * own order (so a dumbbell+bench exercise reports dumbbells, not the bench), and custom tags fall
     * back to id order so the chip is stable regardless of the underlying set's iteration order.
     */
    private fun primaryEquipmentOf(exercise: Exercise): EquipmentTag = exercise.requiredEquipment
        .filterNot { it == EquipmentTag.BODYWEIGHT }
        .minWithOrNull(
            compareBy<EquipmentTag>(
                { tag ->
                    EquipmentTag.BUILT_IN.indexOf(tag)
                        .takeIf { index -> index >= 0 } ?: Int.MAX_VALUE
                },
                { it.id }
            )
        )
        ?: EquipmentTag.BODYWEIGHT

    private data class SlotContext(
        val entry: AcceptedExercise,
        val request: PlanRequest,
        val weekUsed: Set<String>,
        val sameDayIds: Set<String>,
        val weeklyVolume: Map<MuscleGroup, Double>
    )
}
