package com.hydrafit.app.core.domain.workout

import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.routine.ClusterKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

class BodyweightPullEvidenceTest {

    private fun chin(reps: Int, added: Double? = null, id: String = "chin-up") = PullSetSample(
        id,
        reps,
        added,
        if (added == null) LoadKind.BODYWEIGHT else LoadKind.ADDED
    )

    private fun pulldown(reps: Int, kg: Double, id: String = "lat-pulldown") =
        PullSetSample(id, reps, kg, LoadKind.EXTERNAL)

    @Test
    fun comparesTheBestEstimateOnEachSideUsingBodyWeightAsTheChinUpLoad() {
        val evidence = assertNotNull(
            BodyweightPullComparison.compare(
                bodyWeightKg = 90.0,
                bodyweightSets = listOf(chin(8), chin(6)),
                externalSets = listOf(pulldown(8, 70.0), pulldown(10, 60.0))
            )
        )

        // 90 x (1 + 8/30) and 70 x (1 + 8/30).
        assertEquals(114.0, evidence.bodyweightPullOneRepMaxKg, 1e-9)
        assertEquals(88.6667, evidence.externalPullOneRepMaxKg, 1e-3)
        assertEquals(88.6667 / 114.0, evidence.ratio, 1e-4)
        assertEquals(2, evidence.bodyweightPullSetCount)
        assertEquals(2, evidence.externalPullSetCount)
    }

    @Test
    fun addedLoadRaisesTheBodyweightSideByTheAddedKilograms() {
        val evidence = assertNotNull(
            BodyweightPullComparison.compare(
                bodyWeightKg = 80.0,
                bodyweightSets = listOf(chin(5, added = 10.0), chin(5, added = 10.0)),
                externalSets = listOf(pulldown(8, 60.0), pulldown(8, 60.0))
            )
        )

        assertEquals(90.0 * (1.0 + 5 / 30.0), evidence.bodyweightPullOneRepMaxKg, 1e-9)
    }

    @Test
    fun givesNothingWithoutABodyWeightOrEnoughSetsOnBothSides() {
        val chins = listOf(chin(8), chin(6))
        val pulls = listOf(pulldown(8, 70.0), pulldown(10, 60.0))

        assertNull(BodyweightPullComparison.compare(null, chins, pulls))
        assertNull(BodyweightPullComparison.compare(0.0, chins, pulls))
        assertNull(BodyweightPullComparison.compare(Double.NaN, chins, pulls))
        assertNull(BodyweightPullComparison.compare(90.0, chins.take(1), pulls))
        assertNull(BodyweightPullComparison.compare(90.0, chins, pulls.take(1)))
        assertNull(BodyweightPullComparison.compare(90.0, emptyList(), pulls))
    }

    @Test
    fun ignoresSetsThatCannotGiveAnEstimate() {
        val evidence = BodyweightPullComparison.compare(
            bodyWeightKg = 90.0,
            bodyweightSets = listOf(
                chin(8),
                chin(BodyweightPullComparison.MAX_REPS + 1),
                chin(0),
                PullSetSample("chin-up", 8, 5.0, LoadKind.EXTERNAL),
                PullSetSample("chin-up", 8, null, LoadKind.LEGACY_UNSPECIFIED)
            ),
            externalSets = listOf(
                pulldown(8, 70.0),
                pulldown(8, 0.0),
                pulldown(8, -5.0),
                PullSetSample("lat-pulldown", 8, 70.0, LoadKind.ADDED)
            )
        )

        // One usable set each, below the two-set minimum.
        assertNull(evidence)
    }

    @Test
    fun aVeryStrongLifterIsReportedAsTheyAreNotAveraged() {
        val evidence = assertNotNull(
            BodyweightPullComparison.compare(
                bodyWeightKg = 70.0,
                bodyweightSets = listOf(chin(15, added = 40.0), chin(12, added = 40.0)),
                externalSets = listOf(pulldown(10, 70.0), pulldown(10, 70.0))
            )
        )

        assertTrue(evidence.bodyweightPullOneRepMaxKg > 160.0)
        assertTrue(evidence.ratio < 0.6)
    }

