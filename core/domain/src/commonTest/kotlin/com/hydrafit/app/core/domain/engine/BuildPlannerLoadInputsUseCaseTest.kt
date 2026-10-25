package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.WorkoutPrescriptionSnapshot
import com.hydrafit.app.core.domain.workout.WorkoutSession
import com.hydrafit.app.core.domain.workout.WorkoutSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * The load half of a plan request: only external resistance (or a legacy number on an exercise that
 * is external today) seeds the baseline. Progression reads each exercise's recorded comparable
 * targets, so a legacy or missing recorded target seeds the baseline but never fabricates a streak.
 */
class BuildPlannerLoadInputsUseCaseTest {

    private val useCase = BuildPlannerLoadInputsUseCase(
        catalog = FakeCatalog(
            listOf(
                exercise("bench-press", ExerciseLoadCapability.EXTERNAL),
                exercise("ab-roll", ExerciseLoadCapability.BODYWEIGHT_ONLY)
            )
        ),
        buildRecentWeights = BuildRecentWeightsUseCase(),
        progressWeights = ProgressWeightsUseCase()
    )

    @Test
    fun legacyExternalSetsSeedTheBaseline() = runTest {
        val result = useCase(
            sources = sources(
                listOf(
                    set("bench-press", day = 99, kind = LoadKind.LEGACY_UNSPECIFIED),
                    set("bench-press", day = 100, kind = LoadKind.LEGACY_UNSPECIFIED)
                )
            ),
            pauseIncrements = false,
            utcOffsetMillis = 0L,
            nowMillis = NOW_MILLIS
        )

        // Epley(100kg x 8) = 126.666...
        assertEquals(126.66666666666667, result.suggestedWeightsKg.getValue("bench-press"), 1e-9)
    }

    @Test
    fun aLegacyPrescriptionSeedsTheBaselineButDoesNotEarnAStreak() = runTest {
        val completed = completedExternalSessions(
            "bench-press",
            target(kind = LoadKind.LEGACY_UNSPECIFIED)
        )
        val result = useCase(
            sources = sources(completed),
            pauseIncrements = false,
            utcOffsetMillis = 0L,
            nowMillis = NOW_MILLIS
        )

        // Three completed external sessions, but the recorded target is legacy: no increment.
        assertEquals(126.66666666666667, result.suggestedWeightsKg.getValue("bench-press"), 1e-9)
    }

    @Test
    fun aTypedExternalPrescriptionEarnsAStreak() = runTest {
        val completed = completedExternalSessions("bench-press", target())
        val result = useCase(
            sources = sources(completed),
            pauseIncrements = false,
            utcOffsetMillis = 0L,
            nowMillis = NOW_MILLIS
        )

        assertEquals(129.16666666666667, result.suggestedWeightsKg.getValue("bench-press"), 1e-9)
    }

    @Test
    fun legacyLoadOnABodyweightOnlyExerciseIsExcluded() = runTest {
        val result = useCase(
            sources = sources(listOf(set("ab-roll", day = 1, weightKg = 32.5))),
            pauseIncrements = false,
            utcOffsetMillis = 0L,
            nowMillis = NOW_MILLIS
        )

        assertTrue(result.suggestedWeightsKg.isEmpty())
    }

    @Test
    fun aStaleMaximumIsWithheldWhenRecentEvidenceIsInsufficient() = runTest {
        val sets = listOf(
            set("bench-press", day = 1, weightKg = 120.0, kind = LoadKind.EXTERNAL),
            set("bench-press", day = 90, weightKg = 100.0, kind = LoadKind.EXTERNAL)
        )

        val result = useCase(
            sources = sources(sets),
            pauseIncrements = false,
            utcOffsetMillis = 0L,
            nowMillis = NOW_MILLIS
        )

        // Only one set falls inside the window, so neither the stale history nor that single set
        // produces an automatic numeric suggestion.
        assertTrue("bench-press" in result.withheldWeightExerciseIds)
        assertTrue("bench-press" !in result.suggestedWeightsKg)
    }

