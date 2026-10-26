package com.hydrafit.app.feature.fatigueheatmap

import com.hydrafit.app.core.domain.fatigue.CalculateMuscleFatigueUseCase
import com.hydrafit.app.core.domain.fatigue.CoverageSet
import com.hydrafit.app.core.domain.fatigue.LoggedSet
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.fatigue.MuscleInvolvement
import com.hydrafit.app.core.domain.fatigue.MuscleTarget
import com.hydrafit.app.core.domain.fatigue.TrainingCoverageRepository
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.workout.PerformedClusterSegment
import com.hydrafit.app.core.domain.workout.WorkoutCluster
import com.hydrafit.app.core.domain.workout.WorkoutLogRepository
import com.hydrafit.app.core.domain.workout.WorkoutSet
import hydrafit.feature.fatigueheatmap.generated.resources.Res
import hydrafit.feature.fatigueheatmap.generated.resources.fatigue_percentage
import hydrafit.feature.fatigueheatmap.generated.resources.fatigue_percentage_near_limit
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

private typealias CoverageSetsFlow = MutableStateFlow<List<CoverageSet>>

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
        assertEquals(CHEST_24_FRESH, fresh.scoreOf(MuscleGroup.CHEST_UPPER), 1e-9)
        assertEquals(0.0, fresh.scoreOf(MuscleGroup.QUADS), 1e-9)

        val decayed = viewModel(sets, nowMillis = 48L * 60L * 60L * 1000L)
        runCurrent()
        assertEquals(CHEST_24_48H, decayed.scoreOf(MuscleGroup.CHEST_UPPER), 1e-9)
    }

    @Test
    fun updatesWhenNewSetsAreLogged() = runTest(dispatcher) {
        val sets = MutableStateFlow<List<LoggedSet>>(emptyList())
        val viewModel = FatigueHeatmapViewModel(
            workoutLogRepository = FlowingWorkoutLogRepository(sets),
            calculateMuscleFatigue = CalculateMuscleFatigueUseCase(),
            trainingCoverageRepository = FakeTrainingCoverageRepository(),
            timeProvider = TimeProvider { 0L },
            calculationDispatcher = dispatcher
        )
        runCurrent()
        assertEquals(0.0, viewModel.scoreOf(MuscleGroup.CHEST_UPPER), 1e-9)

        sets.value = List(24) { chestSet(timestampMillis = 0L) }
        runCurrent()

        assertEquals(CHEST_24_FRESH, viewModel.scoreOf(MuscleGroup.CHEST_UPPER), 1e-9)
    }

    @Test
    fun updatesWhenSetsAreDeleted() = runTest(dispatcher) {
        val sets = MutableStateFlow(List(24) { chestSet(timestampMillis = 0L) })
        val viewModel = FatigueHeatmapViewModel(
            workoutLogRepository = FlowingWorkoutLogRepository(sets),
            calculateMuscleFatigue = CalculateMuscleFatigueUseCase(),
            trainingCoverageRepository = FakeTrainingCoverageRepository(),
            timeProvider = TimeProvider { 0L },
            calculationDispatcher = dispatcher
        )
        runCurrent()
        assertEquals(CHEST_24_FRESH, viewModel.scoreOf(MuscleGroup.CHEST_UPPER), 1e-9)

        sets.value = emptyList()
        runCurrent()

        assertEquals(0.0, viewModel.scoreOf(MuscleGroup.CHEST_UPPER), 1e-9)
    }

    @Test
    fun resumeRefreshesWithoutRepositoryEmission() = runTest(dispatcher) {
        val clock = FakeClock()
        val viewModel = viewModel(List(24) { chestSet(0L) }, clock)
        try {
            runCurrent()
            assertEquals(CHEST_24_FRESH, viewModel.scoreOf(MuscleGroup.CHEST_UPPER), 1e-9)

            clock.now = 48L * 60L * 60L * 1000L
            viewModel.onResume()
            runCurrent()
            assertEquals(CHEST_24_48H, viewModel.scoreOf(MuscleGroup.CHEST_UPPER), 1e-9)

            viewModel.onPause()
            clock.now *= 2
            viewModel.onResume()
            runCurrent()
            assertEquals(CHEST_24_96H, viewModel.scoreOf(MuscleGroup.CHEST_UPPER), 1e-9)
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
            assertEquals(CHEST_24_FRESH, viewModel.scoreOf(MuscleGroup.CHEST_UPPER), 1e-9)
            advanceTimeBy(1L)
            runCurrent()
            assertEquals(CHEST_24_48H, viewModel.scoreOf(MuscleGroup.CHEST_UPPER), 1e-9)

            clock.now *= 2
            advanceTimeBy(60_000L)
            runCurrent()
            assertEquals(CHEST_24_96H, viewModel.scoreOf(MuscleGroup.CHEST_UPPER), 1e-9)
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
            assertEquals(CHEST_24_FRESH, viewModel.scoreOf(MuscleGroup.CHEST_UPPER), 1e-9)

            viewModel.onResume()
            runCurrent()
            assertEquals(CHEST_24_48H, viewModel.scoreOf(MuscleGroup.CHEST_UPPER), 1e-9)
            clock.now *= 2
            advanceTimeBy(60_000L)
            runCurrent()
            assertEquals(CHEST_24_96H, viewModel.scoreOf(MuscleGroup.CHEST_UPPER), 1e-9)
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
    fun calculatesFatigueOnTheInjectedDispatcherNotTheCaller() = runTest(dispatcher) {
        val calculationDispatcher = RecordingDispatcher(dispatcher)
        val viewModel = FatigueHeatmapViewModel(
            workoutLogRepository = FakeWorkoutLogRepository(List(24) { chestSet(0L) }),
            calculateMuscleFatigue = CalculateMuscleFatigueUseCase(),
            trainingCoverageRepository = FakeTrainingCoverageRepository(),
            timeProvider = TimeProvider { 0L },
            calculationDispatcher = calculationDispatcher
        )

        runCurrent()

        assertTrue(calculationDispatcher.used)
        assertEquals(CHEST_24_FRESH, viewModel.scoreOf(MuscleGroup.CHEST_UPPER), 1e-9)
    }

    @Test
    fun newerRefreshIsNotOverwrittenByAnOlderInFlightRefresh() = runTest(dispatcher) {
        val clock = FakeClock()
        val heldDispatcher = HeldFirstDispatcher(dispatcher)
        val viewModel = FatigueHeatmapViewModel(
            workoutLogRepository = FlowingWorkoutLogRepository(
                MutableStateFlow(List(24) { chestSet(timestampMillis = 0L) })
            ),
            calculateMuscleFatigue = CalculateMuscleFatigueUseCase(),
            trainingCoverageRepository = object : TrainingCoverageRepository {
                override fun observeSets(): Flow<List<CoverageSet>> = emptyFlow()
            },
            timeProvider = clock,
            calculationDispatcher = heldDispatcher
        )
        try {
            // The first refresh captures the 0 h clock and is then held inside its calculation.
            runCurrent()

            // A later refresh captures the 48 h clock and completes first, committing newer state.
            clock.now = 48L * 60L * 60L * 1000L
            viewModel.onResume()
            runCurrent()
            assertEquals(CHEST_24_48H, viewModel.scoreOf(MuscleGroup.CHEST_UPPER), 1e-9)

            // The held refresh now finishes. Its generation is stale, so it must not overwrite the
            // newer state (without the guard it would commit the 0 h score of 0.8 over 0.2).
            heldDispatcher.releaseHeld()
            runCurrent()
            assertEquals(CHEST_24_48H, viewModel.scoreOf(MuscleGroup.CHEST_UPPER), 1e-9)
        } finally {
            viewModel.onPause()
        }
    }

    @Test
    fun coverageCountsWorkingSetsAndCreditsSeparately() = runTest(dispatcher) {
        val pairs = coverageViewModel(
            sets = MutableStateFlow(
                listOf(
                    CoverageSet(NOW, isWarmup = false, involvements = PROFILE),
                    CoverageSet(NOW, isWarmup = false, involvements = PROFILE),
                    CoverageSet(NOW, isWarmup = true, involvements = PROFILE)
                )
            ),
            timeProvider = TimeProvider { NOW }
        )
        runCurrent()

        val biceps = pairs.coverageOf(MuscleGroup.BICEPS)
        val forearms = pairs.coverageOf(MuscleGroup.FOREARMS)
        assertEquals(2, biceps.workingSets)
        assertEquals(2, forearms.workingSets)
        assertEquals(2.0, biceps.estimatedInvolvementCredits, 1e-12)
        assertEquals(0.6, forearms.estimatedInvolvementCredits, 1e-12)
        assertTrue(pairs.state.value.coverageComputed)
    }

    @Test
    fun coverageWarmUpSetAddsNeitherCountNorCredits() = runTest(dispatcher) {
        val viewModel = coverageViewModel(
            sets = MutableStateFlow(
                listOf(CoverageSet(NOW, isWarmup = true, involvements = PROFILE))
            ),
            timeProvider = TimeProvider { NOW }
        )
        runCurrent()

        val biceps = viewModel.coverageOf(MuscleGroup.BICEPS)
        assertEquals(0, biceps.workingSets)
        assertEquals(0.0, biceps.estimatedInvolvementCredits, 1e-12)
    }

    @Test
    fun deletingOneWorkingSetHalvesCountAndCredits() = runTest(dispatcher) {
        val sets = MutableStateFlow(
            listOf(
                CoverageSet(NOW, isWarmup = false, involvements = PROFILE),
                CoverageSet(NOW, isWarmup = false, involvements = PROFILE)
            )
        )
        val viewModel = coverageViewModel(sets = sets, timeProvider = TimeProvider { NOW })
        runCurrent()
        assertEquals(2, viewModel.coverageOf(MuscleGroup.BICEPS).workingSets)

        sets.value = listOf(CoverageSet(NOW, isWarmup = false, involvements = PROFILE))
        runCurrent()

        val biceps = viewModel.coverageOf(MuscleGroup.BICEPS)
        val forearms = viewModel.coverageOf(MuscleGroup.FOREARMS)
        assertEquals(1, biceps.workingSets)
        assertEquals(1.0, biceps.estimatedInvolvementCredits, 1e-12)
        assertEquals(1, forearms.workingSets)
        assertEquals(0.3, forearms.estimatedInvolvementCredits, 1e-12)
    }

    @Test
    fun coverageTickRollsTheWindowOverWhileLastTrainedPersists() = runTest(dispatcher) {
        val clock = FakeClock()
        val sets = MutableStateFlow(
            listOf(CoverageSet(performedAtMillis = 0L, isWarmup = false, involvements = PROFILE))
        )
        val viewModel = coverageViewModel(sets = sets, timeProvider = clock)
        try {
            clock.now = 6L * DAY
            runCurrent()
            viewModel.onResume()
            runCurrent()

            assertEquals(1, viewModel.coverageOf(MuscleGroup.BICEPS).workingSets)
            assertEquals(0L, viewModel.coverageOf(MuscleGroup.BICEPS).lastTrainedAtMillis)

            clock.now = 7L * DAY
            advanceTimeBy(60_000L)
            runCurrent()

            val afterRollover = viewModel.coverageOf(MuscleGroup.BICEPS)
            assertEquals(0, afterRollover.workingSets)
            assertEquals(0L, afterRollover.lastTrainedAtMillis)
        } finally {
            viewModel.onPause()
        }
    }

    @Test
    fun coverageExposesUnclassifiedSnapshots() = runTest(dispatcher) {
        val viewModel = coverageViewModel(
            sets = MutableStateFlow(
                listOf(
                    CoverageSet(
                        performedAtMillis = 5L * DAY,
                        isWarmup = false,
                        involvements = null
                    )
                )
            ),
            timeProvider = TimeProvider { 10L * DAY }
        )
        runCurrent()

        assertTrue(viewModel.state.value.hasUnclassifiedHistory)
        assertEquals(1, viewModel.state.value.unclassifiedSetsInWindow)
        val biceps = viewModel.coverageOf(MuscleGroup.BICEPS)
        assertEquals(0, biceps.workingSets)
        assertNull(biceps.lastTrainedAtMillis)
    }

    @Test
    fun coverageIsNotClaimedBeforeTheFirstSnapshot() = runTest(dispatcher) {
        val viewModel = FatigueHeatmapViewModel(
            workoutLogRepository = FakeWorkoutLogRepository(emptyList()),
            calculateMuscleFatigue = CalculateMuscleFatigueUseCase(),
            trainingCoverageRepository = object : TrainingCoverageRepository {
                override fun observeSets(): Flow<List<CoverageSet>> = emptyFlow()
            },
            timeProvider = TimeProvider { NOW },
            calculationDispatcher = dispatcher
        )
        runCurrent()

        assertFalse(viewModel.state.value.coverageComputed)
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

    @Test
    fun creditPartsSplitWholeAndTenths() {
        assertEquals(0 to 6, creditParts(0.6))
        assertEquals(2 to 0, creditParts(2.0))
        assertEquals(0 to 0, creditParts(0.0))
        assertEquals(1 to 2, creditParts(1.234))
    }

    @Test
    fun coverageDateLabelKeepsOnlyTheCivilDate() {
        assertEquals("1970-01-01", coverageDateLabel(0L, 0L))
        assertEquals("1970-01-01", coverageDateLabel(60_000L, 0L))
    }

    private fun FatigueHeatmapViewModel.scoreOf(muscle: MuscleGroup): Double =
        state.value.entries.first { it.muscle == muscle }.score

    private fun FatigueHeatmapViewModel.coverageOf(muscle: MuscleGroup): MuscleFatigueEntry =
        state.value.entries.first { it.muscle == muscle }

    private companion object {
        // Phase B derivation for 24 CHEST sets at one timestamp, involvement 1.0, reps 8:
        //   R = sqrt(8 / 8) = 1.0; batched stimulus = 24; delta = 6 * ln((6 + 24) / 6) = 6 * ln 5;
        //   F = 1 - exp(-delta / K) with K = 6, so F = 1 - 1/5 = 0.8.
        // CHEST half-life is 24 h, so 48 h (2 half-lives) gives 0.8 / 4 = 0.2 and 96 h gives 0.05.
        const val CHEST_24_FRESH = 0.8
        const val CHEST_24_48H = 0.2
        const val CHEST_24_96H = 0.05

        const val DAY = 86_400_000L
        const val NOW = 10L * DAY
        val PROFILE = mapOf(MuscleGroup.BICEPS to 1.0, MuscleGroup.FOREARMS to 0.3)
    }

    private fun chestSet(timestampMillis: Long) = LoggedSet(
        timestampMillis = timestampMillis,
        targets = listOf(
            MuscleTarget(MuscleGroup.CHEST_UPPER, MuscleInvolvement.PRIMARY.volumeWeight)
        )
    )

    private fun viewModel(sets: List<LoggedSet>, nowMillis: Long) = FatigueHeatmapViewModel(
        workoutLogRepository = FakeWorkoutLogRepository(sets),
        calculateMuscleFatigue = CalculateMuscleFatigueUseCase(),
        trainingCoverageRepository = FakeTrainingCoverageRepository(),
        timeProvider = TimeProvider { nowMillis },
        calculationDispatcher = dispatcher
    )

    private fun viewModel(sets: List<LoggedSet>, clock: FakeClock) = FatigueHeatmapViewModel(
        workoutLogRepository = FakeWorkoutLogRepository(sets),
        calculateMuscleFatigue = CalculateMuscleFatigueUseCase(),
        trainingCoverageRepository = FakeTrainingCoverageRepository(),
        timeProvider = clock,
        calculationDispatcher = dispatcher
    )

    private fun coverageViewModel(sets: CoverageSetsFlow, timeProvider: TimeProvider) =
        FatigueHeatmapViewModel(
            workoutLogRepository = FakeWorkoutLogRepository(emptyList()),
            calculateMuscleFatigue = CalculateMuscleFatigueUseCase(),
            trainingCoverageRepository = FlowingTrainingCoverageRepository(sets),
            timeProvider = timeProvider,
            calculationDispatcher = dispatcher
        )

    private class RecordingDispatcher(private val delegate: CoroutineDispatcher) :
        CoroutineDispatcher() {
        var used = false
            private set

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            used = true
            delegate.dispatch(context, block)
        }

        override fun isDispatchNeeded(context: CoroutineContext): Boolean =
            delegate.isDispatchNeeded(context)
    }

    /**
     * Test dispatcher that withholds the first block dispatched to it until [releaseHeld] forwards it
     * to [delegate]. Everything dispatched after that runs normally, so a test can keep an older
     * refresh suspended inside its calculation while a newer refresh starts, completes and commits,
     * then resume the older one deterministically.
     */
    private class HeldFirstDispatcher(private val delegate: CoroutineDispatcher) :
        CoroutineDispatcher() {
        private var holdNext = true
        private var held: Runnable? = null

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            if (holdNext) {
                holdNext = false
                held = block
            } else {
                delegate.dispatch(context, block)
            }
        }

        fun releaseHeld() {
            val block = held ?: error("no held block to release")
            held = null
            delegate.dispatch(EmptyCoroutineContext, block)
        }

        override fun isDispatchNeeded(context: CoroutineContext): Boolean =
            delegate.isDispatchNeeded(context)
    }

    private class FakeClock : TimeProvider {
        var now = 0L
        var reads = 0

        override fun nowMillis(): Long {
            reads++
            return now
        }
    }

    private class FakeTrainingCoverageRepository(
        private val sets: List<CoverageSet> = emptyList()
    ) : TrainingCoverageRepository {
        override fun observeSets(): Flow<List<CoverageSet>> = flowOf(sets)
    }

    private class FlowingTrainingCoverageRepository(
        private val sets: MutableStateFlow<List<CoverageSet>>
    ) : TrainingCoverageRepository {
        override fun observeSets(): Flow<List<CoverageSet>> = sets
    }

    private class FakeWorkoutLogRepository(private val sets: List<LoggedSet>) :
        WorkoutLogRepository {
        override suspend fun add(set: WorkoutSet) = Unit

        override suspend fun addCluster(set: WorkoutSet, cluster: WorkoutCluster) = Unit

        override suspend fun appendClusterSegments(
            setId: Long,
            segments: List<PerformedClusterSegment>
        ) = Unit

        override suspend fun assignSession(setId: Long, sessionId: String) = Unit

        override suspend fun delete(id: Long) = Unit

        override suspend fun updateSetPerformedAt(setId: Long, performedAtMillis: Long) = Unit

        override suspend fun all(): List<WorkoutSet> = emptyList()

        override suspend fun lastSetBySession(sessionId: String): WorkoutSet? = null

        override suspend fun setsForOccurrence(occurrenceId: Long): List<WorkoutSet> = emptyList()

        override fun setsFlow(): Flow<List<WorkoutSet>> = flowOf(emptyList())

        override suspend fun loggedSets(): List<LoggedSet> = sets

        override fun loggedSetsFlow(): Flow<List<LoggedSet>> = flowOf(sets)

        override suspend fun clear() = Unit
    }

    private class FlowingWorkoutLogRepository(private val sets: MutableStateFlow<List<LoggedSet>>) :
        WorkoutLogRepository {
        override suspend fun add(set: WorkoutSet) = Unit

        override suspend fun addCluster(set: WorkoutSet, cluster: WorkoutCluster) = Unit

        override suspend fun appendClusterSegments(
            setId: Long,
            segments: List<PerformedClusterSegment>
        ) = Unit

        override suspend fun assignSession(setId: Long, sessionId: String) = Unit

        override suspend fun delete(id: Long) = Unit

        override suspend fun updateSetPerformedAt(setId: Long, performedAtMillis: Long) = Unit

        override suspend fun all(): List<WorkoutSet> = emptyList()

        override suspend fun lastSetBySession(sessionId: String): WorkoutSet? = null

        override suspend fun setsForOccurrence(occurrenceId: Long): List<WorkoutSet> = emptyList()

        override fun setsFlow(): Flow<List<WorkoutSet>> = flowOf(emptyList())

        override suspend fun loggedSets(): List<LoggedSet> = sets.value

        override fun loggedSetsFlow(): Flow<List<LoggedSet>> = sets

        override suspend fun clear() = Unit
    }
}
