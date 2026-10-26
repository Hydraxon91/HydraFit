package com.hydrafit.app.core.domain.workout

import com.hydrafit.app.core.domain.equipment.StationAttribution
import com.hydrafit.app.core.domain.equipment.StationLoadBasis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WorkoutSetStationTest {

    @Test
    fun stationAttributionIsIndependentOfTheMeasurementShape() {
        val station = StationAttribution(4L, "Lat pulldown A", StationLoadBasis.TOTAL)
        val timed = WorkoutSet(
            exerciseId = "lat-pulldown",
            weightKg = 55.0,
            performedAtMillis = 100L,
            durationSeconds = 45,
            station = station
        )
        val counted = timed.copy(durationSeconds = null, reps = 10)

        assertEquals(station, timed.station)
        assertEquals(station, counted.station)
        assertEquals(SetMeasurement.Duration(45), timed.measurement)
        assertEquals(SetMeasurement.Repetitions(10), counted.measurement)
        assertNull(timed.copy(station = null).station)
    }
}
