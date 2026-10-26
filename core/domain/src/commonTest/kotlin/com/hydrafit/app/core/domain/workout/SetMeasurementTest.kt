package com.hydrafit.app.core.domain.workout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class SetMeasurementTest {

    @Test
    fun acceptsTheLowestPositiveRepCountAndSeconds() {
        assertEquals(1, SetMeasurement.Repetitions(1).count)
        assertEquals(1, SetMeasurement.Duration(1).seconds)
    }

    @Test
    fun acceptsTheLargestRepresentableRepCountAndSeconds() {
        assertEquals(Int.MAX_VALUE, SetMeasurement.Repetitions(Int.MAX_VALUE).count)
        assertEquals(Int.MAX_VALUE, SetMeasurement.Duration(Int.MAX_VALUE).seconds)
    }

    @Test
    fun rejectsZeroReps() {
        assertFailsWith<IllegalArgumentException> { SetMeasurement.Repetitions(0) }
    }

    @Test
    fun rejectsNegativeReps() {
        assertFailsWith<IllegalArgumentException> { SetMeasurement.Repetitions(-1) }
        assertFailsWith<IllegalArgumentException> { SetMeasurement.Repetitions(Int.MIN_VALUE) }
    }

    @Test
    fun rejectsZeroSeconds() {
        assertFailsWith<IllegalArgumentException> { SetMeasurement.Duration(0) }
    }

    @Test
    fun rejectsNegativeSeconds() {
        assertFailsWith<IllegalArgumentException> { SetMeasurement.Duration(-1) }
        assertFailsWith<IllegalArgumentException> { SetMeasurement.Duration(Int.MIN_VALUE) }
    }

    @Test
    fun revalidatesOnCopy() {
        assertFailsWith<IllegalArgumentException> { SetMeasurement.Repetitions(5).copy(count = 0) }
        assertFailsWith<IllegalArgumentException> { SetMeasurement.Duration(30).copy(seconds = -3) }
    }

    @Test
    fun equalityHoldsWithinACaseAndSeparatesTheCases() {
        assertEquals(SetMeasurement.Repetitions(5), SetMeasurement.Repetitions(5))
        assertEquals(SetMeasurement.Duration(30), SetMeasurement.Duration(30))
        assertNotEquals(SetMeasurement.Repetitions(5), SetMeasurement.Repetitions(6))
        assertNotEquals(SetMeasurement.Duration(30), SetMeasurement.Duration(31))
        // Same underlying number, different measurement: the cases are exclusive, not interchangeable.
        assertNotEquals<SetMeasurement>(SetMeasurement.Repetitions(30), SetMeasurement.Duration(30))
    }

    @Test
    fun casesAreDistinguishedExhaustively() {
        val measurements = listOf(
            SetMeasurement.Repetitions(8),
            SetMeasurement.Duration(45),
            SetMeasurement.Distance(400),
            SetMeasurement.Force(250.0)
        )

        val described = measurements.map { measurement ->
            when (measurement) {
                is SetMeasurement.Repetitions -> "reps:${measurement.count}"
                is SetMeasurement.Duration -> "seconds:${measurement.seconds}"
                is SetMeasurement.Distance -> "metres:${measurement.meters}"
                is SetMeasurement.Force -> "newtons:${measurement.newtons}"
            }
        }

        assertEquals(
            listOf("reps:8", "seconds:45", "metres:400", "newtons:250.0"),
            described
        )
    }
}
