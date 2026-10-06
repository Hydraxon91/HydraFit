package com.hydrafit.app.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.time.TimeProvider
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class DatabaseStartupMaintenanceTest {

    private val dispatcher = StandardTestDispatcher()

    @Test
    fun marksReadyOnlyAfterMaintenanceRunsAndSeedsTheCatalog() = runTest(dispatcher) {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        val database = HydraFitDatabase(driver)

        val maintenance = DatabaseStartupMaintenance(
            seedExerciseCatalog = SeedExerciseCatalog(database),
            seedEquipmentCatalog = SeedEquipmentCatalog(database),
            customExerciseDedupe = CustomExerciseDedupe(database),
            workoutSessionBackfill = WorkoutSessionBackfill(database, TimeProvider { 0L }),
            scope = CoroutineScope(dispatcher)
        )

        assertFalse(maintenance.isReady.value)

        maintenance.start()
        advanceUntilIdle()

        assertTrue(maintenance.isReady.value)
        assertTrue(database.exerciseQueries.selectAll().executeAsList().isNotEmpty())
        assertTrue(database.equipmentQueries.selectAll().executeAsList().isNotEmpty())

        driver.close()
    }
}