    @Test
    fun twoRecentSessionsVoteEquallyAndBoundHistoryAndProgressionConsistently() = runTest {
        val sets = listOf(
            set("bench-press", day = 1, weightKg = 120.0, kind = LoadKind.EXTERNAL),
            set("bench-press", day = 90, weightKg = 120.0, kind = LoadKind.EXTERNAL),
            set("bench-press", day = 92, weightKg = 100.0, kind = LoadKind.EXTERNAL)
        )

        val result = useCase(
            sources = sources(sets),
            pauseIncrements = false,
            utcOffsetMillis = 0L,
            nowMillis = NOW_MILLIS
        )

        // Two sessions vote: Epley(120 x 8) = 152 and Epley(100 x 8) = 126.667. With an even count
        // the baseline averages the two middle votes, so neither session alone sets the bound.
        assertEquals(139.33333333333334, result.suggestedWeightsKg.getValue("bench-press"), 1e-9)
        assertEquals(result.suggestedWeightsKg, result.recentWeightCaps)
    }

    @Test
    fun oneLooseHighSetNoLongerOutranksSeveralCleanerSessions() = runTest {
        val sets = listOf(90L, 91L, 92L, 93L).map { day ->
            set("bench-press", day = day, weightKg = 100.0, kind = LoadKind.EXTERNAL)
        } + set("bench-press", day = 94, weightKg = 140.0, kind = LoadKind.EXTERNAL)

        val result = useCase(
            sources = sources(sets),
            pauseIncrements = false,
            utcOffsetMillis = 0L,
            nowMillis = NOW_MILLIS
        )

        // Four sessions estimate 126.667 and one loose session estimates 177.333. The old per-set
        // maximum returned 177.333; the session median returns the ordinary sessions' value.
        assertEquals(126.66666666666667, result.suggestedWeightsKg.getValue("bench-press"), 1e-9)
        assertEquals(
            LoadBaselineSource.RECENT_PERFORMANCE,
            result.loadExplanations.getValue("bench-press").baseline.source
        )
    }

    @Test
    fun anAscendingSessionsBestSetCastsExactlyOneVote() = runTest {
        val sets = listOf(
            set(
                "bench-press",
                day = 90,
                weightKg = 100.0,
                kind = LoadKind.EXTERNAL,
                sessionId = "s1"
            ),
            set(
                "bench-press",
                day = 90,
                weightKg = 110.0,
                kind = LoadKind.EXTERNAL,
                sessionId = "s1"
            ),
            set(
                "bench-press",
                day = 90,
                weightKg = 120.0,
                kind = LoadKind.EXTERNAL,
                sessionId = "s1"
            ),
            set(
                "bench-press",
                day = 92,
                weightKg = 100.0,
                kind = LoadKind.EXTERNAL,
                sessionId = "s2"
            )
        )

        val result = useCase(
            sources = sources(sets),
            pauseIncrements = false,
            utcOffsetMillis = 0L,
            nowMillis = NOW_MILLIS
        )

        // Session s1 votes once with its best set, Epley(120 x 8) = 152; session s2 votes 126.667.
        // The median of two votes is 139.333. A per-set median would have returned 133.0 from the
        // four separate estimates, and a per-set maximum 152.
        assertEquals(139.33333333333334, result.suggestedWeightsKg.getValue("bench-press"), 1e-9)
    }

    @Test
    fun anEvenSessionCountAveragesTheTwoMiddleVotes() = runTest {
        val sets = listOf(
            set("bench-press", day = 88, weightKg = 90.0, kind = LoadKind.EXTERNAL),
            set("bench-press", day = 90, weightKg = 100.0, kind = LoadKind.EXTERNAL),
            set("bench-press", day = 92, weightKg = 110.0, kind = LoadKind.EXTERNAL),
            set("bench-press", day = 94, weightKg = 120.0, kind = LoadKind.EXTERNAL)
        )

        val result = useCase(
            sources = sources(sets),
            pauseIncrements = false,
            utcOffsetMillis = 0L,
            nowMillis = NOW_MILLIS
        )

        // Votes sorted ascending: 114, 126.667, 139.333, 152. The two middle votes average to 133.
        assertEquals(133.0, result.suggestedWeightsKg.getValue("bench-press"), 1e-9)
    }

