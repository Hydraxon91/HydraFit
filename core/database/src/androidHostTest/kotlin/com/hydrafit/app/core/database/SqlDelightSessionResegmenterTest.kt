package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
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
            sessionId = sessionId
        )
    }
}
