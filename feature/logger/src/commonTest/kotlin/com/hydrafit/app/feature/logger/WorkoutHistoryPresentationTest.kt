package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.workout.WorkoutHistoryEntry
import com.hydrafit.app.core.domain.workout.WorkoutHistoryKey
import com.hydrafit.app.core.domain.workout.WorkoutHistoryNameSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WorkoutHistoryPresentationTest {

    @Test
    fun blankSearchAndNoDaysAreNotFilters() {
        assertFalse(WorkoutHistoryFilter().hasFilters)
        assertFalse(WorkoutHistoryFilter(search = "   ").hasFilters)
        assertTrue(WorkoutHistoryFilter(search = "squat").hasFilters)
        assertTrue(WorkoutHistoryFilter(dayFrom = 5L).hasFilters)
        assertTrue(WorkoutHistoryFilter(dayTo = 5L).hasFilters)
    }

    @Test
    fun anInvertedDayRangeIsDetectedAndAnEqualRangeIsNot() {
        assertFalse(WorkoutHistoryFilter(dayFrom = 5L, dayTo = 5L).hasInvalidDayRange)
        assertFalse(WorkoutHistoryFilter(dayFrom = 5L).hasInvalidDayRange)
        assertFalse(WorkoutHistoryFilter(dayTo = 5L).hasInvalidDayRange)
        assertTrue(WorkoutHistoryFilter(dayFrom = 6L, dayTo = 5L).hasInvalidDayRange)
    }

    @Test
    fun absentAndExplicitlyClearedNotesStayDistinct() {
        assertEquals(HistoryNoteState.ABSENT, noteStateOf(null))
        assertEquals(HistoryNoteState.CLEARED, noteStateOf(""))
        assertEquals(HistoryNoteState.PRESENT, noteStateOf("Felt strong"))
    }

    @Test
    fun listRowsAreKeyedByExplicitIdentity() {
        assertEquals(
            "session:s1",
            entry(WorkoutHistoryKey.Session("s1")).historyRowKey()
        )
        assertEquals(
            "set:42",
            entry(WorkoutHistoryKey.UnassignedSet(42L)).historyRowKey()
        )
    }

    @Test
    fun twoSessionsOnTheSameDayKeepDifferentListKeys() {
        val first = entry(WorkoutHistoryKey.Session("a")).historyRowKey()
        val second = entry(WorkoutHistoryKey.Session("b")).historyRowKey()
        assertTrue(first != second)
    }

    @Test
    fun aNotedSessionWithNoRecordedSetsStillHasAnEntry() {
        val noted = entry(
            WorkoutHistoryKey.Session("empty-noted"),
            note = "",
            recordedSetCount = 0
        )
        assertEquals(HistoryNoteState.CLEARED, noted.noteState())
        assertEquals(WorkoutHistoryNameSource.NONE, noted.nameSource)
    }

    private fun entry(key: WorkoutHistoryKey, note: String? = null, recordedSetCount: Int = 1) =
        WorkoutHistoryEntry(
            key = key,
            timestampMillis = 1_000L,
            localEpochDay = 0L,
            sessionMissing = false,
            note = note,
            recordedSetCount = recordedSetCount,
            nameSource = if (recordedSetCount > 0) {
                WorkoutHistoryNameSource.CURRENT_CATALOG
            } else {
                WorkoutHistoryNameSource.NONE
            }
        )
}
