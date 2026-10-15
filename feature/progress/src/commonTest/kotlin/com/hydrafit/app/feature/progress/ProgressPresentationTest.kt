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
