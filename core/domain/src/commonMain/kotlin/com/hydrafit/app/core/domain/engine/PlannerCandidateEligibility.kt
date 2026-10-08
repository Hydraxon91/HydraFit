package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.Exercise

/** Hard equipment/exclusion eligibility shared by generation, model validation and substitution. */
object PlannerCandidateEligibility {
    fun candidates(exercises: List<Exercise>, request: PlanRequest): List<Exercise> =
        exercises.filter {
            it.isAvailableWith(request.availableEquipment) && it.id !in request.excludedExerciseIds
        }

    /**
     * Reject only a focus with no equipment/exclusion-eligible work, before engine generation.
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