    @Test
    fun setsWithoutASessionIdVoteSeparatelyInsteadOfMerging() = runTest {
        val sets = listOf(
            set("bench-press", day = 90, weightKg = 100.0, kind = LoadKind.EXTERNAL),
            set(
                "bench-press",
                day = 92,
                weightKg = 140.0,
                kind = LoadKind.EXTERNAL,
                sessionId = null
            ),
            set(
                "bench-press",
                day = 93,
                weightKg = 140.0,
                kind = LoadKind.EXTERNAL,
                sessionId = null
            )
        )

        val result = useCase(
            sources = sources(sets),
            pauseIncrements = false,
            utcOffsetMillis = 0L,
            nowMillis = NOW_MILLIS
        )

        // The two unattributed rows are distinct observations, not one session: votes are 126.667,
        // 177.333 and 177.333, so the median is 177.333. Merged into a single vote it would be
        // 152.0, letting an unknown grouping silently dilute the recorded work.
        assertEquals(177.33333333333334, result.suggestedWeightsKg.getValue("bench-press"), 1e-9)
        assertEquals(3, result.loadExplanations.getValue("bench-press").baseline.qualifyingSetCount)
    }

    @Test
    fun deloadWeekProvenanceDoesNotProveIntentOrExcludeLightWorkingEvidence() = runTest {
        val lightDeloadSets = listOf(
            set("bench-press", day = 90, weightKg = 60.0, kind = LoadKind.EXTERNAL)
                .copy(weekNumber = 4),
            set("bench-press", day = 92, weightKg = 60.0, kind = LoadKind.EXTERNAL)
                .copy(weekNumber = 4)
        )
        val result = useCase(
            sources = sources(
                listOf(set("bench-press", day = 1, weightKg = 120.0, kind = LoadKind.EXTERNAL)) +
                    lightDeloadSets
            ),
            pauseIncrements = false,
            utcOffsetMillis = 0L,
            nowMillis = NOW_MILLIS
        )

        assertEquals(76.0, result.suggestedWeightsKg.getValue("bench-press"), 1e-9)
    }

    @Test
    fun anEligibleManualRecordIsAFloorForRecentEvidence() = runTest {
        val sets = listOf(
            set("bench-press", day = 90, weightKg = 100.0, kind = LoadKind.EXTERNAL),
            set("bench-press", day = 92, weightKg = 100.0, kind = LoadKind.EXTERNAL)
        )
        val withRecord = sources(sets).copy(
            personalRecords = listOf(PersonalRecord("bench-press", 140.0, 3))
        )

        val result = useCase(
            sources = withRecord,
            pauseIncrements = false,
            utcOffsetMillis = 0L,
            nowMillis = NOW_MILLIS
        )

        // Epley(140 x 3) = 154; a three-rep manual record is eligible and floors the 126.667
        // session-balanced recent estimate.
        assertEquals(154.0, result.suggestedWeightsKg.getValue("bench-press"), 1e-9)
        val explanation = result.loadExplanations.getValue("bench-press").baseline
        assertEquals(LoadBaselineSource.MANUAL_PERSONAL_RECORD, explanation.source)
        assertEquals(154.0, requireNotNull(explanation.manualFloorKg), 1e-9)
        assertEquals(126.66666666666667, requireNotNull(explanation.recentEstimateKg), 1e-9)
        assertEquals(2, explanation.qualifyingSetCount)
    }

