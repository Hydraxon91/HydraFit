package com.hydrafit.app.core.domain.engine

import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.WorkoutSet
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/**
 * The load half of a plan request: only external resistance (or a legacy number on an exercise that
 * is external today) seeds the baseline; progression needs a typed external prescription, so a
 * pre-EX-02 accepted plan seeds the baseline but never fabricates a streak.
 */
class BuildPlannerLoadInputsUseCaseTest {

    private val useCase = BuildPlannerLoadInputsUseCase(
        catalog = FakeCatalog(
            listOf(
                exercise("bench-press", ExerciseLoadCapability.EXTERNAL),
                exercise("ab-roll", ExerciseLoadCapability.BODYWEIGHT_ONLY)
            )
        ),
        suggestWeights = SuggestWeightsUseCase(),
        buildRecentWeights = BuildRecentWeightsUseCase(),
        progressWeights = ProgressWeightsUseCase()
    )

    @Test
    fun legacyExternalSetsSeedTheBaseline() = runTest {
        val result = useCase(
            sources = sources(
                listOf(set("bench-press", day = 1, kind = LoadKind.LEGACY_UNSPECIFIED))
            ),
            latestPlan = null,
            pauseIncrements = false,
            utcOffsetMillis = 0L,
            nowMillis = NOW_MILLIS
        )

        // Epley(100kg x 8) = 126.666...
        assertEquals(126.66666666666667, result.suggestedWeightsKg.getValue("bench-press"), 1e-9)
    }

    @Test
    fun aLegacyPrescriptionSeedsTheBaselineButDoesNotEarnAStreak() = runTest {
        val completed = completedExternalSessions("bench-press")
        val result = useCase(
            sources = sources(completed),
            latestPlan = plan("bench-press", weightKg = 80.0, kind = LoadKind.LEGACY_UNSPECIFIED),
            pauseIncrements = false,
            utcOffsetMillis = 0L,
            nowMillis = NOW_MILLIS
        )

        // Three completed external sessions, but the accepted plan is legacy: no increment.
        assertEquals(126.66666666666667, result.suggestedWeightsKg.getValue("bench-press"), 1e-9)
    }

    @Test
    fun aTypedExternalPrescriptionEarnsAStreak() = runTest {
        val completed = completedExternalSessions("bench-press")
        val result = useCase(
            sources = sources(completed),
            latestPlan = plan("bench-press", weightKg = 80.0, kind = LoadKind.EXTERNAL),
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
            latestPlan = null,
            pauseIncrements = false,
            utcOffsetMillis = 0L,
            nowMillis = NOW_MILLIS
        )

        assertTrue(result.suggestedWeightsKg.isEmpty())
    }

    @Test
    fun aStaleMaximumIsTemperedByRecentPerformance() = runTest {
        val sets = listOf(
            set("bench-press", day = 1, weightKg = 120.0, kind = LoadKind.EXTERNAL),
            set("bench-press", day = 90, weightKg = 100.0, kind = LoadKind.EXTERNAL),
            set("bench-press", day = 92, weightKg = 100.0, kind = LoadKind.EXTERNAL)
        )

        val result = useCase(
            sources = sources(sets),
            latestPlan = null,
            pauseIncrements = false,
            utcOffsetMillis = 0L,
            nowMillis = NOW_MILLIS
        )

        // The old 120kg x 8 (Epley 152) is outside the window; the recent best bounds the suggestion.
        assertEquals(126.66666666666667, result.suggestedWeightsKg.getValue("bench-press"), 1e-9)
    }

    @Test
    fun aStaleMaximumIsKeptWhenRecentHistoryIsTooThin() = runTest {
        val sets = listOf(
            set("bench-press", day = 1, weightKg = 120.0, kind = LoadKind.EXTERNAL),
            set("bench-press", day = 90, weightKg = 100.0, kind = LoadKind.EXTERNAL)
        )

        val result = useCase(
            sources = sources(sets),
            latestPlan = null,
            pauseIncrements = false,
            utcOffsetMillis = 0L,
            nowMillis = NOW_MILLIS
        )

        // Only one recent set: the temper does not apply, so the all-time best is kept.
        assertEquals(152.0, result.suggestedWeightsKg.getValue("bench-press"), 1e-9)
    }

    @Test
    fun aManualRecordIsNotTemperedByRecentSets() = runTest {
        val sets = listOf(
            set("bench-press", day = 90, weightKg = 100.0, kind = LoadKind.EXTERNAL),
            set("bench-press", day = 92, weightKg = 100.0, kind = LoadKind.EXTERNAL)
        )
        val withRecord = sources(sets).copy(
            personalRecords = listOf(PersonalRecord("bench-press", 130.0, 1))
        )

        val result = useCase(
            sources = withRecord,
            latestPlan = null,
            pauseIncrements = false,
            utcOffsetMillis = 0L,
            nowMillis = NOW_MILLIS
        )

        // Epley(130 x 1) = 134.333; a manual record is a deliberate assertion and is not capped.
        assertEquals(134.33333333333334, result.suggestedWeightsKg.getValue("bench-press"), 1e-9)
    }

    private fun completedExternalSessions(exerciseId: String): List<WorkoutSet> =
        listOf(3L, 2L, 1L).flatMap { day ->
            List(3) { set(exerciseId, day = day, kind = LoadKind.EXTERNAL) }
        }

    private fun set(
        exerciseId: String,
        day: Long,
        weightKg: Double = 100.0,
        kind: LoadKind = LoadKind.LEGACY_UNSPECIFIED
    ) = WorkoutSet(
        exerciseId = exerciseId,
        reps = 8,
        weightKg = weightKg,
        loadKind = kind,
        performedAtMillis = day * DAY_MILLIS
    )

    private fun sources(sets: List<WorkoutSet>) = WorkoutPlanSources(
        availableEquipment = emptySet(),
        selectedEngine = PlannerEngineId.DETERMINISTIC,
        daysPerWeek = 3,
        loggedSets = emptyList(),
        loggedWorkoutSets = sets
    )

    private fun plan(exerciseId: String, weightKg: Double, kind: LoadKind) = AcceptedPlan(
        engine = PlannerEngineId.DETERMINISTIC,
        acceptedAtMillis = 0L,
        id = 1L,
        days = listOf(
            AcceptedDay(
                dayIndex = 0,
                focus = SplitFocus.FULL_BODY,
                exercises = listOf(
                    AcceptedExercise(
                        exerciseId = exerciseId,
                        sets = 3,
                        reps = 8,
                        name = "Bench",
                        movementPattern = MovementPattern.HORIZONTAL_PUSH,
                        suggestedWeightKg = weightKg,
                        loadCapability = ExerciseLoadCapability.EXTERNAL,
                        loadKind = kind
                    )
                )
            )
        )
    )

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
