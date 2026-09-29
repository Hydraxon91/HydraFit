package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.fatigue.CalculateMuscleFatigueUseCase
import com.hydrafit.app.core.domain.time.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart

class ObserveWorkoutPlanInputsUseCase(
    private val sources: WorkoutPlanSourcesRepository,
    private val calculateMuscleFatigue: CalculateMuscleFatigueUseCase,
    private val timeProvider: TimeProvider,
    private val planHistoryRepository: PlanHistoryRepository,
    private val suggestWeights: SuggestWeightsUseCase = SuggestWeightsUseCase(),
    private val buildRecentWeights: BuildRecentWeightsUseCase = BuildRecentWeightsUseCase(),
    private val progressWeights: ProgressWeightsUseCase = ProgressWeightsUseCase()
) {
    operator fun invoke(
        setsPerExercise: Flow<Int?> = flowOf(null),
        refreshRequests: Flow<Unit> = emptyFlow()
    ): Flow<WorkoutPlanInputs> = combine(
        sources.observe().distinctUntilChanged(),
        setsPerExercise.distinctUntilChanged(),
        refreshRequests.onStart { emit(Unit) }
    ) { current, sets, _ ->
        val nowMillis = timeProvider.nowMillis()
        val latestPlan = planHistoryRepository.latest()
        val recentExerciseIdsByPattern = latestPlan
            ?.days
            ?.flatMap { day -> day.exercises.map { it.movementPattern to it.exerciseId } }
            ?.groupBy({ it.first }, { it.second })
            ?.mapValues { (_, ids) -> ids.toSet() }
            .orEmpty()
        WorkoutPlanInputs(
            request = PlanRequest(
                daysPerWeek = current.daysPerWeek,
                availableEquipment = current.availableEquipment,
                muscleFatigue = calculateMuscleFatigue(current.loggedSets, nowMillis),
                nowMillis = nowMillis,
                goal = current.goal,
                setsPerExercise = sets ?: current.goal.defaultSets,
                recentExerciseIdsByPattern = recentExerciseIdsByPattern,
                suggestedWeightsKg = progressWeights(
                    baseline = suggestWeights(current.loggedWorkoutSets, current.goal),
                    prescriptions = prescriptionsFrom(latestPlan),
                    sets = current.loggedWorkoutSets
                ),
                includeWorkoutData = current.workoutDataSharingEnabled,
                recentWeights = if (current.workoutDataSharingEnabled) {
                    buildRecentWeights(current.loggedWorkoutSets)
                } else {
                    emptyList()
                }
            ),
            requestedEngine = current.selectedEngine
        )
    }

    /** The weight each exercise was prescribed in the most recent accepted plan. */
    private fun prescriptionsFrom(plan: AcceptedPlan?): Map<String, Prescription> = plan
        ?.days
        ?.flatMap { it.exercises }
        ?.mapNotNull { exercise ->
            val weight = exercise.suggestedWeightKg ?: return@mapNotNull null
            exercise.exerciseId to Prescription(
                exerciseId = exercise.exerciseId,
                sets = exercise.sets,
                reps = exercise.reps,
                weightKg = weight
            )
        }
        ?.toMap()
        .orEmpty()
}