    @Test
    fun aTwoRepManualRecordDoesNotRaiseTheFloorWhileThreeRepsDoes() = runTest {
        val sets = listOf(
            set("bench-press", day = 90, weightKg = 100.0, kind = LoadKind.EXTERNAL),
            set("bench-press", day = 92, weightKg = 100.0, kind = LoadKind.EXTERNAL)
        )

        suspend fun baselineFor(reps: Int) = useCase(
            sources = sources(sets).copy(
                personalRecords = listOf(PersonalRecord("bench-press", 140.0, reps))
            ),
            pauseIncrements = false,
            utcOffsetMillis = 0L,
            nowMillis = NOW_MILLIS
        )

        val lowRep = baselineFor(2)
        val threeRep = baselineFor(3)

        // Epley(140 x 2) = 149.333, which would have raised the baseline. A two-rep peak attempt is
        // excluded from the automatic floor, so recent evidence alone decides.
        assertEquals(126.66666666666667, lowRep.suggestedWeightsKg.getValue("bench-press"), 1e-9)
        assertNull(lowRep.loadExplanations.getValue("bench-press").baseline.manualFloorKg)
        assertEquals(
            LoadBaselineSource.RECENT_PERFORMANCE,
            lowRep.loadExplanations.getValue("bench-press").baseline.source
        )
        // Epley(140 x 3) = 154 remains eligible.
        assertEquals(154.0, threeRep.suggestedWeightsKg.getValue("bench-press"), 1e-9)
    }

    @Test
    fun aLowRepManualRecordAloneDoesNotInventABaseline() = runTest {
        val result = useCase(
            sources = sources(emptyList()).copy(
                personalRecords = listOf(PersonalRecord("bench-press", 140.0, 2))
            ),
            pauseIncrements = false,
            utcOffsetMillis = 0L,
            nowMillis = NOW_MILLIS
        )

        // The stored record is untouched and still cannot seed a rep-range baseline, but it is
        // carried as the floor a short (1–2 rep) prescription may use.
        assertTrue("bench-press" in result.withheldWeightExerciseIds)
        assertTrue("bench-press" !in result.suggestedWeightsKg)
        assertTrue("bench-press" !in result.loadExplanations)
        assertEquals(
            OneRepMax.estimate(140.0, 2),
            result.lowRepManualFloorsKg.getValue("bench-press"),
            1e-9
        )
    }

    @Test
    fun cutoffEqualityIsIncludedInTheRecentWindow() = runTest {
        // now - 42 days is exactly day 58; both endpoints count.
        val sets = listOf(
            set("bench-press", day = 58, weightKg = 100.0, kind = LoadKind.EXTERNAL),
            set("bench-press", day = 100, weightKg = 100.0, kind = LoadKind.EXTERNAL)
        )
        val result = useCase(
            sources = sources(sets),
            pauseIncrements = false,
            utcOffsetMillis = 0L,
            nowMillis = NOW_MILLIS
        )

        assertEquals(126.66666666666667, result.suggestedWeightsKg.getValue("bench-press"), 1e-9)
    }

    @Test
    fun justExpiredEvidenceIsExcludedAndWithholdsTheSuggestion() = runTest {
        // Day 57 is one day past the 42-day window, leaving only one in-window set.
        val sets = listOf(
            set("bench-press", day = 57, weightKg = 120.0, kind = LoadKind.EXTERNAL),
            set("bench-press", day = 100, weightKg = 100.0, kind = LoadKind.EXTERNAL)
        )
        val result = useCase(
            sources = sources(sets),
            pauseIncrements = false,
            utcOffsetMillis = 0L,
            nowMillis = NOW_MILLIS
        )

        assertTrue("bench-press" in result.withheldWeightExerciseIds)
        assertTrue("bench-press" !in result.suggestedWeightsKg)
    }

    @Test
    fun aHeavierFutureSetCannotRaiseTheEstimateOrProgression() = runTest {
        val sets = completedExternalSessions("bench-press", target()) +
            set("bench-press", day = 101, weightKg = 500.0, kind = LoadKind.EXTERNAL)
        val result = useCase(
            sources = sources(sets),
            pauseIncrements = false,
            utcOffsetMillis = 0L,
            nowMillis = NOW_MILLIS
        )

        // Three completed in-window sessions earn +2.5 over the 126.667 bound. If the future 500 kg
        // set leaked in, the bound would be far higher and this assertion would fail.
        assertEquals(129.16666666666667, result.suggestedWeightsKg.getValue("bench-press"), 1e-9)
    }

