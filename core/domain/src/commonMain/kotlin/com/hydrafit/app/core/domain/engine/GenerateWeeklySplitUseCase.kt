package com.hydrafit.app.core.domain.engine

/**
 * Runs the selected engine and then applies the user's opt-in planner pairing to the result.
 *
 * Post-processing here, rather than inside an engine, keeps deterministic, Gemini and local-LLM
 * output consistent: every engine sees the same adjacency rule and the same rest values, and no
 * engine can add pairing the user did not ask for. A null preference returns the plan unchanged.
 */
class GenerateWeeklySplitUseCase(private val engines: WorkoutPlannerEngineProvider) {
    suspend operator fun invoke(request: PlanRequest): WeeklyPlan = PlannerSupersetPairing.apply(
        engines.get().generatePlan(request),
        request.plannerSupersetPreference
    )
}
