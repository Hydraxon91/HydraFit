package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.workout.LoadKind

/** A ranked replacement for one accepted-plan slot, shown so the user can pick one. */
data class SwapCandidate(
    val exerciseId: String,
    val name: String,
    val movementPattern: MovementPattern,
    val primaryEquipment: EquipmentTag,
    val suggestedWeightKg: Double?,
    /** The meaning of [suggestedWeightKg] after the swap, from the candidate's own prescription. */
    val loadKind: LoadKind = LoadKind.EXTERNAL,
    /** True when the candidate requires dumbbells, so a numeric load is per implement. */
    val requiresDumbbell: Boolean = false,
    /** True for one-side-at-a-time work, whose dumbbell load is per hand. */
    val isUnilateral: Boolean = false,
    /**
     * Why this candidate's own numeric target is present, uses the generic estimate, or is absent.
     * Derived from the candidate's prescription and the request's inventory; null means unknown.
     */
    val loadInventoryOutcome: LoadInventoryOutcome? = null
)

/**
 * Swaps one exercise inside an already-accepted plan, persisting the change in place (the plan keeps
 * its id and acceptance time). Candidates must train the same movement pattern and be available with
 * the user's equipment; they are ranked by the same fatigue/deficit/freshness order the generator
 * uses, so a substitution reads like a fresh pick rather than an arbitrary swap.
 *
 * The slot's sets, reps and progression policy are preserved: substitution replaces the exercise, it
 * does not re-plan the slot. The numeric load is not: it is converted from the replacement's own
 * progressed baseline at the slot's rep count, so the old exercise's number never carries over.
 */
class SubstituteExerciseUseCase(
    private val catalog: ExerciseCatalog,
    private val planHistory: PlanHistoryRepository,
    private val rankingEngine: DeterministicWorkoutPlannerEngine,
    private val weightConfig: SuggestedWeightConfig
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
            val load = resolvedLoad(context.entry, candidate, plan, request)
            SwapCandidate(
                exerciseId = candidate.id,
                name = candidate.name,
                movementPattern = candidate.movementPattern,
                primaryEquipment = primaryEquipmentOf(candidate),
                suggestedWeightKg = load.weightKg,
                loadKind = load.loadKind,
                requiresDumbbell = EquipmentTag.DUMBBELL in candidate.requiredEquipment,
                isUnilateral = candidate.isUnilateral,
                loadInventoryOutcome = loadInventoryOutcome(
                    suggestedWeightKg = load.weightKg,
                    unavailableReason = load.explanation.unavailableReason,
                    hasConvertedWorkingLoad = load.explanation.conversion != null,
                    applicableInventory = hasApplicableLoadingInventory(
                        request.loadingInventory,
                        candidate.requiredEquipment
                    )
                )
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

        val load = resolvedLoad(context.entry, chosen, plan, request)
        val setTargets = recalculatedOrderedTargets(
            targets = context.entry.setTargets,
            capability = chosen.loadCapability,
            candidateId = chosen.id,
            request = request,
            equipmentCeilingKg = EquipmentWeightLimit.ceilingFor(
                chosen,
                request.equipmentMaxWeights
            ),
            intensityScale = intensityScale(plan),
            weightConfig = weightConfig,
            requiredEquipment = chosen.requiredEquipment,
            equipmentId = context.entry.progressionPolicy?.equipmentId
        )
        planHistory.substituteWithProvenance(
            planId = plan.id,
            dayIndex = dayIndex,
            position = position,
            newExerciseId = chosen.id,
            newExerciseName = chosen.name,
            newWeightKg = load.weightKg,
            newLoadCapability = chosen.loadCapability,
            newLoadKind = load.loadKind,
            setTargets = setTargets,
            explanation = load.explanation
        )
        return AcceptedExercise(
            exerciseId = chosen.id,
            sets = context.entry.sets,
            reps = context.entry.reps,
            durationSeconds = context.entry.durationSeconds,
            distanceMeters = context.entry.distanceMeters,
            forceNewtons = context.entry.forceNewtons,
            name = chosen.name,
            movementPattern = chosen.movementPattern,
            suggestedWeightKg = load.weightKg,
            loadCapability = chosen.loadCapability,
            loadKind = load.loadKind,
            // A swap records the replacement's own reasons. A legacy plan has no frozen plan-level
            // context to read them back with, so it renders as unrecorded, matching the reader.
            explanation = load.explanation.takeIf { plan.explanation != null },
            // A swap re-prescribes only the exercise. The user-approved rule and ordered targets are
            // slot configuration, not exercise properties; the policy survives and each ordered
            // target keeps its measure while its numeric load is rederived for the new exercise.
            progressionPolicy = context.entry.progressionPolicy,
            setTargets = setTargets,
            // A swap re-prescribes only this slot; the frozen pair is rest configuration, not an
            // exercise property, so it stays with the slot the user paired.
            superset = context.entry.superset
        )
    }

    private fun rankedCandidates(context: SlotContext, exercises: List<Exercise>): List<Exercise> {
        val available = PlannerCandidateEligibility.candidates(exercises, context.request)
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
                            durationSeconds = exercise.durationSeconds,
                            distanceMeters = exercise.distanceMeters,
                            forceNewtons = exercise.forceNewtons,
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
     * The replacement's own load at the slot's prescribed reps, with the accepted plan's deload
     * scale. A candidate with no usable baseline gets no number rather than the replaced exercise's.
     */
    private fun resolvedLoad(
        entry: AcceptedExercise,
        candidate: Exercise,
        plan: AcceptedPlan,
        request: PlanRequest
    ): CandidatePrescribedLoad = resolveCandidatePrescribedLoad(
        candidateId = candidate.id,
        capability = candidate.loadCapability,
        reps = entry.reps,
        request = request,
        equipmentCeilingKg = EquipmentWeightLimit.ceilingFor(
            candidate,
            request.equipmentMaxWeights
        ),
        intensityScale = intensityScale(plan),
        weightConfig = weightConfig,
        requiredEquipment = candidate.requiredEquipment,
        equipmentId = entry.progressionPolicy?.equipmentId
    )

    /**
     * The accepted plan's own deload scale. The current request describes the next plan, so its
     * `isDeload` flag cannot describe the week this slot already belongs to. A legacy plan with no
     * frozen context has no recorded scale and uses the normal-week value.
     */
    private fun intensityScale(plan: AcceptedPlan): Double {
        val frozen = plan.explanation ?: return 1.0
        return if (frozen.isDeload) frozen.deloadIntensityScale else 1.0
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