    @Test
    fun configuredRepBoundsGovernRecentEvidence() = runTest {
        val narrow = BuildPlannerLoadInputsUseCase(
            catalog = FakeCatalog(listOf(exercise("bench-press", ExerciseLoadCapability.EXTERNAL))),
            buildRecentWeights = BuildRecentWeightsUseCase(),
            progressWeights = ProgressWeightsUseCase(),
            weightConfig = SuggestedWeightConfig(maxRepsForEstimate = 3)
        )
        val sets = listOf(
            set("bench-press", day = 99, weightKg = 100.0, kind = LoadKind.EXTERNAL),
            set("bench-press", day = 100, weightKg = 100.0, kind = LoadKind.EXTERNAL)
        )

        val result = narrow(
            sources = sources(sets),
            pauseIncrements = false,
            utcOffsetMillis = 0L,
            nowMillis = NOW_MILLIS
        )

        assertTrue("bench-press" in result.withheldWeightExerciseIds)
    }

    @Test
    fun futureSetsAreNotIncludedInModelHistoryContext() = runTest {
        val source = sources(
            listOf(
                set("bench-press", day = 99, kind = LoadKind.EXTERNAL),
                set("bench-press", day = 100, kind = LoadKind.EXTERNAL),
                set("bench-press", day = 101, weightKg = 500.0, kind = LoadKind.EXTERNAL)
            )
        ).copy(workoutDataSharingEnabled = true)

        val result = useCase(
            sources = source,
            pauseIncrements = false,
            utcOffsetMillis = 0L,
            nowMillis = NOW_MILLIS
        )

        assertTrue(result.recentWeights.all { it.performedAtMillis <= NOW_MILLIS })
    }

    @Test
    fun warmupsAndUnusableLoadsOrRepsDoNotQualifyAsRecentEvidence() = runTest {
        val sets = listOf(
            set("bench-press", day = 99, weightKg = 100.0, kind = LoadKind.EXTERNAL),
            set("bench-press", day = 100, weightKg = 100.0, kind = LoadKind.EXTERNAL),
            set("bench-press", day = 100, weightKg = 500.0, kind = LoadKind.EXTERNAL)
                .copy(isWarmup = true),
            set("bench-press", day = 100, weightKg = 500.0, kind = LoadKind.EXTERNAL)
                .copy(weightKg = null),
            set("bench-press", day = 100, weightKg = 500.0, kind = LoadKind.EXTERNAL)
                .copy(weightKg = 0.0),
            set("bench-press", day = 100, weightKg = 500.0, kind = LoadKind.EXTERNAL)
                .copy(reps = 16)
        )

        val result = useCase(
            sources = sources(sets),
            pauseIncrements = false,
            utcOffsetMillis = 0L,
            nowMillis = NOW_MILLIS
        )

        assertEquals(126.66666666666667, result.suggestedWeightsKg.getValue("bench-press"), 1e-9)
    }

    @Test
    fun manualRecordDoesNotExemptAStaleHistoryWhenItIsBelowRecentEvidence() = runTest {
        val logged = listOf(
            set("bench-press", day = 1, weightKg = 120.0, kind = LoadKind.EXTERNAL),
            set("bench-press", day = 90, weightKg = 100.0, kind = LoadKind.EXTERNAL),
            set("bench-press", day = 92, weightKg = 100.0, kind = LoadKind.EXTERNAL)
        ) + WorkoutSet(
            exerciseId = "bench-press",
            reps = 8,
            weightKg = 90.0,
            loadKind = LoadKind.EXTERNAL,
            performedAtMillis = 0L
        )
        val result = useCase(
            sources = sources(logged).copy(
                personalRecords = listOf(PersonalRecord("bench-press", 50.0, 3))
            ),
            pauseIncrements = false,
            utcOffsetMillis = 0L,
            nowMillis = NOW_MILLIS
        )

        assertEquals(126.66666666666667, result.suggestedWeightsKg.getValue("bench-press"), 1e-9)
    }

