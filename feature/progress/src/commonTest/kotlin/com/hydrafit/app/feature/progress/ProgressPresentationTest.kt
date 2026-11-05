package com.hydrafit.app.feature.progress

import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.ProgressDay
import com.hydrafit.app.core.domain.workout.ProgressRecord
import com.hydrafit.app.core.domain.workout.ProgressRecordKey
import com.hydrafit.app.core.domain.workout.ProgressRecordType
import com.hydrafit.app.core.domain.workout.WorkoutHistorySet
import com.hydrafit.app.core.domain.workout.WorkoutTimingProvenance
import hydrafit.feature.progress.generated.resources.Res
import hydrafit.feature.progress.generated.resources.progress_table_duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProgressPresentationTest {

    @Test
    fun displayWeightConvertsStoredKilogramsToTheSelectedUnit() {
        assertEquals("100 kg", displayWeight(100.0, WeightUnit.KG))
        // 100 kg rounds to 220.5 lb at one decimal place.
        assertEquals("220.5 lb", displayWeight(100.0, WeightUnit.LB))
        assertEquals("0 kg", displayWeight(0.0, WeightUnit.KG))
    }

    @Test
    fun displayVolumeConvertsOnlyTheKilogramRepsMagnitude() {
        assertEquals("1000 kg·reps", displayVolume(1000.0, WeightUnit.KG))
        assertEquals("2204.6 lb·reps", displayVolume(1000.0, WeightUnit.LB))
    }

    @Test
    fun theInitialMetricIsVolumeAndMetricEmptinessStaysSeparateFromRangeEmptiness() {
        val state = ProgressUiState(
            utcOffsetMillis = 0L,
            selectedExerciseId = "squat",
            selectedExerciseName = "Squat",
            days = listOf(progressDayForTest(localEpochDay = 1L)),
            chartsLoading = false,
            chartsFailed = false
        )
        assertEquals(ProgressMetric.EXTERNAL_VOLUME, state.metric)
        assertFalse(state.chartsHaveNoEligibleDays)
        assertFalse(state.chartsHaveNoSelectedMetricPoints)

        val noEstimate = state.copy(metric = ProgressMetric.ESTIMATED_ONE_REP_MAX)
        assertTrue(noEstimate.chartsHaveNoSelectedMetricPoints)
        // The recorded day still exists, so the numeric table, records and history stay available.
        assertFalse(noEstimate.chartsHaveNoEligibleDays)
        assertEquals(1, noEstimate.days.size)

        val withEstimate = noEstimate.copy(
            days = listOf(progressDayForTest(localEpochDay = 1L, estimate = 120.0))
        )
        assertFalse(withEstimate.chartsHaveNoSelectedMetricPoints)
        assertEquals(120.0, withEstimate.chartPoints.single().value)
    }

    @Test
    fun dayLabelIsAFixedOffsetIsoCivilDate() {
        assertEquals("1970-01-01", progressDayLabel(0L))
        assertEquals("1970-01-02", progressDayLabel(1L))
    }

    @Test
    fun exerciseSearchMatchesNamesCaseInsensitively() {
        val state = ProgressUiState(
            utcOffsetMillis = 0L,
            exercises = listOf(
                ProgressExerciseOption("squat", "Back Squat"),
                ProgressExerciseOption("bench", "Bench Press")
            )
        )
        assertEquals(2, state.matchingExercises.size)
        assertEquals(
            listOf("squat"),
            state.copy(exerciseQuery = "squat").matchingExercises.map { it.id }
        )
        assertEquals(
            listOf("bench"),
            state.copy(exerciseQuery = "  PRESS ").matchingExercises.map { it.id }
        )
    }

    @Test
    fun aSelectedIdWithNoCatalogNameIsReportedAsMissingNotCleared() {
        val state = ProgressUiState(
            utcOffsetMillis = 0L,
            selectedExerciseId = "gone",
            selectedExerciseName = null
        )
        assertTrue(state.selectedExerciseNameMissing)
        assertFalse(state.copy(selectedExerciseName = "Renamed").selectedExerciseNameMissing)
    }

    @Test
    fun chartsEmptyAndHistoryEmptyAreIndependentFlags() {
        val noSelection = ProgressUiState(utcOffsetMillis = 0L)
        assertFalse(noSelection.chartsHaveNoEligibleDays)
        assertFalse(noSelection.historyIsEmpty)

        val loaded = ProgressUiState(
            utcOffsetMillis = 0L,
            selectedExerciseId = "squat",
            selectedExerciseName = "Squat",
            chartsLoading = false,
            chartsFailed = false,
            days = emptyList(),
            historyLoading = false,
            historyFailed = false,
            historySets = emptyList()
        )
        assertTrue(loaded.chartsHaveNoEligibleDays)
        assertTrue(loaded.historyIsEmpty)

        // A recorded history does not make the charts non-empty, and vice versa.
        val withHistory = loaded.copy(historySets = listOf(historySetForTest(1L)))
        assertTrue(withHistory.chartsHaveNoEligibleDays)
        assertFalse(withHistory.historyIsEmpty)
    }

    @Test
    fun durationTotalAppearsOnlyForDaysWithRecordedTimedWork() {
        val timed = progressDayForTest(localEpochDay = 1L, totalDurationSeconds = 90L)
        val untimed = progressDayForTest(localEpochDay = 0L, totalDurationSeconds = 0L)

        // A day with timed work carries the localized duration line; a day without shows no total,
        // not a misleading zero.
        assertEquals(Res.string.progress_table_duration, progressDayDurationResource(timed))
        assertNull(progressDayDurationResource(untimed))
    }

    @Test
    fun chartMarksStayInsideTheCanvasEdges() {
        val radius = 3f
        val width = 100f
        // The first day sits at the left usable edge, the last at the right usable edge.
        assertEquals(radius, progressMarkX(0L, 0L, 10L, width, radius))
        assertEquals(width - radius, progressMarkX(10L, 0L, 10L, width, radius))
        // A middle day is interpolated across the usable span, not the full canvas width.
        assertEquals(radius + (width - 2f * radius) / 2f, progressMarkX(5L, 0L, 10L, width, radius))
        // A single-day window collapses to the left edge rather than dividing by zero.
        assertEquals(radius, progressMarkX(7L, 7L, 7L, width, radius))
    }

    @Test
    fun chartMaximumRestsOnTheTopInsetAndZeroOnTheBottom() {
        val radius = 4f
        val height = 60f
        assertEquals(radius, progressMarkY(10.0, 10.0, height, radius))
        assertEquals(height - radius, progressMarkY(0.0, 10.0, height, radius))
        assertEquals(radius + (height - 2f * radius) / 2f, progressMarkY(5.0, 10.0, height, radius))
        // A negative reading is clamped rather than drawn below the canvas.
        assertEquals(height - radius, progressMarkY(-1.0, 10.0, height, radius))
    }
}

private fun progressDayForTest(
    localEpochDay: Long,
    totalDurationSeconds: Long = 0L,
    estimate: Double? = null
) = ProgressDay(
    localEpochDay = localEpochDay,
    workingSets = 3,
    totalReps = 30L,
    externalVolumeKg = 1000.0,
    bestLoads = emptyList(),
    bestReps = emptyList(),
    estimatedOneRepMax = estimate?.let {
        ProgressRecord(
            key = ProgressRecordKey(
                type = ProgressRecordType.ESTIMATED_ONE_REP_MAX,
                loadKind = LoadKind.EXTERNAL
            ),
            value = it,
            setIds = emptyList()
        )
    },
    totalDurationSeconds = totalDurationSeconds
)

private fun historySetForTest(setId: Long) = WorkoutHistorySet(
    id = setId,
    exerciseId = "squat",
    exerciseName = "Squat",
    reps = 5,
    weightKg = 100.0,
    loadKind = LoadKind.EXTERNAL,
    isWarmup = false,
    rir = null,
    performedAtMillis = 0L,
    timingProvenance = WorkoutTimingProvenance.LIVE
)
