package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.workout.WorkoutHistoryEntry
import com.hydrafit.app.core.domain.workout.WorkoutHistoryKey
import com.hydrafit.app.core.domain.workout.WorkoutHistoryNameSource
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkoutHistoryDaysTest {

    private fun session(id: String, at: Long) = WorkoutHistoryEntry(
        key = WorkoutHistoryKey.Session(id),
        timestampMillis = at,
        localEpochDay = null,
        sessionMissing = false,
        note = null,
        recordedSetCount = 1,
        nameSource = WorkoutHistoryNameSource.CURRENT_CATALOG
    )

    @Test
    fun theFirstEntryOfEachLocalDayStartsAHeadingNewestFirst() {
        val entries = listOf(
            session("c", 3 * DAY + 5 * HOUR),
            session("b", 3 * DAY + HOUR),
            session("a", 1 * DAY + 20 * HOUR)
        )

        assertEquals(
            setOf("session:c", "session:a"),
            historyDayStartKeys(entries, utcOffsetMillis = 0L)
        )
    }

    @Test
    fun theLocalOffsetDecidesWhereOneDayEndsAndTheNextBegins() {
        // 23:30 on one UTC day and 00:30 on the next are two days at UTC, but the same day at UTC+1.
        val entries = listOf(
            session("late", 2 * DAY + 30 * MINUTE),
            session(
                "early",
                DAY + 23 * HOUR + 30 * MINUTE
            )
        )

        assertEquals(setOf("session:late", "session:early"), historyDayStartKeys(entries, 0L))
        assertEquals(setOf("session:late"), historyDayStartKeys(entries, HOUR))
    }

    @Test
    fun aLaterPageContinuingADayDoesNotRepeatItsHeading() {
        val firstPage = listOf(session("a", 5 * DAY + 10 * HOUR))
        val withNextPage = firstPage + session("b", 5 * DAY + 2 * HOUR) + session("c", 4 * DAY)

        assertEquals(setOf("session:a"), historyDayStartKeys(firstPage, 0L))
        assertEquals(setOf("session:a", "session:c"), historyDayStartKeys(withNextPage, 0L))
    }

    @Test
    fun anEmptyListHasNoHeadings() {
        assertEquals(emptySet(), historyDayStartKeys(emptyList(), 0L))
    }

    private companion object {
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR
    }
}
