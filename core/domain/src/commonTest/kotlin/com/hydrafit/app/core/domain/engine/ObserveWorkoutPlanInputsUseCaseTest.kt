package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.CalculateMuscleFatigueUseCase
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.workout.WorkoutSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class ObserveWorkoutPlanInputsUseCaseTest {

    @Test
    fun usesTheGoalDefaultSetsWhenThereIsNoManualOverride() = runTest {
        val sources = FakeWorkoutPlanSourcesRepository(
            WorkoutPlanSources(
                availableEquipment = setOf(EquipmentTag.BARBELL),
                selectedEngine = PlannerEngineId.DETERMINISTIC,
                daysPerWeek = 4,
                loggedSets = emptyList(),
                goal = TrainingGoal.STRENGTH
            )
        )
        val useCase = useCase(
            sources = sources,
            calculateMuscleFatigue = CalculateMuscleFatigueUseCase(),
            timeProvider = TimeProvider { 100L },
            planHistoryRepository = FakePlanHistoryRepository()
        )

        val inputs = useCase().first()

        assertEquals(TrainingGoal.STRENGTH, inputs.request.goal)
        assertEquals(TrainingGoal.STRENGTH.defaultSets, inputs.request.setsPerExercise)
    }

    @Test
    fun startsAtWeekOneWithoutAnAcceptedPlan() = runTest {
        val useCase = useCase(
            sources = FakeWorkoutPlanSourcesRepository(
                WorkoutPlanSources(
                    availableEquipment = setOf(EquipmentTag.BARBELL),
                    selectedEngine = PlannerEngineId.DETERMINISTIC,
                    daysPerWeek = 3,
                    loggedSets = emptyList()
                )
            ),
            calculateMuscleFatigue = CalculateMuscleFatigueUseCase(),
            timeProvider = TimeProvider { 0L },
            planHistoryRepository = FakePlanHistoryRepository()
        )

        val request = useCase().first().request

        assertEquals(1, request.weekNumber)
        assertEquals(1, request.cycleNumber)
    }

    @Test
    fun advancesTheWeekAfterAnAcceptedPlan() = runTest {
        val accepted = AcceptedPlan(
            engine = PlannerEngineId.DETERMINISTIC,
            acceptedAtMillis = 1L,
            weekNumber = 2,
            cycleNumber = 1,
            days = emptyList()
        )
        val useCase = useCase(
            sources = FakeWorkoutPlanSourcesRepository(
                WorkoutPlanSources(
                    availableEquipment = setOf(EquipmentTag.BARBELL),
                    selectedEngine = PlannerEngineId.DETERMINISTIC,
                    daysPerWeek = 3,
                    loggedSets = emptyList()
                )
            ),
            calculateMuscleFatigue = CalculateMuscleFatigueUseCase(),
            timeProvider = TimeProvider { 0L },
            planHistoryRepository = FakePlanHistoryRepository(accepted)
        )

        val request = useCase().first().request

        assertEquals(3, request.weekNumber)
        assertEquals(1, request.cycleNumber)
    }

    @Test
    fun wrapsToTheNextCycleAfterTheDeloadWeek() = runTest {
        val accepted = AcceptedPlan(
            engine = PlannerEngineId.DETERMINISTIC,
            acceptedAtMillis = 1L,
            weekNumber = 4,
            cycleNumber = 1,
            days = emptyList()
        )
        val useCase = useCase(
            sources = FakeWorkoutPlanSourcesRepository(
                WorkoutPlanSources(
                    availableEquipment = setOf(EquipmentTag.BARBELL),
                    selectedEngine = PlannerEngineId.DETERMINISTIC,
                    daysPerWeek = 3,
                    loggedSets = emptyList()
                )
            ),
            calculateMuscleFatigue = CalculateMuscleFatigueUseCase(),
            timeProvider = TimeProvider { 0L },
            planHistoryRepository = FakePlanHistoryRepository(accepted)
        )

        val request = useCase().first().request

        assertEquals(1, request.weekNumber)
        assertEquals(2, request.cycleNumber)
    }

    @Test
    fun manualSetCountOverridesTheGoalDefault() = runTest {
        val sources = FakeWorkoutPlanSourcesRepository(
            WorkoutPlanSources(
                availableEquipment = setOf(EquipmentTag.BARBELL),
                selectedEngine = PlannerEngineId.DETERMINISTIC,
                daysPerWeek = 4,
                loggedSets = emptyList(),
                goal = TrainingGoal.STRENGTH
            )
        )
        val useCase = useCase(
            sources = sources,
            calculateMuscleFatigue = CalculateMuscleFatigueUseCase(),
            timeProvider = TimeProvider { 100L },
            planHistoryRepository = FakePlanHistoryRepository()
        )

        val inputs = useCase(setsPerExercise = flowOf(6)).first()

        assertEquals(TrainingGoal.STRENGTH, inputs.request.goal)
        assertEquals(6, inputs.request.setsPerExercise)
    }

    @Test
    fun emitsRequestsWhenSourcesSetCountOrRefreshChanges() = runTest {
        val source = WorkoutPlanSources(
            availableEquipment = setOf(EquipmentTag.BARBELL),
            selectedEngine = PlannerEngineId.DETERMINISTIC,
            daysPerWeek = 4,
            loggedSets = emptyList()
        )
        val sources = FakeWorkoutPlanSourcesRepository(source)
        val sets = MutableStateFlow(2)
        val refresh = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        var nowMillis = 100L
        val useCase = useCase(
            sources = sources,
            calculateMuscleFatigue = CalculateMuscleFatigueUseCase(),
            timeProvider = TimeProvider { nowMillis },
            planHistoryRepository = FakePlanHistoryRepository()
        )
        val emitted = mutableListOf<WorkoutPlanInputs>()

        val collection = backgroundScope.launch {
            useCase(setsPerExercise = sets, refreshRequests = refresh)
                .take(4)
                .toList(emitted)
        }
        runCurrent()

        nowMillis = 200L
        sets.value = 5
        runCurrent()

        nowMillis = 300L
        sources.update(source.copy(daysPerWeek = 5, selectedEngine = PlannerEngineId.GEMINI_API))
        runCurrent()

        nowMillis = 400L
        refresh.tryEmit(Unit)
        runCurrent()
        collection.join()

        assertEquals(listOf(2, 5, 5, 5), emitted.map { it.request.setsPerExercise })
        assertEquals(listOf(100L, 200L, 300L, 400L), emitted.map { it.request.nowMillis })
        assertEquals(listOf(4, 4, 5, 5), emitted.map { it.request.daysPerWeek })
        assertEquals(
            listOf(
                PlannerEngineId.DETERMINISTIC,
                PlannerEngineId.DETERMINISTIC,
                PlannerEngineId.GEMINI_API,
                PlannerEngineId.GEMINI_API
            ),
            emitted.map { it.requestedEngine }
        )
        assertTrue(emitted.all { it.request.availableEquipment == setOf(EquipmentTag.BARBELL) })
    }

    @Test
    fun addsPreviousAcceptedSelectionsToTheRequest() = runTest {
        val accepted = AcceptedPlan(
            engine = PlannerEngineId.DETERMINISTIC,
            acceptedAtMillis = 1L,
            days = listOf(
                AcceptedDay(
                    dayIndex = 0,
                    focus = SplitFocus.PUSH,
                    exercises = listOf(
                        AcceptedExercise(
                            exerciseId = "bench-press",
                            sets = 3,
                            reps = 8,
                            name = "Bench Press",
                            movementPattern = MovementPattern.HORIZONTAL_PUSH
                        )
                    )
                )
            )
        )
        val history = FakePlanHistoryRepository(accepted)
        val useCase = useCase(
            sources = FakeWorkoutPlanSourcesRepository(
                WorkoutPlanSources(
                    availableEquipment = setOf(EquipmentTag.BARBELL),
                    selectedEngine = PlannerEngineId.DETERMINISTIC,
                    daysPerWeek = 3,
                    loggedSets = emptyList()
                )
            ),
            calculateMuscleFatigue = CalculateMuscleFatigueUseCase(),
            timeProvider = TimeProvider { 0L },
            planHistoryRepository = history
        )

        val request = useCase().first().request

        assertEquals(
            setOf("bench-press"),
            request.recentExerciseIdsByPattern[MovementPattern.HORIZONTAL_PUSH]
        )
    }

    @Test
    fun estimatesTheOneRepMaxFromLoggedSets() = runTest {
        val sources = FakeWorkoutPlanSourcesRepository(
            WorkoutPlanSources(
                availableEquipment = setOf(EquipmentTag.BARBELL),
                selectedEngine = PlannerEngineId.DETERMINISTIC,
                daysPerWeek = 3,
                loggedSets = emptyList(),
                goal = TrainingGoal.STRENGTH,
                loggedWorkoutSets = listOf(
                    WorkoutSet(
                        exerciseId = "bench-press",
                        reps = 5,
                        weightKg = 100.0,
                        performedAtMillis = 1L
                    )
                )
            )
        )
        val useCase = useCase(
            sources = sources,
            calculateMuscleFatigue = CalculateMuscleFatigueUseCase(),
            timeProvider = TimeProvider { 0L },
            planHistoryRepository = FakePlanHistoryRepository()
        )

        val request = useCase().first().request

        // 100kg x 5 -> Epley 1RM 116.666...
        assertEquals(116.66666666666667, request.suggestedWeightsKg["bench-press"])
    }

    @Test
    fun sharesRecentWeightsOnlyWhenSharingIsEnabled() = runTest {
        fun sources(enabled: Boolean) = FakeWorkoutPlanSourcesRepository(
            WorkoutPlanSources(
                availableEquipment = setOf(EquipmentTag.BARBELL),
                selectedEngine = PlannerEngineId.DETERMINISTIC,
                daysPerWeek = 3,
                loggedSets = emptyList(),
                workoutDataSharingEnabled = enabled,
                loggedWorkoutSets = listOf(
                    WorkoutSet(
                        exerciseId = "bench-press",
                        reps = 5,
                        weightKg = 100.0,
                        performedAtMillis = 1L
                    )
                )
            )
        )
        fun useCase(sources: WorkoutPlanSourcesRepository) = useCase(
            sources = sources,
            calculateMuscleFatigue = CalculateMuscleFatigueUseCase(),
            timeProvider = TimeProvider { 0L },
            planHistoryRepository = FakePlanHistoryRepository()
        )

        val off = useCase(sources(false))().first().request
        assertFalse(off.includeWorkoutData)
        assertTrue(off.recentWeights.isEmpty())

        val on = useCase(sources(true))().first().request
        assertTrue(on.includeWorkoutData)
        assertEquals(listOf("bench-press"), on.recentWeights.map { it.exerciseId })
    }

    @Test
    fun progressesTheSuggestedWeightWhenTheAcceptedPrescriptionIsCompleted() = runTest {
        val accepted = AcceptedPlan(
            engine = PlannerEngineId.DETERMINISTIC,
            acceptedAtMillis = 1L,
            days = listOf(
                AcceptedDay(
                    dayIndex = 0,
                    focus = SplitFocus.PUSH,
                    exercises = listOf(
                        AcceptedExercise(
                            exerciseId = "bench-press",
                            sets = 3,
                            reps = 8,
                            name = "Bench Press",
                            movementPattern = MovementPattern.HORIZONTAL_PUSH,
                            suggestedWeightKg = 100.0
                        )
                    )
                )
            )
        )
        val completedDays = (3 downTo 1).flatMap { day ->
            List(3) {
                WorkoutSet(
                    exerciseId = "bench-press",
                    reps = 8,
                    weightKg = 100.0,
                    performedAtMillis = day.toLong() * 24L * 60L * 60L * 1000L
                )
            }
        }
        val sources = FakeWorkoutPlanSourcesRepository(
            WorkoutPlanSources(
                availableEquipment = setOf(EquipmentTag.BARBELL),
                selectedEngine = PlannerEngineId.DETERMINISTIC,
                daysPerWeek = 3,
                loggedSets = emptyList(),
                loggedWorkoutSets = completedDays
            )
        )
        val useCase = useCase(
            sources = sources,
            calculateMuscleFatigue = CalculateMuscleFatigueUseCase(),
            timeProvider = TimeProvider { 0L },
            planHistoryRepository = FakePlanHistoryRepository(accepted)
        )

        val request = useCase().first().request

        // Baseline 1RM estimate 100x8 -> 126.667, then +2.5 after three completed sessions.
        assertEquals(129.16666666666666, request.suggestedWeightsKg["bench-press"])
    }

    @Test
    fun pausesProgressionWhenTheLatestAcceptedPlanIsADeloadWeek() = runTest {
        val accepted = AcceptedPlan(
            engine = PlannerEngineId.DETERMINISTIC,
            acceptedAtMillis = 1L,
            weekNumber = 4,
            days = listOf(
                AcceptedDay(
                    dayIndex = 0,
                    focus = SplitFocus.PUSH,
                    exercises = listOf(
                        AcceptedExercise(
                            exerciseId = "bench-press",
                            sets = 3,
                            reps = 8,
                            name = "Bench Press",
                            movementPattern = MovementPattern.HORIZONTAL_PUSH,
                            suggestedWeightKg = 100.0
                        )
                    )
                )
            )
        )
        val completedDays = (3 downTo 1).flatMap { day ->
            List(3) {
                WorkoutSet(
                    exerciseId = "bench-press",
                    reps = 8,
                    weightKg = 100.0,
                    performedAtMillis = day.toLong() * 24L * 60L * 60L * 1000L
                )
            }
        }
        val useCase = useCase(
            sources = FakeWorkoutPlanSourcesRepository(
                WorkoutPlanSources(
                    availableEquipment = setOf(EquipmentTag.BARBELL),
                    selectedEngine = PlannerEngineId.DETERMINISTIC,
                    daysPerWeek = 3,
                    loggedSets = emptyList(),
                    loggedWorkoutSets = completedDays
                )
            ),
            calculateMuscleFatigue = CalculateMuscleFatigueUseCase(),
            timeProvider = TimeProvider { 0L },
            planHistoryRepository = FakePlanHistoryRepository(accepted)
        )

        val request = useCase().first().request

        // No +2.5 increment during the deload week: baseline 1RM 126.667 unchanged.
        assertEquals(126.66666666666666, request.suggestedWeightsKg["bench-press"])
    }

    @Test
    fun defaultsAccessorySetsFromTheGoalAndOverridesWithThePicker() = runTest {
        val useCase = useCase(
            sources = FakeWorkoutPlanSourcesRepository(
                WorkoutPlanSources(
                    availableEquipment = setOf(EquipmentTag.BARBELL),
                    selectedEngine = PlannerEngineId.DETERMINISTIC,
                    daysPerWeek = 4,
                    loggedSets = emptyList(),
                    goal = TrainingGoal.STRENGTH
                )
            ),
            calculateMuscleFatigue = CalculateMuscleFatigueUseCase(),
            timeProvider = TimeProvider { 0L },
            planHistoryRepository = FakePlanHistoryRepository()
        )

        val default = useCase().first().request
        assertEquals(TrainingGoal.STRENGTH.accessorySets, default.accessorySetsPerExercise)

        val overridden = useCase(accessorySetsPerExercise = flowOf(5)).first().request
        assertEquals(5, overridden.accessorySetsPerExercise)
    }

    @Test
    fun rotationHistoryIncludesCompoundPatternsOnly() = runTest {
        val accepted = AcceptedPlan(
            engine = PlannerEngineId.DETERMINISTIC,
            acceptedAtMillis = 1L,
            days = listOf(
                AcceptedDay(
                    dayIndex = 0,
                    focus = SplitFocus.PUSH,
                    exercises = listOf(
                        AcceptedExercise(
                            exerciseId = "bench-press",
                            sets = 3,
                            reps = 8,
                            name = "Bench Press",
                            movementPattern = MovementPattern.HORIZONTAL_PUSH
                        ),
                        AcceptedExercise(
                            exerciseId = "pushdown",
                            sets = 2,
                            reps = 12,
                            name = "Pushdown",
                            movementPattern = MovementPattern.TRICEPS_ISOLATION
                        )
                    )
                )
            )
        )
        val useCase = useCase(
            sources = FakeWorkoutPlanSourcesRepository(
                WorkoutPlanSources(
                    availableEquipment = setOf(EquipmentTag.BARBELL),
                    selectedEngine = PlannerEngineId.DETERMINISTIC,
                    daysPerWeek = 3,
                    loggedSets = emptyList()
                )
            ),
            calculateMuscleFatigue = CalculateMuscleFatigueUseCase(),
            timeProvider = TimeProvider { 0L },
            planHistoryRepository = FakePlanHistoryRepository(accepted)
        )

        val request = useCase().first().request

        assertEquals(
            setOf("bench-press"),
            request.recentExerciseIdsByPattern[MovementPattern.HORIZONTAL_PUSH]
        )
        assertTrue(request.recentExerciseIdsByPattern.keys.none { !it.isCompound })
    }

    private fun useCase(
        sources: WorkoutPlanSourcesRepository,
        calculateMuscleFatigue: CalculateMuscleFatigueUseCase = CalculateMuscleFatigueUseCase(),
        timeProvider: TimeProvider = TimeProvider { 0L },
        planHistoryRepository: PlanHistoryRepository = FakePlanHistoryRepository()
    ) = ObserveWorkoutPlanInputsUseCase(
        sources = sources,
        calculateMuscleFatigue = calculateMuscleFatigue,
        timeProvider = timeProvider,
        planHistoryRepository = planHistoryRepository,
        suggestWeights = SuggestWeightsUseCase(),
        buildRecentWeights = BuildRecentWeightsUseCase(),
        progressWeights = ProgressWeightsUseCase(),
        periodization = PeriodizationConfig()
    )

    private class FakeWorkoutPlanSourcesRepository(initial: WorkoutPlanSources) :
        WorkoutPlanSourcesRepository {
        private val state = MutableStateFlow(initial)

        override fun observe() = state.asStateFlow()

        fun update(sources: WorkoutPlanSources) {
            state.value = sources
        }
    }

    private class FakePlanHistoryRepository(private val accepted: AcceptedPlan? = null) :
        PlanHistoryRepository {
        override fun observeLatest() = flowOf(accepted)

        override fun observeHistory() = flowOf(listOfNotNull(accepted))

        override suspend fun latest(): AcceptedPlan? = accepted

        override suspend fun accept(plan: AcceptedPlan) = Unit

        override suspend fun clear() = Unit
    }
}
