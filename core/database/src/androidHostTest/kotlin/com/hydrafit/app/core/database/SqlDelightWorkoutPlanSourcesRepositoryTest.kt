package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.TrainingGoal
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.time.TimeProvider
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
        val goal = SqlDelightTrainingGoalRepository(database)
        val workoutLog = SqlDelightWorkoutLogRepository(database)
        val equipmentCatalog = SqlDelightEquipmentRepository(database)
        SeedEquipmentCatalog(database).seed()
        val records = SqlDelightPersonalRecordRepository(database, TimeProvider { 0L })
        val sources = SqlDelightWorkoutPlanSourcesRepository(
            equipment,
            preference,
            workoutLog,
            goal,
            equipmentCatalog,
            records
        )
        equipment.setSelected(setOf(EquipmentTag.BARBELL, EquipmentTag.BENCH))
        preference.setEngine(PlannerEngineId.GEMINI_API)
        preference.setDaysPerWeek(5)
        goal.setGoal(TrainingGoal.HYPERTROPHY)
        equipmentCatalog.setMaxWeight(EquipmentTag.CABLE_MACHINE, 100.0)
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
        assertEquals(TrainingGoal.HYPERTROPHY, result.goal)
        assertEquals(100L, result.loggedSets.single().timestampMillis)
        assertEquals(80.0, result.loggedWorkoutSets.single().weightKg)
        assertEquals(mapOf(EquipmentTag.CABLE_MACHINE to 100.0), result.equipmentMaxWeights)
    }
}
