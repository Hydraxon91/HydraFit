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

class SqlDelightExerciseNoteRepositoryTest {

    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: HydraFitDatabase
    private lateinit var repository: SqlDelightExerciseNoteRepository

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        database = HydraFitDatabase(driver)
        SeedExerciseCatalog(database).seed()
        repository = SqlDelightExerciseNoteRepository(database)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun absentNoteReadsAsNull() = runTest {
        assertNull(repository.note("back-squat"))
    }

    @Test
    fun savedNoteSurvivesARead() = runTest {
        repository.save("back-squat", "Brace hard, elbows under the bar")

        assertEquals("Brace hard, elbows under the bar", repository.note("back-squat"))
    }

    @Test
    fun savingReplacesTheExistingNote() = runTest {
        repository.save("back-squat", "first")
        repository.save("back-squat", "second")

        assertEquals("second", repository.note("back-squat"))
    }

    @Test
    fun anExplicitClearIsStoredAndDistinctFromAbsence() = runTest {
        repository.save("back-squat", "first")
        repository.clear("back-squat")

        // The cleared note is an explicit empty value, not a missing row.
        assertEquals("", repository.note("back-squat"))
        assertNull(repository.note("barbell-bench-press"))
    }

    @Test
    fun clearOnAnAbsentNotePersistsAnEmptyNote() = runTest {
        repository.clear("back-squat")

        assertEquals("", repository.note("back-squat"))
    }

    @Test
    fun saveAcceptsTheMaximumLengthAndRejectsLongerTextWithoutReplacingTheStoredNote() = runTest {
        val maximum = "x".repeat(NOTE_MAX_LENGTH)
        repository.save("back-squat", maximum)

        assertEquals(maximum, repository.note("back-squat"))
        assertFailsWith<IllegalArgumentException> {
            repository.save("back-squat", "y".repeat(maximum.length + 1))
        }
        assertEquals(maximum, repository.note("back-squat"))
    }

    @Test
    fun saveAndClearRejectUnknownOrDeletedExerciseIds() = runTest {
        assertFailsWith<IllegalArgumentException> {
            repository.save("missing-exercise", "note")
        }
        assertFailsWith<IllegalArgumentException> {
            repository.clear("missing-exercise")
        }

        assertNull(repository.note("missing-exercise"))
    }
}
