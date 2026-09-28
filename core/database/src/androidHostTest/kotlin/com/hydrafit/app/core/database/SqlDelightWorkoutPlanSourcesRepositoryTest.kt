package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.workout.WorkoutSet
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class SqlDelightWorkoutPlanSourcesRepositoryTest {

    private lateinit var driver: SqlDriver
    private lateinit var database: HydraFitDatabase

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        database = HydraFitDatabase(driver)
        SeedExerciseCatalog(database).seed()
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun combinesCurrentEquipmentEngineScheduleAndLoggedSets() = runTest {
        val equipment = SqlDelightEquipmentSelectionRepository(database)
        val preference = SqlDelightEnginePreferenceRepository(database)
        val workoutLog = SqlDelightWorkoutLogRepository(database)
        val sources = SqlDelightWorkoutPlanSourcesRepository(equipment, preference, workoutLog)
        equipment.setSelected(setOf(EquipmentTag.BARBELL, EquipmentTag.BENCH))
        preference.setEngine(PlannerEngineId.GEMINI_API)
        preference.setDaysPerWeek(5)
        workoutLog.add(
            WorkoutSet(
                exerciseId = "barbell-bench-press",
                reps = 5,
                weightKg = 80.0,
                performedAtMillis = 100L
            )
        )

        val result = sources.observe().first()

        assertEquals(setOf(EquipmentTag.BARBELL, EquipmentTag.BENCH), result.availableEquipment)
        assertEquals(PlannerEngineId.GEMINI_API, result.selectedEngine)
        assertEquals(5, result.daysPerWeek)
        assertEquals(100L, result.loggedSets.single().timestampMillis)
    }
}
