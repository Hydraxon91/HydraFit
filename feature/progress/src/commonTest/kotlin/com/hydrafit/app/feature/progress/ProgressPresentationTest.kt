package com.hydrafit.app.feature.progress

import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.WorkoutHistorySet
import com.hydrafit.app.core.domain.workout.WorkoutTimingProvenance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
