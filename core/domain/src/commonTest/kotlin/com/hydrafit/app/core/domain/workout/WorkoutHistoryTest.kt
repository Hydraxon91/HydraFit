package com.hydrafit.app.core.domain.workout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkoutHistoryTest {

    private fun entry(key: WorkoutHistoryKey, timestampMillis: Long, setCount: Int = 0) =
        WorkoutHistoryEntry(
            key = key,
            timestampMillis = timestampMillis,
            localEpochDay = 1L,
            sessionMissing = false,
            note = null,
            recordedSetCount = setCount,
            nameSource = if (setCount > 0) {
                WorkoutHistoryNameSource.CURRENT_CATALOG
            } else {
                WorkoutHistoryNameSource.NONE
            }
        )

    private fun filteredRequest(
        search: String,
        dayFrom: Long,
        dayTo: Long,
        utcOffsetMillis: Long,
        cursor: WorkoutHistoryCursor
    ) = WorkoutHistoryRequest(
        search = search,
        dayFrom = dayFrom,
        dayTo = dayTo,
        utcOffsetMillis = utcOffsetMillis,
        cursor = cursor
    )

    @Test
    fun blankOrWhitespaceSearchIsNotAFilter() {
        assertFalse(WorkoutHistoryRequest(search = "").hasSearch)
        assertFalse(WorkoutHistoryRequest(search = "   ").hasSearch)
        assertEquals("%", WorkoutHistoryRequest(search = "   ").searchPattern)
        assertTrue(WorkoutHistoryRequest(search = "  squat ").hasSearch)
        assertEquals("squat", WorkoutHistoryRequest(search = "  squat ").normalizedSearch)
    }

    @Test
    fun searchPatternFoldsUnicodeCaseBeforeEscaping() {
        assertEquals("%strasse%", WorkoutHistoryRequest(search = "Stra\u00DFe").searchPattern)
        assertEquals("%\u00E9paules%", WorkoutHistoryRequest(search = "\u00C9PAULES").searchPattern)
    }

    @Test
    fun searchPatternEscapesEveryLikeWildcardAndTheEscapeCharacter() {
        assertEquals("%50\\%%", WorkoutHistoryRequest(search = "50%").searchPattern)
        assertEquals("%back\\_squat%", WorkoutHistoryRequest(search = "back_squat").searchPattern)
        assertEquals("%a\\\\b%", WorkoutHistoryRequest(search = "a\\b").searchPattern)
    }

    @Test
    fun pageSizeIsBoundedToTheSupportedRange() {
        WorkoutHistoryRequest(pageSize = 1)
        WorkoutHistoryRequest(pageSize = WORKOUT_HISTORY_MAX_PAGE_SIZE)
        assertFailsWith<IllegalArgumentException> { WorkoutHistoryRequest(pageSize = 0) }
        assertFailsWith<IllegalArgumentException> {
            WorkoutHistoryRequest(pageSize = WORKOUT_HISTORY_MAX_PAGE_SIZE + 1)
        }
        assertFailsWith<IllegalArgumentException> { WorkoutHistorySetRequest(pageSize = 0) }
    }

    @Test
    fun invertedDayRangeIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            WorkoutHistoryRequest(dayFrom = 5L, dayTo = 4L)
        }
        WorkoutHistoryRequest(dayFrom = 4L, dayTo = 4L)
        WorkoutHistoryRequest(dayFrom = 4L)
        WorkoutHistoryRequest(dayTo = 4L)
    }

    @Test
    fun fetchLimitReadsOneLookaheadRow() {
        assertEquals(31, WorkoutHistoryRequest(pageSize = 30).fetchLimit)
        assertEquals(3, WorkoutHistorySetRequest(pageSize = 2).fetchLimit)
    }

    @Test
    fun cursorMustBelongToItsFilter() {
        val cursor = WorkoutHistoryCursor(
            key = WorkoutHistoryKey.Session("s1"),
            timestampMillis = 100L,
            search = "squat",
            dayFrom = 1L,
            dayTo = 2L,
            utcOffsetMillis = 3_600_000L
        )
        // A matching request (after normalizing whitespace) accepts the cursor.
        filteredRequest(" squat ", 1L, 2L, 3_600_000L, cursor)
        // Any changed filter must be rejected rather than paging a different result set.
        assertFailsWith<IllegalArgumentException> {
            filteredRequest("bench", 1L, 2L, 3_600_000L, cursor)
        }
        assertFailsWith<IllegalArgumentException> {
            filteredRequest("squat", 1L, 3L, 3_600_000L, cursor)
        }
        assertFailsWith<IllegalArgumentException> {
            filteredRequest("squat", 1L, 2L, 0L, cursor)
        }
    }

    @Test
    fun unassignedSetSortKeyStaysNumericallyOrdered() {
        val nine = WorkoutHistoryKey.UnassignedSet(9L).sortKey
        val ten = WorkoutHistoryKey.UnassignedSet(10L).sortKey
        assertTrue(nine < ten, "padded set ids must compare numerically: $nine < $ten")
    }

    @Test
    fun unassignedSetSortKeyHandlesTheMaximumPositiveLongId() {
        assertEquals(
            "9223372036854775807",
            WorkoutHistoryKey.UnassignedSet(Long.MAX_VALUE).sortKey
        )
    }

    @Test
    fun sessionRanksBeforeAnUnassignedSetAtTheSameTimestamp() {
        val session = WorkoutHistoryKey.Session("s1")
        val set = WorkoutHistoryKey.UnassignedSet(1L)
        assertTrue(session.kindRank < set.kindRank)
    }

    @Test
    fun nextCursorIsNullWithoutMoreEntriesOrARow() {
        val request = WorkoutHistoryRequest()
        assertNull(WorkoutHistoryPage(entries = emptyList(), hasMore = true).nextCursor(request))
        assertNull(
            WorkoutHistoryPage(
                entries = listOf(entry(WorkoutHistoryKey.Session("s1"), 10L)),
                hasMore = false
            ).nextCursor(request)
        )
    }

    @Test
    fun nextCursorCarriesTheLastEntryAndTheExactFilter() {
        val request = WorkoutHistoryRequest(
            search = "squat",
            dayFrom = 2L,
            dayTo = 3L,
            utcOffsetMillis = 1_000L
        )
        val page = WorkoutHistoryPage(
            entries = listOf(
                entry(WorkoutHistoryKey.Session("s2"), 20L, setCount = 3),
                entry(WorkoutHistoryKey.UnassignedSet(7L), 10L, setCount = 1)
            ),
            hasMore = true
        )

        val cursor = page.nextCursor(request)!!
        assertEquals(WorkoutHistoryKey.UnassignedSet(7L), cursor.key)
        assertEquals(10L, cursor.timestampMillis)
        assertEquals(request.normalizedSearch, cursor.search)
        assertEquals(2L, cursor.dayFrom)
        assertEquals(3L, cursor.dayTo)
        assertEquals(1_000L, cursor.utcOffsetMillis)
        // The produced cursor satisfies its own request.
        request.copy(cursor = cursor)
    }

    @Test
    fun setPageCursorUsesTheLastRecordsPerformedTimeAndId() {
        val set = WorkoutHistorySet(
            id = 5L,
            exerciseId = "back-squat",
            exerciseName = "Back Squat",
            reps = 5,
            weightKg = 100.0,
            loadKind = LoadKind.EXTERNAL,
            isWarmup = false,
            rir = null,
            performedAtMillis = 42L,
            timingProvenance = WorkoutTimingProvenance.UNKNOWN
        )
        assertEquals(
            WorkoutHistorySetCursor(42L, 5L),
            WorkoutHistorySetPage(sets = listOf(set), hasMore = true)
                .nextCursor(WorkoutHistorySetRequest())
        )
        assertNull(
            WorkoutHistorySetPage(sets = listOf(set), hasMore = false)
                .nextCursor(WorkoutHistorySetRequest())
        )
    }
}
