package com.hydrafit.app.core.domain.workout

import com.hydrafit.app.core.domain.time.TimeProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

class CorrectWorkoutSetUseCaseTest {
    @Test
    fun forwardsValidValuesIncludingZeroAndAbsentLoadAndRir() = runTest {
        val port = RecordingResegmenter()
        val clock = object : TimeProvider {
            override fun nowMillis(): Long = 1000L
            override fun utcOffsetMillis(): Long = 3600000L
        }
        val action = CorrectWorkoutSetUseCase(port, clock)
        val zero = WorkoutSetCorrection(8, 0.0, 0, 1000L)
        val absent = WorkoutSetCorrection(10, null, null, 500L)
        val force = WorkoutSetCorrection(
            reps = null,
            weightKg = null,
            rir = null,
            performedAtMillis = 500L,
            forceNewtons = 250.0,
            device = DeviceAttribution("Grip Trainer", 60.0)
        )
        action(7L, zero)
        action(7L, absent)
        action(7L, force)
        assertEquals(
            listOf(
                Triple(7L, zero, 3600000L),
                Triple(7L, absent, 3600000L),
                Triple(7L, force, 3600000L)
            ),
            port.calls
        )
    }

    @Test
    fun invalidValuesNeverReachPersistence() = runTest {
        val port = RecordingResegmenter()
        val action = CorrectWorkoutSetUseCase(port, TimeProvider { 1000L })
        val valid = WorkoutSetCorrection(8, 10.0, 2, 500L)
        listOf(
            valid.copy(reps = 0),
            valid.copy(reps = -1),
            valid.copy(weightKg = -1.0),
            valid.copy(weightKg = Double.NaN),
            valid.copy(weightKg = Double.POSITIVE_INFINITY),
            valid.copy(rir = -1),
            valid.copy(rir = 11),
            valid.copy(performedAtMillis = 1001L),
            // A force set is load-free and only a force set may name a measuring device.
            valid.copy(reps = null, forceNewtons = 250.0, weightKg = 80.0),
            valid.copy(reps = null, device = DeviceAttribution("Grip Trainer", 60.0))
        ).forEach { invalid ->
            assertFailsWith<IllegalArgumentException> { action(7L, invalid) }
        }
        assertEquals(emptyList(), port.calls)
    }

    private class RecordingResegmenter : SessionResegmenter {
        val calls = mutableListOf<Triple<Long, WorkoutSetCorrection, Long>>()

        override suspend fun resegmentAfterSetCorrection(
            setId: Long,
            correction: WorkoutSetCorrection,
            utcOffsetMillis: Long
        ) {
            calls.add(Triple(setId, correction, utcOffsetMillis))
        }

        override suspend fun resegmentAfterTimeCorrection(
            setId: Long,
            performedAtMillis: Long,
            utcOffsetMillis: Long
        ) = error("Unexpected time-only correction")
    }
}
