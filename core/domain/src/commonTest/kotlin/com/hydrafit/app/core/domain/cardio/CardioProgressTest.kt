package com.hydrafit.app.core.domain.cardio

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class CardioProgressTest {

    private fun activity(type: CardioActivityType, day: Long, seconds: Int, metres: Int? = null) =
        CardioActivity(
            identity = "id-$type-$day-$seconds",
            type = type,
            startedAtMillis = day * 86_400_000L + 3_600_000L,
            durationSeconds = seconds,
            distanceMeters = metres
        )

    @Test
    fun totalsComeFromMatchingTotalsRatherThanAveragedPace() {
        val progress = buildCardioProgress(
            activities = listOf(
                // A short slow walk and a long fast run must not average to the walk's pace.
                activity(CardioActivityType.WALKING, day = 10, seconds = 600, metres = 500),
                activity(CardioActivityType.WALKING, day = 11, seconds = 3600, metres = 12_000)
            ),
            fromEpochDay = 1,
            throughEpochDay = 20,
            utcOffsetMillis = 0
        )

        val walking = progress.totalsByType.single { it.type == CardioActivityType.WALKING }
        assertEquals(2, walking.activities)
        assertEquals(4_200, walking.durationSeconds)
        assertEquals(12_500, walking.distanceMeters)
        assertEquals(12_500.0 / 4_200.0, walking.metresPerSecond)
    }

    @Test
    fun speedExcludesDurationOfActivitiesWithoutDistance() {
        val progress = buildCardioProgress(
            listOf(
                activity(CardioActivityType.WALKING, day = 10, seconds = 100, metres = 200),
                activity(CardioActivityType.WALKING, day = 11, seconds = 900)
            ),
            fromEpochDay = 1,
            throughEpochDay = 20,
            utcOffsetMillis = 0
        )
        val total = progress.totalsByType.single()
        assertEquals(1000L, total.durationSeconds)
        assertEquals(100L, total.distanceDurationSeconds)
        assertEquals(2.0, total.metresPerSecond)
    }

    @Test
    fun aTypeWithoutDistanceReportsNoPace() {
        val progress = buildCardioProgress(
            activities = listOf(activity(CardioActivityType.ROWING, day = 5, seconds = 1200)),
            fromEpochDay = 1,
            throughEpochDay = 10,
            utcOffsetMillis = 0
        )

        assertNull(progress.totalsByType.single().metresPerSecond)
    }

    @Test
    fun daysAreInclusiveAndExcludeActivitiesOutsideTheWindow() {
        val progress = buildCardioProgress(
            activities = listOf(
                activity(CardioActivityType.CYCLING, day = 9, seconds = 100),
                activity(CardioActivityType.CYCLING, day = 10, seconds = 200),
                activity(CardioActivityType.CYCLING, day = 11, seconds = 400)
            ),
            fromEpochDay = 10,
            throughEpochDay = 10,
            utcOffsetMillis = 0
        )

        assertEquals(listOf(10L), progress.days.map { it.epochDay })
        assertEquals(200, progress.days.single().durationSeconds)
        assertEquals(1, progress.totalsByType.single().activities)
    }

    @Test
    fun anInvertedWindowIsRefused() {
        assertFailsWith<IllegalArgumentException> {
            buildCardioProgress(
                emptyList(),
                fromEpochDay = 5,
                throughEpochDay = 4,
                utcOffsetMillis = 0
            )
        }
    }

    @Test
    fun activityValidationBoundsDurationDistanceAndNote() {
        val valid = activity(CardioActivityType.RUNNING, day = 1, seconds = 1800, metres = 5000)
        valid.requireValid()

        assertFailsWith<IllegalArgumentException> {
            valid.copy(durationSeconds = 0).requireValid()
        }
        assertFailsWith<IllegalArgumentException> {
            valid.copy(distanceMeters = 0).requireValid()
        }
        assertFailsWith<IllegalArgumentException> {
            valid.copy(identity = "").requireValid()
        }
        assertFailsWith<IllegalArgumentException> {
            valid.copy(note = "x".repeat(CardioActivity.MAX_NOTE_LENGTH + 1)).requireValid()
        }
    }
}
