package com.hydrafit.app.core.domain.engine

/**
 * The plan-mutation surface a feature may call: accepting a draft plan and swapping a single
 * exercise inside an already-accepted one. Grouping them keeps the SplitBuilder ViewModel within
 * its dependency budget instead of injecting each use case separately.
 */
class PlanBuilderActions(
    private val acceptWeeklyPlan: AcceptWeeklyPlanUseCase,
    private val substituteExercise: SubstituteExerciseUseCase
) {
    suspend fun accept(plan: WeeklyPlan) = acceptWeeklyPlan(plan)

    suspend fun swapCandidates(
        plan: AcceptedPlan,
        dayIndex: Int,
        position: Int,
        request: PlanRequest
    ): List<SwapCandidate> = substituteExercise.candidates(plan, dayIndex, position, request)

    suspend fun substitute(
        plan: AcceptedPlan,
        dayIndex: Int,
        position: Int,
        request: PlanRequest,
        selectedExerciseId: String
    ): AcceptedExercise? =
        substituteExercise.invoke(plan, dayIndex, position, request, selectedExerciseId)
}