    @Test
    fun ineligibleManualRecordDoesNotRestoreAnExpiredHistoricalSuggestion() = runTest {
        val result = useCase(
            sources = sources(
                listOf(set("bench-press", day = 1, weightKg = 120.0, kind = LoadKind.EXTERNAL))
            ).copy(personalRecords = listOf(PersonalRecord("bench-press", 100.0, 16))),
            pauseIncrements = false,
            utcOffsetMillis = 0L,
            nowMillis = NOW_MILLIS
        )

        assertTrue("bench-press" in result.withheldWeightExerciseIds)
        assertTrue("bench-press" !in result.suggestedWeightsKg)
    }

    @Test
    fun aHigherManualRecordIsTheBaselineSourceNotMerelyAFloor() = runTest {
        val result = useCase(
            sources = sources(
                listOf(
                    set("bench-press", day = 99, weightKg = 80.0, kind = LoadKind.EXTERNAL),
                    set("bench-press", day = 100, weightKg = 80.0, kind = LoadKind.EXTERNAL)
                )
            ).copy(
                personalRecords = listOf(PersonalRecord("bench-press", weightKg = 120.0, reps = 8))
            ),
            pauseIncrements = false,
            utcOffsetMillis = 0L,
            nowMillis = NOW_MILLIS
        )

        val explanation = result.loadExplanations.getValue("bench-press").baseline
        // Recent estimate: Epley(80 x 8) = 101.33; manual record: Epley(120 x 8) = 152, so the record
        // determines the baseline and names the source.
        assertEquals(LoadBaselineSource.MANUAL_PERSONAL_RECORD, explanation.source)
        assertEquals(152.0, explanation.baselineKg, 1e-9)
        assertEquals(101.33333333333333, requireNotNull(explanation.recentEstimateKg), 1e-9)
        assertEquals(152.0, requireNotNull(explanation.manualFloorKg), 1e-9)
        assertEquals(2, explanation.qualifyingSetCount)
    }

    @Test
    fun recordsTheRecentPerformanceBaselineAndItsQualifyingSetCount() = runTest {
        val result = useCase(
            sources = sources(completedExternalSessions("bench-press")),
            pauseIncrements = false,
            utcOffsetMillis = 0L,
            nowMillis = NOW_MILLIS
        )

        val explanation = result.loadExplanations.getValue("bench-press").baseline
        assertEquals(LoadBaselineSource.RECENT_PERFORMANCE, explanation.source)
        assertEquals(9, explanation.qualifyingSetCount)
        assertEquals(126.66666666666667, explanation.baselineKg, 1e-9)
        assertEquals(126.66666666666667, explanation.recentEstimateKg!!, 1e-9)
        assertNull(explanation.manualFloorKg)
    }

    @Test
    fun recordsAManualRecordBaselineAndWhyProgressionDidNotApply() = runTest {
        val result = useCase(
            sources = sources(sets = emptyList()).copy(
                personalRecords = listOf(PersonalRecord("bench-press", weightKg = 100.0, reps = 8))
            ),
            pauseIncrements = false,
            utcOffsetMillis = 0L,
            nowMillis = NOW_MILLIS
        )

        val explanation = result.loadExplanations.getValue("bench-press")
        assertEquals(LoadBaselineSource.MANUAL_PERSONAL_RECORD, explanation.baseline.source)
        assertNull(explanation.baseline.recentEstimateKg)
        assertEquals(0, explanation.baseline.qualifyingSetCount)
        assertEquals(126.66666666666667, explanation.baseline.baselineKg, 1e-9)
        // With no accepted plan there is no typed prescription to earn a streak against.
        assertEquals(ProgressionOutcome.NONE_NO_TYPED_PRESCRIPTION, explanation.progression.outcome)
    }

