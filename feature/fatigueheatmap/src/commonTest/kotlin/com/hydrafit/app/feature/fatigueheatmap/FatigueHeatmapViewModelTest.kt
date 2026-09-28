package com.hydrafit.app.feature.fatigueheatmap

import com.hydrafit.app.core.domain.fatigue.CalculateMuscleFatigueUseCase
import com.hydrafit.app.core.domain.fatigue.LoggedSet
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.fatigue.MuscleInvolvement
import com.hydrafit.app.core.domain.fatigue.MuscleTarget
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.workout.WorkoutLogRepository
import com.hydrafit.app.core.domain.workout.WorkoutSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class FatigueHeatmapViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun emptyHistoryYieldsZeroForEveryMuscle() = runTest(dispatcher) {
        val viewModel = viewModel(sets = emptyList(), nowMillis = 0L)
        advanceUntilIdle()

        assertEquals(MuscleGroup.entries.size, viewModel.state.value.entries.size)
        assertTrue(viewModel.state.value.entries.all { it.score == 0.0 })
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun scoresTrainedMuscleRelativeToProvidedTime() = runTest(dispatcher) {
        val sets = List(24) { chestSet(timestampMillis = 0L) }

        val fresh = viewModel(sets, nowMillis = 0L)
        advanceUntilIdle()
        assertEquals(1.0, fresh.scoreOf(MuscleGroup.CHEST), 1e-9)
        assertEquals(0.0, fresh.scoreOf(MuscleGroup.QUADS), 1e-9)

        val decayed = viewModel(sets, nowMillis = 48L * 60L * 60L * 1000L)
        advanceUntilIdle()
        assertEquals(0.5, decayed.scoreOf(MuscleGroup.CHEST), 1e-9)
    }

    private fun FatigueHeatmapViewModel.scoreOf(muscle: MuscleGroup): Double =
        state.value.entries.first { it.muscle == muscle }.score

    private fun chestSet(timestampMillis: Long) = LoggedSet(
        timestampMillis = timestampMillis,
        targets = listOf(MuscleTarget(MuscleGroup.CHEST, MuscleInvolvement.PRIMARY))
    )

    private fun viewModel(sets: List<LoggedSet>, nowMillis: Long) = FatigueHeatmapViewModel(
        workoutLogRepository = FakeWorkoutLogRepository(sets),
        calculateMuscleFatigue = CalculateMuscleFatigueUseCase(),
        timeProvider = TimeProvider { nowMillis }
    )

    private class FakeWorkoutLogRepository(private val sets: List<LoggedSet>) : WorkoutLogRepository {
        override suspend fun add(set: WorkoutSet) = Unit

        override suspend fun all(): List<WorkoutSet> = emptyList()

        override suspend fun loggedSets(): List<LoggedSet> = sets

        override suspend fun clear() = Unit
    }
}
