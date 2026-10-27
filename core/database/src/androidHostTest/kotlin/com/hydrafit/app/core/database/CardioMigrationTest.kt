package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.cardio.CardioActivity
import com.hydrafit.app.core.domain.cardio.CardioActivityType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class CardioMigrationTest {
    @Test
    fun migrationFromPreClusterSchemaPreservesSetsAndAcceptsCardio() = runTest {
        val driver = HistoricalDatabaseFixtures.v1()
        try {
            HydraFitDatabaseSchema.migrate(driver, 1, 63)
            HistoricalDatabaseFixtures.exec(
                driver,
                "INSERT INTO workoutSet(exerciseId, reps, weightKg, performedAt, isWarmup) " +
                    "VALUES ('back-squat', 5, 100.0, 1, 0)"
            )
            HydraFitDatabaseSchema.migrate(driver, 63, HydraFitDatabaseSchema.version)
            val database = HydraFitDatabase(driver)
            val set = database.workoutLogQueries.selectAllSets().executeAsOne()
            assertEquals(5L, set.reps)
            assertEquals(100.0, set.weightKg)
            val repository = SqlDelightCardioRepository(database)
            assertTrue(repository.all().isEmpty())
            repository.add(
                CardioActivity(
                    identity = "migrated",
                    type = CardioActivityType.RUNNING,
                    startedAtMillis = 1000L,
                    durationSeconds = 600
                )
            )
            assertEquals(600, repository.all().single().durationSeconds)
            assertEquals(1, database.workoutLogQueries.selectAllSets().executeAsList().size)
        } finally {
            driver.close()
        }
    }
}
