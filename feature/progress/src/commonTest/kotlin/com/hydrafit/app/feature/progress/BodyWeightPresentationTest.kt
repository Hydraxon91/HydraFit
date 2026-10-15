package com.hydrafit.app.feature.progress

import com.hydrafit.app.core.userdata.measurements.BodyWeightMeasurement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BodyWeightPresentationTest {

    @Test
    fun theRecordedRangeInvertsAcrossTheInsetCanvas() {
        val radius = 4f
        val height = 60f
        assertEquals(radius, bodyWeightMarkY(80.0, 70.0, 80.0, height, radius))
        assertEquals(height - radius, bodyWeightMarkY(70.0, 70.0, 80.0, height, radius))
        assertEquals(
            radius + (height - 2f * radius) / 2f,
            bodyWeightMarkY(75.0, 70.0, 80.0, height, radius)
        )
    }

    @Test
    fun aFlatRangeCentresItsSingleValueRatherThanDividingByZero() {
        val radius = 4f
        val height = 60f
        assertEquals(
            radius + (height - 2f * radius) / 2f,
            bodyWeightMarkY(80.0, 80.0, 80.0, height, radius)
        )
    }

    @Test
    fun aGoalOutsideTheRecordedRangeClampsToTheNearestEdge() {
        val radius = 4f
        val height = 60f
        assertEquals(radius, bodyWeightMarkY(90.0, 70.0, 80.0, height, radius))
        assertEquals(height - radius, bodyWeightMarkY(60.0, 70.0, 80.0, height, radius))
    }

    @Test
    fun theRangeWindowIsInclusiveAndExcludesDistantDays() {
        val state = BodyWeightUiState(
            loading = false,
            todayLocalEpochDay = 100L,
            range = ProgressRange.DAYS_28,
            measurements = listOf(
                BodyWeightMeasurement(100L, 80.0),
                BodyWeightMeasurement(73L, 79.0),
                BodyWeightMeasurement(72L, 78.0)
            )
        )
        assertEquals(listOf(73L, 100L), state.measurementsInRange.map { it.localEpochDay })
        assertFalse(state.isEmpty)
    }

    @Test
    fun anUnloadedOrEmptyStateIsReportedAsEmptyOnlyWhenNotLoading() {
        val loading = BodyWeightUiState(loading = true)
        assertFalse(loading.isEmpty)

        val failed = BodyWeightUiState(loading = false, loadFailed = true)
        assertFalse(failed.isEmpty)

        val loaded = BodyWeightUiState(loading = false)
        assertTrue(loaded.isEmpty)
    }
}