    @Test
    fun theUseCaseReadsOnlyRecentWorkingVerticalPullSetsOfBothKinds() = runTest {
        val now = 100L * DAY
        val recent = now - 10 * DAY
        val old = now - 60 * DAY
        val history = mapOf(
            "chin-up" to listOf(
                set(1, "chin-up", 8, null, LoadKind.BODYWEIGHT, recent),
                set(2, "chin-up", 6, null, LoadKind.BODYWEIGHT, recent),
                set(3, "chin-up", 12, null, LoadKind.BODYWEIGHT, old),
                set(4, "chin-up", 3, null, LoadKind.BODYWEIGHT, recent, warmup = true)
            ),
            "lat-pulldown" to listOf(
                set(5, "lat-pulldown", 8, 70.0, LoadKind.EXTERNAL, recent),
                set(6, "lat-pulldown", 9, 65.0, LoadKind.EXTERNAL, recent)
            ),
            // Another pattern must never leak into the comparison.
            "barbell-row" to listOf(
                set(7, "barbell-row", 8, 100.0, LoadKind.EXTERNAL, recent),
                set(8, "barbell-row", 8, 100.0, LoadKind.EXTERNAL, recent)
            )
        )
        val useCase = BuildBodyweightPullEvidenceUseCase(catalog, FakeProgress(history))

        val evidence = assertNotNull(useCase(bodyWeightKg = 90.0, nowMillis = now))

        assertEquals("chin-up", evidence.bodyweightPullExerciseId)
        assertEquals("lat-pulldown", evidence.externalPullExerciseId)
        assertEquals(2, evidence.bodyweightPullSetCount)
        assertEquals(2, evidence.externalPullSetCount)
        assertNull(useCase(bodyWeightKg = null, nowMillis = now))
    }

    @Test
    fun theUseCaseCountsLegacyExternalSetsSkipsClustersAndKeepsTheWindowEdges() = runTest {
        val now = 100L * DAY
        val cutoff = now - 42 * DAY
        val cluster = WorkoutClusterSummary(ClusterKind.entries.first(), 3, 3)
        val history = mapOf(
            "chin-up" to listOf(
                set(1, "chin-up", 8, null, LoadKind.BODYWEIGHT, now),
                set(2, "chin-up", 6, 10.0, LoadKind.ADDED, cutoff)
            ),
            "lat-pulldown" to listOf(
                set(3, "lat-pulldown", 8, 70.0, LoadKind.LEGACY_UNSPECIFIED, now - DAY),
                set(4, "lat-pulldown", 8, 70.0, LoadKind.EXTERNAL, now - DAY, cluster = cluster),
                set(5, "lat-pulldown", 8, 99.0, LoadKind.EXTERNAL, now + 1),
                set(6, "lat-pulldown", 8, 99.0, LoadKind.EXTERNAL, cutoff - 1),
                set(7, "lat-pulldown", 10, 60.0, LoadKind.EXTERNAL, cutoff + 1)
            )
        )

        val evidence = assertNotNull(
            BuildBodyweightPullEvidenceUseCase(catalog, FakeProgress(history))(90.0, now)
        )

        // Chin-ups at exactly now and exactly the cutoff both count; the 99 kg sets outside the
        // window and the cluster set do not, so the best pulldown is the legacy 8 x 70 kg set.
        assertEquals(2, evidence.bodyweightPullSetCount)
        assertEquals(2, evidence.externalPullSetCount)
        assertEquals(70.0 * (1.0 + 8 / 30.0), evidence.externalPullOneRepMaxKg, 1e-9)
        // The added-load set at the cutoff (90 + 10 kg for 6 reps) beats the plain 8 reps.
        assertEquals(100.0 * (1.0 + 6 / 30.0), evidence.bodyweightPullOneRepMaxKg, 1e-9)
    }

