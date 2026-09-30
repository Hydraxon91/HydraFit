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
    private val suggestWeights: SuggestWeightsUseCase,
    private val buildRecentWeights: BuildRecentWeightsUseCase,
    private val progressWeights: ProgressWeightsUseCase,
    private val periodization: PeriodizationConfig
) {
    operator fun invoke(
        setsPerExercise: Flow<Int?> = flowOf(null),
        accessorySetsPerExercise: Flow<Int?> = flowOf(null),
        refreshRequests: Flow<Unit> = emptyFlow()
    ): Flow<WorkoutPlanInputs> = combine(
        sources.observe().distinctUntilChanged(),
        setsPerExercise.distinctUntilChanged(),
        accessorySetsPerExercise.distinctUntilChanged(),
        refreshRequests.onStart { emit(Unit) }
    ) { current, sets, accessorySets, _ ->
        val nowMillis = timeProvider.nowMillis()
        val latestPlan = planHistoryRepository.latest()
        val (weekNumber, cycleNumber) = nextPeriodization(latestPlan)
        // A logged set's Epley 1RM, lifted to at least any manually entered personal record.
        val baseline = suggestWeights(current.loggedWorkoutSets).toMutableMap()
        current.personalRecords.forEach { record ->
            baseline[record.exerciseId] = maxOf(
                baseline[record.exerciseId] ?: 0.0,
                OneRepMax.estimate(record.weightKg, record.reps)
            )
        }
        // Accessory slots are exempt from week-over-week rotation: only compound patterns rotate.
        val recentExerciseIdsByPattern = latestPlan
            ?.days
            ?.flatMap { day ->
                day.exercises
                    .filter { it.movementPattern.isCompound }
                    .map { it.movementPattern to it.exerciseId }
            }
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
                accessorySetsPerExercise = accessorySets ?: current.goal.accessorySets,
                recentExerciseIdsByPattern = recentExerciseIdsByPattern,
                suggestedWeightsKg = progressWeights(
                    baseline = baseline,
                    prescriptions = prescriptionsFrom(latestPlan),
                    sets = current.loggedWorkoutSets,
                    pauseIncrements = latestPlan?.let {
                        periodization.isDeload(it.weekNumber)
                    } ?: false
                ),
                equipmentMaxWeights = current.equipmentMaxWeights,
                includeWorkoutData = current.workoutDataSharingEnabled,
                recentWeights = if (current.workoutDataSharingEnabled) {
                    buildRecentWeights(current.loggedWorkoutSets)
                } else {
                    emptyList()
                },
                weekNumber = weekNumber,
                cycleNumber = cycleNumber,
                isDeload = periodization.isDeload(weekNumber)
            ),
            requestedEngine = current.selectedEngine
        )
    }

    /** The next week to generate: advances the accepted plan's week, or starts at 1/1 with none. */
    private fun nextPeriodization(latest: AcceptedPlan?): Pair<Int, Int> {
        if (latest == null) return 1 to 1
        val week = periodization.nextWeek(latest.weekNumber)
        val cycle = if (week == 1) latest.cycleNumber + 1 else latest.cycleNumber
        return week to cycle
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
