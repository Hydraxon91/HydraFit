package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.workout.WorkoutSetCorrection
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlinx.coroutines.test.runTest

class SqlDelightSessionResegmenterTest {

    private lateinit var driver: SqlDriver
    private lateinit var database: HydraFitDatabase
    private lateinit var log: SqlDelightWorkoutLogRepository
    private lateinit var sessions: SqlDelightWorkoutSessionRepository
    private lateinit var resegmenter: SqlDelightSessionResegmenter

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        database = HydraFitDatabase(driver)
        log = SqlDelightWorkoutLogRepository(database)
        sessions = SqlDelightWorkoutSessionRepository(database)
        resegmenter = SqlDelightSessionResegmenter(database)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun movesASetBetweenSeededSessionsAndRecomputesBothBounds() = runTest {
        insertSession("s1", started = 100, ended = 200, day = 0)
        insertSession("s2", started = 1000, ended = 1100, day = 0)
        insertSet(1, "s1", 100)
        insertSet(2, "s1", 150)
        insertSet(3, "s2", 1000)
        insertSet(4, "s2", 1050)

        resegmenter.resegmentAfterTimeCorrection(
            setId = 2,
            performedAtMillis = 1000,
            utcOffsetMillis = 0
        )

        val corrected = log.all().first { it.id == 2L }
        assertEquals("s2", corrected.sessionId)
        assertEquals(1000L, corrected.performedAtMillis)
        val byId = sessions.all().associateBy { it.id }
        assertEquals(100, byId.getValue("s1").startedAtMillis)
        assertEquals(100, byId.getValue("s1").endedAtMillis)
        assertEquals(1000, byId.getValue("s2").startedAtMillis)
        assertEquals(1050, byId.getValue("s2").endedAtMillis)
    }

    @Test
    fun correctsTheSameRowAndPreservesSnapshotsAndOccurrenceLinks() = runTest {
        insertSession("s1", 100, 200, 0)
        insertSession("s2", 1000, 1100, 0)
        insertSet(1, "s1", 100)
        insertSet(2, "s1", 150)
        insertSet(3, "s2", 1000)
        database.workoutLogQueries.assignOccurrence(30L, 40L, 2L)
        val before = database.workoutLogQueries.selectSetById(2L).executeAsOne()

        resegmenter.resegmentAfterSetCorrection(2L, WorkoutSetCorrection(12, 0.0, 3, 1000L), 0L)

        val after = database.workoutLogQueries.selectSetById(2L).executeAsOne()
        assertEquals(
            before.copy(reps = 12, weightKg = 0.0, rir = 3, performedAt = 1000L, sessionId = "s2"),
            after
        )
        assertEquals(3, log.all().size)
        assertEquals(100L, sessions.all().first { it.id == "s1" }.endedAtMillis)
    }

    @Test
    fun unchangedTimeKeepsSessionsAndStoredLoadMeaningIncludingAbsentAndZero() = runTest {
        insertSession("s1", 100, 200, 0)
        listOf("EXTERNAL", "ADDED", "LEGACY_UNSPECIFIED").forEachIndexed { index, kind ->
            database.workoutLogQueries.insertSetWithId(
                id = index + 1L,
                exerciseId = "exercise",
                reps = 5,
                weightKg = 20.0,
                performedAt = 150L,
                isWarmup = 1,
                involvements = "BICEPS:0.7",
                weekNumber = 2,
                cycleNumber = 3,
                dayIndex = 1,
                rir = 2,
                sessionId = "s1",
                occurrenceId = 30,
                occurrenceEntryId = 40,
                loadKind = kind
            )
        }
        val bounds = sessions.all()
        log.all().forEach { set ->
            resegmenter.resegmentAfterSetCorrection(
                set.id,
                WorkoutSetCorrection(8, null, null, 150L),
                0L
            )
            val absent = database.workoutLogQueries.selectSetById(set.id).executeAsOne()
            assertEquals(set.loadKind.name, absent.loadKind)
            assertEquals(null, absent.weightKg)
            assertEquals(null, absent.rir)
            assertEquals("BICEPS:0.7", absent.involvements)
            resegmenter.resegmentAfterSetCorrection(
                set.id,
                WorkoutSetCorrection(8, 0.0, 0, 150L),
                0L
            )
            assertEquals(0.0, log.all().first { it.id == set.id }.weightKg)
        }
        assertEquals(bounds, sessions.all())
    }

    @Test
    fun aFailureAfterResegmentationRollsBackValuesTimeAndSessions() = runTest {
        insertSession("s1", 100, 200, 0)
        insertSession("s2", 1000, 1100, 0)
        insertSet(1, "s1", 100)
        insertSet(2, "s1", 150)
        insertSet(3, "s2", 1000)
        val before = log.all()
        val bounds = sessions.all()
        driver.execute(
            null,
            "CREATE TRIGGER fail_correction BEFORE UPDATE OF reps ON workoutSet " +
                "BEGIN SELECT RAISE(ABORT, 'injected failure'); END",
            0
        )

        assertFails {
            resegmenter.resegmentAfterSetCorrection(2L, WorkoutSetCorrection(8, 10.0, 2, 1000L), 0L)
        }
        assertEquals(before, log.all())
        assertEquals(bounds, sessions.all())
    }

    @Test
    fun missingRowAndBodyweightNumericLoadAreRejectedWithoutChanges() = runTest {
        database.workoutLogQueries.insertSetWithId(
            id = 1,
            exerciseId = "exercise",
            reps = 5,
            weightKg = null,
            performedAt = 100,
            isWarmup = 0,
            involvements = null,
            weekNumber = null,
            cycleNumber = null,
            dayIndex = null,
            rir = null,
            sessionId = null,
            occurrenceId = null,
            occurrenceEntryId = null,
            loadKind = "BODYWEIGHT"
        )
        val before = log.all()
        assertFailsWith<IllegalArgumentException> {
            resegmenter.resegmentAfterSetCorrection(
                999L,
                WorkoutSetCorrection(8, null, null, 100L),
                0L
            )
        }
        assertFailsWith<IllegalArgumentException> {
            resegmenter.resegmentAfterSetCorrection(
                1L,
                WorkoutSetCorrection(8, 10.0, null, 100L),
                0L
            )
        }
        assertEquals(before, log.all())
    }

    private fun insertSession(id: String, started: Long, ended: Long?, day: Long) {
        database.workoutSessionQueries.insertSession(
            id = id,
            startedAtMillis = started,
            endedAtMillis = ended,
            localEpochDay = day
        )
    }

    private fun insertSet(id: Long, sessionId: String, performedAt: Long) {
        database.workoutLogQueries.insertSet(
            exerciseId = "exercise",
            reps = 5,
            weightKg = null,
            performedAt = performedAt,
            isWarmup = 0,
            involvements = null,
            weekNumber = null,
            cycleNumber = null,
            dayIndex = null,
            rir = null,
            sessionId = sessionId,
            loadKind = "EXTERNAL"
        )
    }
}