    @Test
    fun theUseCaseReadsPastTheFirstPageOfAHeavyHistory() = runTest {
        val now = 100L * DAY
        // 120 recent pulldown sets, newest first: the best one is the oldest in-window set, which a
        // single 100-set page would never reach.
        val pulldowns = (1..120).map { index ->
            val heavy = index == 120
            set(
                id = index.toLong(),
                exerciseId = "lat-pulldown",
                reps = 8,
                weightKg = if (heavy) 100.0 else 50.0,
                loadKind = LoadKind.EXTERNAL,
                at = now - index * 1_000L
            )
        }
        val history = mapOf(
            "chin-up" to listOf(
                set(500, "chin-up", 8, null, LoadKind.BODYWEIGHT, now - DAY),
                set(501, "chin-up", 8, null, LoadKind.BODYWEIGHT, now - DAY)
            ),
            "lat-pulldown" to pulldowns
        )

        val evidence = assertNotNull(
            BuildBodyweightPullEvidenceUseCase(catalog, FakeProgress(history))(90.0, now)
        )

        assertEquals(100.0 * (1.0 + 8 / 30.0), evidence.externalPullOneRepMaxKg, 1e-9)
        assertEquals(120, evidence.externalPullSetCount)
    }

    private fun set(
        id: Long,
        exerciseId: String,
        reps: Int,
        weightKg: Double?,
        loadKind: LoadKind,
        at: Long,
        warmup: Boolean = false,
        cluster: WorkoutClusterSummary? = null
    ) = WorkoutHistorySet(
        id = id,
        exerciseId = exerciseId,
        exerciseName = null,
        reps = reps,
        weightKg = weightKg,
        loadKind = loadKind,
        isWarmup = warmup,
        rir = null,
        performedAtMillis = at,
        timingProvenance = WorkoutTimingProvenance.UNKNOWN,
        cluster = cluster
    )

    private val catalog = object : ExerciseCatalog {
        override suspend fun all(): List<Exercise> = listOf(
            exercise(
                "chin-up",
                MovementPattern.VERTICAL_PULL,
                ExerciseLoadCapability.BODYWEIGHT_ADDABLE
            ),
            exercise(
                "lat-pulldown",
                MovementPattern.VERTICAL_PULL,
                ExerciseLoadCapability.EXTERNAL
            ),
            exercise(
                "barbell-row",
                MovementPattern.HORIZONTAL_PULL,
                ExerciseLoadCapability.EXTERNAL
            )
        )
    }

    private fun exercise(id: String, pattern: MovementPattern, capability: ExerciseLoadCapability) =
        Exercise(
            id = id,
            name = id,
            requiredEquipment = emptySet(),
            primaryMuscles = setOf(MuscleGroup.LATS),
            movementPattern = pattern,
            loadCapability = capability
        )

    private class FakeProgress(private val history: Map<String, List<WorkoutHistorySet>>) :
        ExerciseProgressRepository {
        override fun observeHistory(request: ExerciseHistoryRequest): Flow<ExerciseHistoryPage> {
            // Newest first with the real (performedAt, id) cursor, like the database implementation.
            val ordered = history[request.exerciseId].orEmpty()
                .sortedWith(
                    compareByDescending<WorkoutHistorySet> {
                        it.performedAtMillis
                    }.thenByDescending { it.id }
                )
                .filter { set ->
                    request.cursor == null ||
                        set.performedAtMillis < request.cursor.performedAtMillis ||
                        (
                            set.performedAtMillis == request.cursor.performedAtMillis &&
                                set.id < request.cursor.setId
                            )
                }
            return flowOf(
                ExerciseHistoryPage(
                    ordered.take(request.pageSize),
                    hasMore = ordered.size > request.pageSize
                )
            )
        }

        override fun observeProgress(request: ExerciseProgressRequest) = error("unused")

        override fun observeStationOptions(exerciseId: String) = error("unused")

        override fun observeOccurrenceExerciseIds(occurrenceId: Long) = error("unused")

        override fun observeSet(exerciseId: String, setId: Long) = error("unused")
    }

    private companion object {
        const val DAY = 86_400_000L
    }
}