    @Test
    fun theRecordedBaselinePlusDeltaAlwaysMatchesTheSuggestedWeight() = runTest {
        val result = useCase(
            sources = sources(completedExternalSessions("bench-press", target())),
            pauseIncrements = false,
            utcOffsetMillis = 0L,
            nowMillis = NOW_MILLIS
        )

        val explanation = result.loadExplanations.getValue("bench-press")
        assertEquals(ProgressionOutcome.APPLIED, explanation.progression.outcome)
        assertEquals(
            result.suggestedWeightsKg.getValue("bench-press"),
            explanation.baseline.baselineKg + explanation.progression.deltaKg,
            1e-9
        )
    }

    @Test
    fun aWithheldExerciseHasNoBaselineRecord() = runTest {
        val result = useCase(
            sources = sources(
                listOf(
                    set("bench-press", day = 1, weightKg = 120.0, kind = LoadKind.EXTERNAL),
                    set("bench-press", day = 90, weightKg = 100.0, kind = LoadKind.EXTERNAL)
                )
            ),
            pauseIncrements = false,
            utcOffsetMillis = 0L,
            nowMillis = NOW_MILLIS
        )

        assertTrue("bench-press" in result.withheldWeightExerciseIds)
        assertTrue("bench-press" !in result.loadExplanations)
    }

    private fun completedExternalSessions(
        exerciseId: String,
        target: WorkoutPrescriptionSnapshot? = null
    ): List<WorkoutSet> = listOf(100L, 99L, 98L).flatMap { day ->
        List(3) { set(exerciseId, day = day, kind = LoadKind.EXTERNAL, target = target) }
    }

    private fun target(weightKg: Double = 80.0, kind: LoadKind = LoadKind.EXTERNAL) =
        WorkoutPrescriptionSnapshot(
            exerciseId = "bench-press",
            sets = 3,
            reps = 8,
            loadKind = kind,
            weightKg = weightKg,
            isDeload = false
        )

    private fun set(
        exerciseId: String,
        day: Long,
        weightKg: Double = 100.0,
        kind: LoadKind = LoadKind.LEGACY_UNSPECIFIED,
        target: WorkoutPrescriptionSnapshot? = null,
        sessionId: String? = "s$day",
        reps: Int = 8
    ) = WorkoutSet(
        exerciseId = exerciseId,
        reps = reps,
        weightKg = weightKg,
        loadKind = kind,
        performedAtMillis = day * DAY_MILLIS,
        sessionId = sessionId,
        prescriptionSnapshot = target
    )

    private fun sources(sets: List<WorkoutSet>) = WorkoutPlanSources(
        availableEquipment = emptySet(),
        selectedEngine = PlannerEngineId.DETERMINISTIC,
        daysPerWeek = 3,
        loggedSets = emptyList(),
        loggedWorkoutSets = sets,
        progressionHistory = ProgressionHistory(sets, sessionsOf(sets))
    )

    private fun sessionsOf(sets: List<WorkoutSet>) = sets
        .mapNotNull { set -> set.sessionId?.let { it to set } }
        .groupBy({ it.first }, { it.second })
        .map { (id, group) ->
            WorkoutSession(
                id = id,
                startedAtMillis = group.minOf { it.performedAtMillis },
                endedAtMillis = group.maxOf { it.performedAtMillis },
                localEpochDay = 0
            )
        }

    private fun exercise(id: String, capability: ExerciseLoadCapability) = Exercise(
        id = id,
        name = id,
        requiredEquipment = emptySet(),
        primaryMuscles = setOf(MuscleGroup.CHEST_UPPER),
        movementPattern = MovementPattern.HORIZONTAL_PUSH,
        loadCapability = capability
    )

    private class FakeCatalog(private val exercises: List<Exercise>) : ExerciseCatalog {
        override suspend fun all(): List<Exercise> = exercises
    }

    private companion object {
        const val DAY_MILLIS = 86_400_000L
        const val NOW_MILLIS = 100 * DAY_MILLIS
    }
}
