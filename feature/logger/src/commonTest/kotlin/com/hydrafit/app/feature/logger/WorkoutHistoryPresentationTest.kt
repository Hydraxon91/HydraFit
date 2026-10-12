package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.WorkoutHistoryEntry
import com.hydrafit.app.core.domain.workout.WorkoutHistoryKey
import com.hydrafit.app.core.domain.workout.WorkoutHistoryNameSource
import com.hydrafit.app.core.domain.workout.WorkoutHistorySet
import com.hydrafit.app.core.domain.workout.WorkoutTimingProvenance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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

    @Test
    fun aHistoryRowKeepsItsStoredIdentityLoadAndOccurrenceAttribution() {
        val recorded = WorkoutHistorySet(
            id = 12L,
            exerciseId = "bench",
            exerciseName = "Bench Press",
            reps = 5,
            weightKg = 80.0,
            loadKind = LoadKind.ADDED,
            isWarmup = true,
            rir = 3,
            performedAtMillis = 1_000L,
            timingProvenance = WorkoutTimingProvenance.CATCH_UP,
            occurrenceId = 77L
        )

        val row = recorded.toLoggedSetRow(WorkoutHistoryKey.Session("s9"))

        assertEquals(12L, row.id)
        assertEquals("bench", row.exerciseId)
        assertEquals("Bench Press", row.exerciseName)
        assertEquals(5, row.reps)
        assertEquals(80.0, row.weightKg)
        assertEquals(LoadKind.ADDED, row.loadKind)
        assertTrue(row.isWarmup)
        assertEquals(3, row.rir)
        assertEquals(1_000L, row.performedAtMillis)
        assertEquals(77L, row.occurrenceId)
        assertEquals("s9", row.sessionId)
    }

    @Test
    fun aHistoryRowFallsBackToTheExerciseIdAndCarriesNoSessionForAnUnassignedSet() {
        val recorded = WorkoutHistorySet(
            id = 5L,
            exerciseId = "bench",
            exerciseName = null,
            reps = 5,
            weightKg = null,
            loadKind = LoadKind.LEGACY_UNSPECIFIED,
            isWarmup = false,
            rir = null,
            performedAtMillis = 1_000L,
            timingProvenance = WorkoutTimingProvenance.UNKNOWN
        )

        val row = recorded.toLoggedSetRow(WorkoutHistoryKey.UnassignedSet(5L))

        assertEquals("bench", row.exerciseName)
        assertEquals(LoadKind.LEGACY_UNSPECIFIED, row.loadKind)
        assertNull(row.weightKg)
        assertNull(row.sessionId)
        assertNull(row.occurrenceId)
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
