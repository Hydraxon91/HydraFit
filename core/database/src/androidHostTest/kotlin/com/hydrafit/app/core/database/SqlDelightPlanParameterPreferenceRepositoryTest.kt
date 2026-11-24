package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.engine.AutomaticSetPrescription
import com.hydrafit.app.core.domain.engine.SessionLength
import com.hydrafit.app.core.domain.engine.SetPrescriptionShape
import com.hydrafit.app.core.userdata.settings.PlanParameterPreferences
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

/**
 * Migration 66 -> 67 adds the split builder's plan parameters to `plannerEngine`; each is NULL
 * ("not chosen", so the goal default applies) until the user picks one.
 */
class SqlDelightPlanParameterPreferenceRepositoryTest {

    private lateinit var driver: SqlDriver

    @AfterTest
    fun tearDown() {
        if (::driver.isInitialized) driver.close()
    }

    private fun freshRepository(): SqlDelightPlanParameterPreferenceRepository {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        return SqlDelightPlanParameterPreferenceRepository(HydraFitDatabase(driver))
    }

    @Test
    fun nothingIsChosenOnAFreshInstall() = runTest {
        assertEquals(PlanParameterPreferences(), freshRepository().current())
    }

    @Test
    fun choicesSurviveAndCanBeClearedAgain() = runTest {
        val repository = freshRepository()
        val automatic = AutomaticSetPrescription(
            shape = SetPrescriptionShape.ASCENDING_LOAD,
            minReps = 6,
            maxReps = 10,
            targetRir = 2
        )
        repository.setSetsPerExercise(5)
        repository.setAccessorySetsPerExercise(2)
        repository.setAutomaticSetPrescription(automatic)
        assertEquals(PlanParameterPreferences(5, 2, automatic), repository.current())

        repository.setAutomaticSetPrescription(null)
        repository.setSetsPerExercise(null)
        assertEquals(PlanParameterPreferences(null, 2, null), repository.current())
    }

    @Test
    fun aStoredTargetThatNoLongerValidatesReadsAsNotChosen() = runTest {
        val repository = freshRepository()
        repository.setSetsPerExercise(3)
        HistoricalDatabaseFixtures.exec(
            driver,
            "UPDATE plannerEngine SET autoTargetShape = 'STRAIGHT', autoTargetMinReps = 12, " +
                "autoTargetMaxReps = 6 WHERE id = 0"
        )
        assertEquals(PlanParameterPreferences(setsPerExercise = 3), repository.current())
    }

    @Test
    fun theSessionLengthIsRememberedAndCanBeClearedAgain() = runTest {
        val repository = freshRepository()

        repository.setSessionLength(SessionLength.LONGER)
        assertEquals(SessionLength.LONGER, repository.current().sessionLength)

        repository.setSessionLength(null)
        assertEquals(null, repository.current().sessionLength)
    }

    @Test
    fun aStoredSessionLengthThatIsNoLongerKnownReadsAsNotChosen() = runTest {
        val repository = freshRepository()
        repository.setSetsPerExercise(4)
        HistoricalDatabaseFixtures.exec(
            driver,
            "UPDATE plannerEngine SET sessionLength = 'MARATHON' WHERE id = 0"
        )

        assertEquals(PlanParameterPreferences(setsPerExercise = 4), repository.current())
    }

    @Test
    fun migration68AddsTheSessionLengthColumnAndKeepsTheChoices() = runTest {
        driver = HistoricalDatabaseFixtures.v1()
        // Schema 68 is the state after migration 67, which added the other plan parameters.
        HydraFitDatabaseSchema.migrate(driver, 1, 68)
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT OR REPLACE INTO plannerEngine(id, engineId, setsPerExercise) " +
                "VALUES (0, 'DETERMINISTIC', 5)"
        )

        HydraFitDatabaseSchema.migrate(driver, 68, HydraFitDatabaseSchema.version)

        assertEquals(
            PlanParameterPreferences(setsPerExercise = 5),
            SqlDelightPlanParameterPreferenceRepository(HydraFitDatabase(driver)).current()
        )
    }

    @Test
    fun migrationKeepsTheExistingPreferenceRowAndChoosesNothing() = runTest {
        driver = HistoricalDatabaseFixtures.v1()
        HydraFitDatabaseSchema.migrate(driver, 1, 66)
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT OR REPLACE INTO plannerEngine(id, engineId, daysPerWeek) " +
                "VALUES (0, 'DETERMINISTIC', 5)"
        )

        HydraFitDatabaseSchema.migrate(driver, 66, HydraFitDatabaseSchema.version)

        val database = HydraFitDatabase(driver)
        assertEquals(5L, database.plannerEngineQueries.selectDaysPerWeek().executeAsOne())
        assertEquals(
            PlanParameterPreferences(),
            SqlDelightPlanParameterPreferenceRepository(database).current()
        )
    }
}
