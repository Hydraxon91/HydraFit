package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.fatigue.CalculateMuscleFatigueUseCase
import com.hydrafit.app.core.domain.time.TimeProvider
import kotlin.test.Test
import kotlin.test.assertEquals
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
        val useCase = ObserveWorkoutPlanInputsUseCase(
            sources = sources,
            calculateMuscleFatigue = CalculateMuscleFatigueUseCase(),
            timeProvider = TimeProvider { 100L }
        )

        val inputs = useCase().first()

        assertEquals(TrainingGoal.STRENGTH, inputs.request.goal)
        assertEquals(TrainingGoal.STRENGTH.defaultSets, inputs.request.setsPerExercise)
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
        val useCase = ObserveWorkoutPlanInputsUseCase(
            sources = sources,
            calculateMuscleFatigue = CalculateMuscleFatigueUseCase(),
            timeProvider = TimeProvider { 100L }
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
        val useCase = ObserveWorkoutPlanInputsUseCase(
            sources = sources,
            calculateMuscleFatigue = CalculateMuscleFatigueUseCase(),
            timeProvider = TimeProvider { nowMillis }
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

    private class FakeWorkoutPlanSourcesRepository(initial: WorkoutPlanSources) :
        WorkoutPlanSourcesRepository {
        private val state = MutableStateFlow(initial)

        override fun observe() = state.asStateFlow()

        fun update(sources: WorkoutPlanSources) {
            state.value = sources
        }
    }
}
