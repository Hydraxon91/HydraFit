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
    fun twoRecentSetsBoundHistoryAndProgressionConsistently() = runTest {
        val sets = listOf(
            set("bench-press", day = 1, weightKg = 120.0, kind = LoadKind.EXTERNAL),
            set("bench-press", day = 90, weightKg = 100.0, kind = LoadKind.EXTERNAL),
            set("bench-press", day = 92, weightKg = 100.0, kind = LoadKind.EXTERNAL)
        )

        val result = useCase(
            sources = sources(sets),
            pauseIncrements = false,
            utcOffsetMillis = 0L,
            nowMillis = NOW_MILLIS
        )

        assertEquals(126.66666666666667, result.suggestedWeightsKg.getValue("bench-press"), 1e-9)
        assertEquals(result.suggestedWeightsKg, result.recentWeightCaps)
    }

    @Test
    fun strongerEstimateStillInsideTheRecentWindowRemainsTheBound() = runTest {
        val result = useCase(
            sources = sources(
                listOf(
                    set("bench-press", day = 90, weightKg = 120.0, kind = LoadKind.EXTERNAL),
                    set("bench-press", day = 92, weightKg = 100.0, kind = LoadKind.EXTERNAL)
                )
            ),
            pauseIncrements = false,
            utcOffsetMillis = 0L,
            nowMillis = NOW_MILLIS
        )

        assertEquals(152.0, result.suggestedWeightsKg.getValue("bench-press"), 1e-9)
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
            personalRecords = listOf(PersonalRecord("bench-press", 130.0, 1))
        )

        val result = useCase(
            sources = withRecord,
            pauseIncrements = false,
            utcOffsetMillis = 0L,
            nowMillis = NOW_MILLIS
        )

        // Epley(130 x 1) = 134.333; the eligible manual estimate is the floor above recent evidence.
        assertEquals(134.33333333333334, result.suggestedWeightsKg.getValue("bench-press"), 1e-9)
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
                personalRecords = listOf(PersonalRecord("bench-press", 50.0, 1))
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
        target: WorkoutPrescriptionSnapshot? = null
    ) = WorkoutSet(
        exerciseId = exerciseId,
        reps = 8,
        weightKg = weightKg,
        loadKind = kind,
        performedAtMillis = day * DAY_MILLIS,
        sessionId = "s$day",
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
