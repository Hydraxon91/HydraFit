package com.hydrafit.app.core.domain.workout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionResegmentationTest {

    @Test
    fun keepsTheSetInItsSessionAndRecomputesItsBounds() {
        val sets = listOf(set(1, "s1", 100), set(2, "s1", 150))
        val sessions = listOf(session("s1", started = 100, ended = 200, day = 0))

        val result = resegment(sets, sessions, correctedSetId = 2, performedAtMillis = 90)

        assertTrue(result.assignments.isEmpty())
        assertEquals(
            SessionBounds(startedAtMillis = 90, endedAtMillis = 100, localEpochDay = 0),
            result.sessionBounds["s1"]
        )
    }

    @Test
    fun reassignsASetThatLandsBetweenAnotherSessionsSets() {
        val sets = listOf(
            set(1, "s1", 100),
            set(2, "s1", 150),
            set(3, "s2", 1000),
            set(4, "s2", 1050)
        )
        val sessions = listOf(
            session("s1", started = 100, ended = 200, day = 0),
            session("s2", started = 1000, ended = 1100, day = 0)
        )

        val result = resegment(sets, sessions, correctedSetId = 2, performedAtMillis = 1000)

        assertEquals(mapOf(2L to "s2"), result.assignments)
        // s1 keeps only set 1; s2 gains set 2 alongside 3 and 4.
        assertEquals(SessionBounds(100, 100, 0), result.sessionBounds["s1"])
        assertEquals(SessionBounds(1000, 1050, 0), result.sessionBounds["s2"])
    }

    @Test
    fun snapsACrossDayCorrectionToTheNearestSessionAndUpdatesItsLocalDay() {
        val sets = listOf(set(1, "s1", 100))
        val sessions = listOf(session("s1", started = 100, ended = 150, day = 0))
        val nextDay = 86_400_100L

        val result = resegment(sets, sessions, correctedSetId = 1, performedAtMillis = nextDay)

        assertTrue(result.assignments.isEmpty())
        assertEquals(SessionBounds(nextDay, nextDay, 1), result.sessionBounds["s1"])
    }

    @Test
    fun keepsAnEmptiedSessionsExistingBounds() {
        val sets = listOf(set(1, "s1", 100))
        val sessions = listOf(
            session("s1", started = 100, ended = 200, day = 0),
            session("s2", started = 1000, ended = 1100, day = 0)
        )

        val result = resegment(sets, sessions, correctedSetId = 1, performedAtMillis = 1050)

        assertEquals(mapOf(1L to "s2"), result.assignments)
        assertEquals(setOf("s2"), result.sessionBounds.keys)
        assertEquals(SessionBounds(1050, 1050, 0), result.sessionBounds["s2"])
    }

    @Test
    fun leavesAnOpenSessionOpen() {
        val sets = listOf(set(1, "s1", 100), set(2, "s1", 150))
        val sessions = listOf(session("s1", started = 100, ended = null, day = 0))

        val result = resegment(sets, sessions, correctedSetId = 2, performedAtMillis = 160)

        assertNull(result.sessionBounds["s1"]?.endedAtMillis)
    }

    @Test
    fun isANoOpWithoutSessions() {
        val sets = listOf(set(1, null, 100), set(2, null, 150))

        val result = resegment(sets, emptyList(), correctedSetId = 1, performedAtMillis = 120)

        assertTrue(result.assignments.isEmpty())
        assertTrue(result.sessionBounds.isEmpty())
    }

    private fun resegment(
        sets: List<WorkoutSet>,
        sessions: List<WorkoutSession>,
        correctedSetId: Long,
        performedAtMillis: Long
    ) = resegmentSessions(
        sets = sets,
        sessions = sessions,
        correctedSetId = correctedSetId,
        performedAtMillis = performedAtMillis,
        utcOffsetMillis = 0
    )

    private fun set(id: Long, sessionId: String?, time: Long) = WorkoutSet(
        id = id,
        exerciseId = "exercise",
        reps = 5,
        weightKg = null,
        performedAtMillis = time,
        sessionId = sessionId
    )

    private fun session(id: String, started: Long, ended: Long?, day: Long) = WorkoutSession(
        id = id,
        startedAtMillis = started,
        endedAtMillis = ended,
        localEpochDay = day
    )
}
