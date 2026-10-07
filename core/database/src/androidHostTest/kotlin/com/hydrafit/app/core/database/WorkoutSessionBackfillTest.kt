package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.fatigue.FatigueConfig
import com.hydrafit.app.core.domain.time.TimeProvider
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class WorkoutSessionBackfillTest {

    private val gap = FatigueConfig().sessionGap.inWholeMilliseconds

    private lateinit var driver: SqlDriver
    private lateinit var database: HydraFitDatabase
    private var offsetMillis = 0L

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        database = HydraFitDatabase(driver)
        offsetMillis = 0L
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun emptyDatabaseCreatesNothing() {
        backfill()

        assertTrue(sessions().isEmpty())
        assertTrue(allSets().isEmpty())
    }

    @Test
    fun aSingleSessionIsClosedAndCarriesTheDeterministicId() {
        val firstId = insert(0)
        insert(1_000)

        backfill()

        val session = sessions().single()
        assertEquals("backfill-$firstId", session.id)
        assertEquals(0L, session.startedAtMillis)
        assertEquals(1_000L, session.endedAtMillis)
        assertEquals(0L, session.localEpochDay)
        assertTrue(allSets().all { it.sessionId == session.id })
    }

    @Test
    fun aGapJustUnderTheThresholdStaysOneSession() {
        insert(0)
        insert(gap - 1)

        backfill()

        assertEquals(1, sessions().size)
    }

    @Test
    fun aGapExactlyAtTheThresholdStartsANewSession() {
        insert(0)
        insert(gap)

        backfill()

        assertEquals(2, sessions().size)
    }

    @Test
    fun equalTimestampsShareASession() {
        insert(1_000)
        insert(1_000)
        insert(1_000)

        backfill()

        assertEquals(1, sessions().size)
        assertEquals(3, allSets().count { it.sessionId != null })
    }

    @Test
    fun aSessionSpanningLocalMidnightStaysOneSession() {
        insert(86_399_000)
        insert(86_401_000)

        backfill()

        assertEquals(1, sessions().size)
        assertEquals(0L, sessions().single().localEpochDay)
    }

    @Test
    fun localEpochDayUsesTheBackfillOffset() {
        offsetMillis = 7_200_000L
        insert(86_399_000)

        backfill()

        assertEquals(1L, sessions().single().localEpochDay)
    }

    @Test
    fun warmupIsTheEarliestMemberAndAnchorsTheSession() {
        insert(10_000_000)
        val warmupId = insert(9_999_000, isWarmup = true)

        backfill()

        val session = sessions().single()
        assertEquals("backfill-$warmupId", session.id)
        assertEquals(9_999_000L, session.startedAtMillis)
        assertEquals(10_000_000L, session.endedAtMillis)
        assertEquals(session.id, allSets().single { it.performedAt == 9_999_000L }.sessionId)
    }

    @Test
    fun trailingWarmupAttachesToThePrecedingSession() {
        insert(10_000_000)
        insert(10_001_000, isWarmup = true)

        backfill()

        val session = sessions().single()
        assertEquals(session.id, allSets().single { it.performedAt == 10_001_000L }.sessionId)
        assertEquals(10_001_000L, session.endedAtMillis)
    }

    @Test
    fun isolatedWarmupGetsItsOwnClosedSession() {
        insert(0, isWarmup = true)
        insert(10_000_000)

        backfill()

        assertEquals(2, sessions().size)
        val warmupSession = sessions().single { it.startedAtMillis == 0L }
        assertEquals(0L, warmupSession.endedAtMillis)
        assertEquals(warmupSession.id, allSets().single { it.performedAt == 0L }.sessionId)
    }

    @Test
    fun warmupsWithNoWorkingSetGroupByTheSameGapRule() {
        insert(0, isWarmup = true)
        insert(1_000, isWarmup = true)
        insert(10_000_000, isWarmup = true)

        backfill()

        assertEquals(2, sessions().size)
        assertTrue(allSets().all { it.sessionId != null })
    }

    @Test
    fun rowsWithAnExistingSessionIdAreNeverTouched() {
        insert(0, sessionId = "live-1")
        insert(1_000)

        backfill()

        assertEquals("live-1", allSets().single { it.performedAt == 0L }.sessionId)
        assertTrue(
            allSets().single { it.performedAt == 1_000L }.sessionId!!.startsWith("backfill-")
        )
    }

    @Test
    fun runningTwiceChangesNothing() {
        insert(0)
        insert(1_000)
        insert(20_000_000)

        backfill()
        val firstSetSessions = allSets().map { it.id to it.sessionId }
        val firstSessions = sessions().map { sessionKey(it) }

        backfill()

        assertEquals(firstSetSessions, allSets().map { it.id to it.sessionId })
        assertEquals(firstSessions, sessions().map { sessionKey(it) })
    }

    @Test
    fun aFailureMidBackfillRollsBackEveryWrite() {
        insert(0)
        val secondId = insert(20_000_000)
        database.workoutSessionQueries.insertSession(
            id = "backfill-$secondId",
            startedAtMillis = 0L,
            endedAtMillis = 0L,
            localEpochDay = 0L
        )

        assertFails { backfill() }

        assertTrue(allSets().all { it.sessionId == null })
        val remaining = sessions().single()
        assertEquals("backfill-$secondId", remaining.id)
    }

    @Test
    fun theReplayFixtureBackfillsIntoSameTwoSessionsAsTheCalculator() {
        // Mirrors FatigueReplayFixture.kt (39 working sets + 3 warm-ups). The counts below fail
        // loudly if this copy drifts from the fixture; the core:domain replay test locks the
        // calculator's figures (isolation 82.5504/65.2960, typed 83.1065/68.4753).
        REPLAY_WORKING.forEach { insert(it) }
        REPLAY_WARMUPS.forEach { insert(it, isWarmup = true) }

        assertEquals(39, allSets().count { it.isWarmup == 0L })
        assertEquals(3, allSets().count { it.isWarmup == 1L })

        backfill()

        val sessions = sessions().sortedBy { it.startedAtMillis }
        assertEquals(2, sessions.size)
        // Session A is the first block of 8 working sets (no warm-ups).
        assertEquals(1790787141874L, sessions[0].startedAtMillis)
        assertEquals(1790787542344L, sessions[0].endedAtMillis)
        // Session B is anchored at its first warm-up, which is its earliest assigned set.
        assertEquals(1790839959646L, sessions[1].startedAtMillis)
        assertEquals(1790844708670L, sessions[1].endedAtMillis)

        val bySession = allSets().groupBy { it.sessionId }
        assertEquals(8, bySession.getValue(sessions[0].id).count { it.isWarmup == 0L })
        assertEquals(0, bySession.getValue(sessions[0].id).count { it.isWarmup == 1L })
        assertEquals(31, bySession.getValue(sessions[1].id).count { it.isWarmup == 0L })
        assertEquals(3, bySession.getValue(sessions[1].id).count { it.isWarmup == 1L })
        assertEquals(42, bySession.values.sumOf { it.size })
        assertTrue(allSets().all { it.sessionId != null })
    }

    @Test
    fun everyBackfilledSessionEndsAtItsLatestAssignedSet() {
        insert(0)
        insert(1_000)
        insert(20_000_000)

        backfill()

        sessions().forEach { session ->
            val latest = allSets().filter { it.sessionId == session.id }.maxOf { it.performedAt }
            assertNotNull(session.endedAtMillis)
            assertEquals(latest, session.endedAtMillis)
        }
    }

    private fun backfill() =
        WorkoutSessionBackfill(database, TestTimeProvider(offsetMillis)).backfill()

    private fun insert(
        performedAt: Long,
        isWarmup: Boolean = false,
        sessionId: String? = null
    ): Long {
        database.workoutLogQueries.insertSet(
            exerciseId = "e",
            reps = 1L,
            weightKg = null,
            performedAt = performedAt,
            isWarmup = if (isWarmup) 1L else 0L,
            involvements = null,
            weekNumber = null,
            cycleNumber = null,
            dayIndex = null,
            rir = null,
            sessionId = sessionId,
            loadKind = "EXTERNAL"
        )
        return allSets().filter { it.performedAt == performedAt && it.sessionId == sessionId }
            .maxOf { it.id }
    }

    private fun allSets() = database.workoutLogQueries.selectAllSets().executeAsList()

    private fun sessions() = database.workoutSessionQueries.selectAllSessions().executeAsList()

    private fun sessionKey(session: WorkoutSession) =
        Triple(session.id, session.startedAtMillis, session.endedAtMillis)

    private class TestTimeProvider(private val offsetMillis: Long) : TimeProvider {
        override fun nowMillis(): Long = 0L
        override fun utcOffsetMillis(): Long = offsetMillis
    }

    private companion object {
        val REPLAY_WORKING = listOf(
            1790787141874, 1790787142631, 1790787143031, 1790787143323,
            1790787537730, 1790787538221, 1790787538587, 1790787542344,
            1790840075113, 1790840172676, 1790840544775, 1790840847553, 1790840853110,
            1790840961215, 1790840966189, 1790841004989, 1790841188246, 1790841471413,
            1790841949887, 1790841975016, 1790842215182, 1790842342522, 1790842517018,
            1790842775294, 1790842907722, 1790842910996, 1790842916068, 1790842921428,
            1790843013812, 1790843343214, 1790843811620, 1790843906097, 1790843910347,
            1790844220908, 1790844225083, 1790844413048, 1790844419279, 1790844557801,
            1790844708670
        )

        val REPLAY_WARMUPS = listOf(1790839959646, 1790840464767, 1790842632912)
    }
}
