package com.hydrafit.app.feature.fatigueheatmap

import com.hydrafit.app.core.domain.fatigue.CalculateMuscleFatigueUseCase
import com.hydrafit.app.core.domain.fatigue.LoggedSet
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.fatigue.MuscleInvolvement
import com.hydrafit.app.core.domain.fatigue.MuscleTarget
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.workout.WorkoutLogRepository
import com.hydrafit.app.core.domain.workout.WorkoutSet
import hydrafit.feature.fatigueheatmap.generated.resources.Res
import hydrafit.feature.fatigueheatmap.generated.resources.fatigue_percentage
import hydrafit.feature.fatigueheatmap.generated.resources.fatigue_percentage_near_limit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

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
        runCurrent()

        assertEquals(MuscleGroup.entries.size, viewModel.state.value.entries.size)
        assertTrue(viewModel.state.value.entries.all { it.score == 0.0 })
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun scoresTrainedMuscleRelativeToProvidedTime() = runTest(dispatcher) {
        val sets = List(24) { chestSet(timestampMillis = 0L) }

        val fresh = viewModel(sets, nowMillis = 0L)
        runCurrent()
        assertEquals(CHEST_24_FRESH, fresh.scoreOf(MuscleGroup.CHEST), 1e-9)
        assertEquals(0.0, fresh.scoreOf(MuscleGroup.QUADS), 1e-9)

        val decayed = viewModel(sets, nowMillis = 48L * 60L * 60L * 1000L)
        runCurrent()
        assertEquals(CHEST_24_48H, decayed.scoreOf(MuscleGroup.CHEST), 1e-9)
    }

    @Test
    fun updatesWhenNewSetsAreLogged() = runTest(dispatcher) {
        val sets = MutableStateFlow<List<LoggedSet>>(emptyList())
        val viewModel = FatigueHeatmapViewModel(
            workoutLogRepository = FlowingWorkoutLogRepository(sets),
            calculateMuscleFatigue = CalculateMuscleFatigueUseCase(),
            timeProvider = TimeProvider { 0L }
        )
        runCurrent()
        assertEquals(0.0, viewModel.scoreOf(MuscleGroup.CHEST), 1e-9)

        sets.value = List(24) { chestSet(timestampMillis = 0L) }
        runCurrent()

        assertEquals(CHEST_24_FRESH, viewModel.scoreOf(MuscleGroup.CHEST), 1e-9)
    }

    @Test
    fun updatesWhenSetsAreDeleted() = runTest(dispatcher) {
        val sets = MutableStateFlow(List(24) { chestSet(timestampMillis = 0L) })
        val viewModel = FatigueHeatmapViewModel(
            workoutLogRepository = FlowingWorkoutLogRepository(sets),
            calculateMuscleFatigue = CalculateMuscleFatigueUseCase(),
            timeProvider = TimeProvider { 0L }
        )
        runCurrent()
        assertEquals(CHEST_24_FRESH, viewModel.scoreOf(MuscleGroup.CHEST), 1e-9)

        sets.value = emptyList()
        runCurrent()

        assertEquals(0.0, viewModel.scoreOf(MuscleGroup.CHEST), 1e-9)
    }

    @Test
    fun resumeRefreshesWithoutRepositoryEmission() = runTest(dispatcher) {
        val clock = FakeClock()
        val viewModel = viewModel(List(24) { chestSet(0L) }, clock)
        try {
            runCurrent()
            assertEquals(CHEST_24_FRESH, viewModel.scoreOf(MuscleGroup.CHEST), 1e-9)

            clock.now = 48L * 60L * 60L * 1000L
            viewModel.onResume()
            runCurrent()
            assertEquals(CHEST_24_48H, viewModel.scoreOf(MuscleGroup.CHEST), 1e-9)

            viewModel.onPause()
            clock.now *= 2
            viewModel.onResume()
            runCurrent()
            assertEquals(CHEST_24_96H, viewModel.scoreOf(MuscleGroup.CHEST), 1e-9)
        } finally {
            viewModel.onPause()
        }
    }

    @Test
    fun tickRefreshesEveryMinuteWithoutRepositoryEmission() = runTest(dispatcher) {
        val clock = FakeClock()
        val viewModel = viewModel(List(24) { chestSet(0L) }, clock)
        try {
            runCurrent()
            viewModel.onResume()
            runCurrent()

            clock.now = 48L * 60L * 60L * 1000L
            advanceTimeBy(59_999L)
            runCurrent()
            assertEquals(CHEST_24_FRESH, viewModel.scoreOf(MuscleGroup.CHEST), 1e-9)
            advanceTimeBy(1L)
            runCurrent()
            assertEquals(CHEST_24_48H, viewModel.scoreOf(MuscleGroup.CHEST), 1e-9)

            clock.now *= 2
            advanceTimeBy(60_000L)
            runCurrent()
            assertEquals(CHEST_24_96H, viewModel.scoreOf(MuscleGroup.CHEST), 1e-9)
        } finally {
            viewModel.onPause()
        }
    }

    @Test
    fun tickerStopsWhenInactiveAndRestartsOnResume() = runTest(dispatcher) {
        val clock = FakeClock()
        val viewModel = viewModel(List(24) { chestSet(0L) }, clock)
        try {
            runCurrent()
            viewModel.onResume()
            runCurrent()
            advanceTimeBy(30_000L)
            viewModel.onPause()

            clock.now = 48L * 60L * 60L * 1000L
            val readsWhenPaused = clock.reads
            advanceTimeBy(180_000L)
            runCurrent()
            assertEquals(readsWhenPaused, clock.reads)
            assertEquals(CHEST_24_FRESH, viewModel.scoreOf(MuscleGroup.CHEST), 1e-9)

            viewModel.onResume()
            runCurrent()
            assertEquals(CHEST_24_48H, viewModel.scoreOf(MuscleGroup.CHEST), 1e-9)
            clock.now *= 2
            advanceTimeBy(60_000L)
            runCurrent()
            assertEquals(CHEST_24_96H, viewModel.scoreOf(MuscleGroup.CHEST), 1e-9)
        } finally {
            viewModel.onPause()
        }
    }

    @Test
    fun repeatedResumeDoesNotStartDuplicateTickers() = runTest(dispatcher) {
        val clock = FakeClock()
        val viewModel = viewModel(List(24) { chestSet(0L) }, clock)
        try {
            runCurrent()
            viewModel.onResume()
            runCurrent()
            viewModel.onResume()
            runCurrent()
            val readsBeforeTick = clock.reads

            advanceTimeBy(60_000L)
            runCurrent()

            assertEquals(readsBeforeTick + 1, clock.reads)
        } finally {
            viewModel.onPause()
        }
    }

    @Test
    fun percentageRoundsToOneDecimal() {
        assertEquals(0, roundedFatiguePercentage(0.0))
        assertEquals(123, roundedFatiguePercentage(0.1234))
        assertEquals(124, roundedFatiguePercentage(0.1235))
        assertEquals(124, roundedFatiguePercentage(0.1236))
        assertEquals(500, roundedFatiguePercentage(0.5))
        assertEquals(999, roundedFatiguePercentage(0.9994))
        assertEquals(Res.string.fatigue_percentage, fatiguePercentageResource(0))
        assertEquals(Res.string.fatigue_percentage, fatiguePercentageResource(999))
    }

    @Test
    fun percentageUsesNearLimitMarkerWhenItWouldRoundToOneHundred() {
        listOf(0.9995, 0.99999, 1.0).forEach { score ->
            val tenths = roundedFatiguePercentage(score)
            assertEquals(1000, tenths)
            assertEquals(
                Res.string.fatigue_percentage_near_limit,
                fatiguePercentageResource(tenths)
            )
        }
    }

    private fun FatigueHeatmapViewModel.scoreOf(muscle: MuscleGroup): Double =
        state.value.entries.first { it.muscle == muscle }.score

    private companion object {
        // Phase B derivation for 24 CHEST sets at one timestamp, involvement 1.0, reps 8:
        //   R = sqrt(8 / 8) = 1.0; batched stimulus = 24; delta = 6 * ln((6 + 24) / 6) = 6 * ln 5;
        //   F = 1 - exp(-delta / K) with K = 6, so F = 1 - 1/5 = 0.8.
        // CHEST half-life is 24 h, so 48 h (2 half-lives) gives 0.8 / 4 = 0.2 and 96 h gives 0.05.
        const val CHEST_24_FRESH = 0.8
        const val CHEST_24_48H = 0.2
        const val CHEST_24_96H = 0.05
    }

    private fun chestSet(timestampMillis: Long) = LoggedSet(
        timestampMillis = timestampMillis,
        targets = listOf(
            MuscleTarget(MuscleGroup.CHEST, MuscleInvolvement.PRIMARY.volumeWeight)
        )
    )

    private fun viewModel(sets: List<LoggedSet>, nowMillis: Long) = FatigueHeatmapViewModel(
        workoutLogRepository = FakeWorkoutLogRepository(sets),
        calculateMuscleFatigue = CalculateMuscleFatigueUseCase(),
        timeProvider = TimeProvider { nowMillis }
    )

    private fun viewModel(sets: List<LoggedSet>, clock: FakeClock) = FatigueHeatmapViewModel(
        workoutLogRepository = FakeWorkoutLogRepository(sets),
        calculateMuscleFatigue = CalculateMuscleFatigueUseCase(),
        timeProvider = clock
    )

    private class FakeClock : TimeProvider {
        var now = 0L
        var reads = 0

        override fun nowMillis(): Long {
            reads++
            return now
        }
    }

    private class FakeWorkoutLogRepository(private val sets: List<LoggedSet>) :
        WorkoutLogRepository {
        override suspend fun add(set: WorkoutSet) = Unit

        override suspend fun assignSession(setId: Long, sessionId: String) = Unit

        override suspend fun delete(id: Long) = Unit

        override suspend fun updateSetPerformedAt(setId: Long, performedAtMillis: Long) = Unit

        override suspend fun all(): List<WorkoutSet> = emptyList()

        override fun setsFlow(): Flow<List<WorkoutSet>> = flowOf(emptyList())

        override suspend fun loggedSets(): List<LoggedSet> = sets

        override fun loggedSetsFlow(): Flow<List<LoggedSet>> = flowOf(sets)

        override suspend fun clear() = Unit
    }

    private class FlowingWorkoutLogRepository(private val sets: MutableStateFlow<List<LoggedSet>>) :
        WorkoutLogRepository {
        override suspend fun add(set: WorkoutSet) = Unit

        override suspend fun assignSession(setId: Long, sessionId: String) = Unit

        override suspend fun delete(id: Long) = Unit

        override suspend fun updateSetPerformedAt(setId: Long, performedAtMillis: Long) = Unit

        override suspend fun all(): List<WorkoutSet> = emptyList()

        override fun setsFlow(): Flow<List<WorkoutSet>> = flowOf(emptyList())

        override suspend fun loggedSets(): List<LoggedSet> = sets.value

        override fun loggedSetsFlow(): Flow<List<LoggedSet>> = sets

        override suspend fun clear() = Unit
    }
}
