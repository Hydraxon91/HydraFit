package com.hydrafit.app.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.workout.NOTE_MAX_LENGTH
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class SqlDelightSessionNoteRepositoryTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: HydraFitDatabase
    private lateinit var repository: SqlDelightSessionNoteRepository

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        database = HydraFitDatabase(driver)
        repository = SqlDelightSessionNoteRepository(database)
        database.workoutSessionQueries.insertSession("session", 10, null, 1)
    }

    @AfterTest
    fun tearDown() = driver.close()

    @Test
    fun absentSavedAndExplicitlyClearedNotesRemainDistinct() = runTest {
        assertNull(repository.note("session"))
        repository.save("session", "Keep the setup consistent")
        assertEquals("Keep the setup consistent", repository.note("session"))
        repository.clear("session")
        assertEquals("", repository.note("session"))
    }

    @Test
    fun acceptsTwoThousandCharactersAndRejectsTwoThousandAndOne() = runTest {
        val maximum = "x".repeat(NOTE_MAX_LENGTH)
        repository.save("session", maximum)
        assertEquals(maximum, repository.note("session"))
        assertFailsWith<IllegalArgumentException> {
            repository.save("session", "x".repeat(NOTE_MAX_LENGTH + 1))
        }
        assertEquals(maximum, repository.note("session"))
    }

    @Test
    fun saveAndClearNeverCreateMissingSessions() = runTest {
        assertFailsWith<IllegalArgumentException> { repository.save("missing", "note") }
        assertFailsWith<IllegalArgumentException> { repository.clear("missing") }
        assertNull(repository.note("missing"))
        assertNull(database.workoutSessionQueries.selectSessionById("missing").executeAsOneOrNull())
    }

    @Test
    fun endAndNewSessionPreservesTheOriginalNoteWithoutCopyingIt() = runTest {
        repository.save("session", "Original context")
        database.workoutSessionQueries.endSession(20, "session")
        database.workoutSessionQueries.insertSession("next", 30, null, 1)

        assertEquals("Original context", repository.note("session"))
        assertNull(repository.note("next"))
    }

    @Test
    fun databaseFailureDoesNotReplaceExistingNote() = runTest {
        repository.save("session", "before")
        driver.execute(
            null,
            "CREATE TRIGGER fail_session_note BEFORE INSERT ON sessionNote " +
                "BEGIN SELECT RAISE(ABORT, 'test failure'); END",
            0
        )
        assertFailsWith<Exception> { repository.save("session", "after") }
        assertEquals("before", repository.note("session"))
    }
}
