package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.schedule.OccurrenceStatus
import com.hydrafit.app.core.domain.workout.WorkoutHistoryKey
import com.hydrafit.app.core.domain.workout.WorkoutSummary
import com.hydrafit.app.core.domain.workout.WorkoutSummarySession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The summary route's only presentation decision that can mislead is where "View in history" lands:
 * the detail is keyed per session, so a workout spanning several sessions must not be given an
 * invented grouping.
 */
class WorkoutSummaryScreenTest {

    @Test
    fun opensTheDetailForAWorkoutWithOneRecordedSession() {
        val summary = summary(session("s1"))

        assertEquals(WorkoutHistoryKey.Session("s1"), workoutSummaryHistoryKey(summary))
    }

    @Test
    fun opensTheBrowserWhenTheWorkoutSpansSeveralSessions() {
        val summary = summary(session("s1"), session("s2"))

        assertNull(workoutSummaryHistoryKey(summary))
    }

    @Test
    fun opensTheBrowserWhenTheRecordedWorkHasNoSessionIdentity() {
        val summary = summary()

        assertNull(workoutSummaryHistoryKey(summary))
    }

    @Test
    fun keepsTheSingleSessionIdentityEvenAlongsideUnassignedWork() {
        // One session plus sets that belong to none still has exactly one detail to open.
        val summary = summary(session("s1"))

        assertEquals(WorkoutHistoryKey.Session("s1"), workoutSummaryHistoryKey(summary))
    }

    private fun session(id: String) = WorkoutSummarySession(
        sessionId = id,
        startedAtMillis = 1_000L,
        endedAtMillis = 2_000L,
        note = null
    )

    private fun summary(vararg sessions: WorkoutSummarySession) = WorkoutSummary(
        occurrenceId = 30L,
        workoutName = "Upper",
        status = OccurrenceStatus.FINISHED,
        startedAtMillis = 1_000L,
        resolvedAtMillis = 2_000L,
        exercises = emptyList(),
        extraSets = emptyList(),
        warmupSets = emptyList(),
        sessions = sessions.toList()
    )
}
