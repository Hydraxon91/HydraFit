package com.hydrafit.app.core.domain.workout

import com.hydrafit.app.core.domain.time.TimeProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class CorrectWorkoutSetTimeUseCaseTest {

    @Test
    fun forwardsTheCorrectionAndTheCurrentOffsetToTheResegmenter() = runTest {
        val resegmenter = RecordingSessionResegmenter()
        val offset = 3_600_000L

        CorrectWorkoutSetTimeUseCase(resegmenter, offsetProvider(offset))(7L, 1_234L)

        assertEquals(listOf(Triple(7L, 1_234L, offset)), resegmenter.corrections)
    }

    private fun offsetProvider(offsetMillis: Long) = object : TimeProvider {
        override fun nowMillis(): Long = 0L

        override fun utcOffsetMillis(): Long = offsetMillis
    }

    private class RecordingSessionResegmenter : SessionResegmenter {
        val corrections = mutableListOf<Triple<Long, Long, Long>>()

        override suspend fun resegmentAfterTimeCorrection(
            setId: Long,
            performedAtMillis: Long,
            utcOffsetMillis: Long
        ) {
            corrections.add(Triple(setId, performedAtMillis, utcOffsetMillis))
        }
    }
}
