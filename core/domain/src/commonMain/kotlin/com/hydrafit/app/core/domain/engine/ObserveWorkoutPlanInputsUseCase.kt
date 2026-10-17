package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.fatigue.CalculateMuscleFatigueUseCase
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.time.localEpochDay
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
    private val buildPlannerLoadInputs: BuildPlannerLoadInputsUseCase,
    private val periodization: PeriodizationConfig
) {
    operator fun invoke(
        setsPerExercise: Flow<Int?> = flowOf(null),
        accessorySetsPerExercise: Flow<Int?> = flowOf(null),
        refreshRequests: Flow<Unit> = emptyFlow(),
        workoutEpochDays: Flow<List<Long>> = flowOf(emptyList())
    ): Flow<WorkoutPlanInputs> = combine(
        sources.observe().distinctUntilChanged(),
        setsPerExercise.distinctUntilChanged(),
        accessorySetsPerExercise.distinctUntilChanged(),
        refreshRequests.onStart { emit(Unit) },
        workoutEpochDays.distinctUntilChanged()
    ) { current, sets, accessorySets, _, requestedDates ->
        val nowMillis = timeProvider.nowMillis()
        val latestPlan = planHistoryRepository.latest()
        val (weekNumber, cycleNumber) = nextPeriodization(latestPlan)
        val utcOffsetMillis = timeProvider.utcOffsetMillis()
        val today = localEpochDay(nowMillis, utcOffsetMillis)
        // A changed frequency or an elapsed date requires new confirmation, never a guessed date.
        val dates = requestedDates.takeIf { selected ->
            selected.size == current.daysPerWeek &&
                selected.all { it in today..Int.MAX_VALUE.toLong() } &&
                selected.zipWithNext().all { (first, second) -> first < second }
        }.orEmpty()
        val recordedSets = current.loggedSets.filter { it.timestampMillis <= nowMillis }
        val projectedFatigue = dates.map { date ->
            calculateMuscleFatigue(recordedSets, nowMillis + (date - today) * 86_400_000L)
        }
        val excludedExerciseIds = current.exerciseExclusions
            .filter { it.isActive(nowMillis) }
            .map { it.exerciseId }
            .toSet()
        val loadInputs = buildPlannerLoadInputs(
            sources = current,
            latestPlan = latestPlan,
            pauseIncrements = latestPlan?.let { periodization.isDeload(it.weekNumber) } ?: false,
            utcOffsetMillis = utcOffsetMillis,
            nowMillis = nowMillis
        )
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
                suggestedWeightsKg = loadInputs.suggestedWeightsKg,
                recentWeightCaps = loadInputs.recentWeightCaps,
                withheldWeightExerciseIds = loadInputs.withheldWeightExerciseIds,
                equipmentMaxWeights = current.equipmentMaxWeights,
                exercisePreferences = current.exercisePreferences,
                excludedExerciseIds = excludedExerciseIds,
                includeWorkoutData = current.workoutDataSharingEnabled,
                recentWeights = loadInputs.recentWeights,
                weekNumber = weekNumber,
                cycleNumber = cycleNumber,
                isDeload = periodization.isDeload(weekNumber),
                loadExplanations = loadInputs.loadExplanations,
                workoutEpochDays = dates,
                fatigueByWorkout = projectedFatigue
            ),
            requestedEngine = current.selectedEngine,
            exercisePreferences = current.exercisePreferences,
            exerciseExclusions = current.exerciseExclusions,
            equipmentContext = current.equipmentContext
        )
    }

    /** The next week to generate: advances the accepted plan's week, or starts at 1/1 with none. */
    private fun nextPeriodization(latest: AcceptedPlan?): Pair<Int, Int> {
        if (latest == null) return 1 to 1
        val week = periodization.nextWeek(latest.weekNumber)
        val cycle = if (week == 1) latest.cycleNumber + 1 else latest.cycleNumber
        return week to cycle
    }
}
