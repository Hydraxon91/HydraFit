package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class SqlDelightEquipmentRepositoryTest {

    private lateinit var driver: SqlDriver
    private lateinit var database: HydraFitDatabase
    private lateinit var repository: SqlDelightEquipmentRepository

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        database = HydraFitDatabase(driver)
        repository = SqlDelightEquipmentRepository(database)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun addingNamesThatNormalizeToTheSameIdKeepsBoth() = runTest {
        val first = repository.add("Lat Pulldown")
        val second = repository.add("Lat-Pulldown")

        assertTrue(first.id != second.id)
        assertEquals(setOf(first.id, second.id), repository.all().map { it.id }.toSet())
        assertEquals(2, repository.all().size)
    }

    @Test
    fun removingCustomEquipmentDeletesItsStationsAndKeepsRecordedAttribution() = runTest {
        driver.execute(null, "PRAGMA foreign_keys = ON", 0)
        SeedExerciseCatalog(database).seed()
        val equipment = repository.add("Rack")
        val profiles = SqlDelightEquipmentProfileRepository(database)
        val secondProfile = profiles.create("Other", copyFrom = null)
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO equipmentStation(profileId, equipmentId, name, loadBasis) VALUES " +
                "(1, '${equipment.id.id}', 'Half rack', 'TOTAL'), " +
                "($secondProfile, '${equipment.id.id}', 'Rack 2', 'PER_HAND')"
        )
        val stationId = HistoricalDatabaseFixtures.long(
            driver,
            "SELECT MIN(id) FROM equipmentStation"
        )
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO workoutSet(" +
                "exerciseId, reps, weightKg, performedAt, isWarmup, loadKind, " +
                "stationId, stationName, stationLoadBasis" +
                ") VALUES ('back-squat', 5, 100.0, 1, 0, 'EXTERNAL', $stationId, " +
                "'Half rack', 'TOTAL')"
        )

        repository.remove(equipment.id)

        assertEquals(0L, HistoricalDatabaseFixtures.count(driver, "equipmentStation"))
        assertEquals(
            0L,
            HistoricalDatabaseFixtures.count(
                driver,
                "equipment WHERE id = '${equipment.id.id}'"
            )
        )
        val set = database.workoutLogQueries.selectAllSets().executeAsOne()
        assertEquals(stationId, set.stationId)
        assertEquals("Half rack", set.stationName)
        assertEquals("TOTAL", set.stationLoadBasis)
        assertEquals(
            0L,
            HistoricalDatabaseFixtures.long(driver, "SELECT COUNT(*) FROM pragma_foreign_key_check")
        )
    }
}
