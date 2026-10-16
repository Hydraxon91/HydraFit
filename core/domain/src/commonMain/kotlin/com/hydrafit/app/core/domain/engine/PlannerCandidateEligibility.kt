package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.MovementPattern

/** Hard eligibility shared by generation, model validation and substitution, not manual logging. */
object PlannerCandidateEligibility {
    fun candidates(exercises: List<Exercise>, request: PlanRequest): List<Exercise> =
        exercises.filter {
            it.isAvailableWith(request.availableEquipment) &&
                it.id !in request.excludedExerciseIds &&
                it.id !in MANUAL_ONLY_EXERCISE_IDS &&
                it.movementPattern !in MANUAL_ONLY_PATTERNS
        }

    // Pin curated identities too: changing a built-in's pattern must not opt it into planning.
    private val MANUAL_ONLY_EXERCISE_IDS = setOf(
        "dumbbell-shrug",
        "ez-bar-shrug",
        "trap-bar-shrug",
        "barbell-shrug",
        "forehead-neck-bridge",
        "self-resisted-neck-lateral-flexion"
    )
    private val MANUAL_ONLY_PATTERNS = setOf(
        MovementPattern.TRAP_ISOLATION,
        MovementPattern.NECK_ISOLATION
    )

    /**
     * Reject a focus with no automatically eligible work, before engine generation.
     * Soreness, volume deficits and capacity do not participate in this hard eligibility check.
     */
    fun requireWorkouts(exercises: List<Exercise>, request: PlanRequest) {
        val eligible = candidates(exercises, request)
        val missing = SplitResolver.focusSequence(request.splitPreference, request.daysPerWeek)
            .firstOrNull { focus ->
                val patterns = SplitResolver.compoundGroups(focus).flatten() +
                    DirectArmCoverage.isolationPatternsFor(focus)
                eligible.none { it.movementPattern in patterns }
            }
        if (missing != null) {
            throw PlanGenerationException(
                transient = false,
                reason = PlanFailureReason.NO_ELIGIBLE_EXERCISES,
                message = "No equipment/exclusion-eligible exercises for $missing"
            )
        }
    }
}
