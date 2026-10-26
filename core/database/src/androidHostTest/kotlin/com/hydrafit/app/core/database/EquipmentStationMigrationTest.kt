package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import com.hydrafit.app.core.domain.equipment.EquipmentStation
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.StationAttribution
import com.hydrafit.app.core.domain.equipment.StationLoadBasis
import com.hydrafit.app.core.domain.workout.WorkoutSet
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest

class EquipmentStationMigrationTest {
    private lateinit var driver: SqlDriver

    @AfterTest
    fun tearDown() {
        if (::driver.isInitialized) driver.close()
    }

    @Test
    fun migrationAddsStationTableAndNullableSetColumnsWithoutBackfilling() = runTest {
        driver = HistoricalDatabaseFixtures.v1()
        HydraFitDatabase.Schema.migrate(driver, 1, 60)
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO exercise(id,name,requiredEquipment,movementPattern,loadCapability) " +
                "VALUES ('cable-row','Cable Row','CABLE_MACHINE','HORIZONTAL_PULL','EXTERNAL')"
        )
        HistoricalDatabaseFixtures.exec(
            driver,
            "INSERT INTO workoutSet(exerciseId,reps,weightKg,performedAt,loadKind) " +
                "VALUES ('cable-row',10,70.0,100,'EXTERNAL')"
        )

        HydraFitDatabase.Schema.migrate(driver, 60, HydraFitDatabase.Schema.version)

        assertEquals(64L, HydraFitDatabase.Schema.version)
        assertNull(HistoricalDatabaseFixtures.text(driver, "SELECT stationId FROM workoutSet"))
        assertNull(HistoricalDatabaseFixtures.text(driver, "SELECT stationName FROM workoutSet"))
        assertNull(
            HistoricalDatabaseFixtures.text(driver, "SELECT stationLoadBasis FROM workoutSet")
        )
        assertEquals(
            1L,
            HistoricalDatabaseFixtures.long(
                driver,
                "SELECT count(*) FROM sqlite_master WHERE type='table' " +
                    "AND name='equipmentStation'"
            )
        )

        val database = HydraFitDatabase(driver)
        val profileRepository = SqlDelightEquipmentProfileRepository(database)
        val context = profileRepository.current().context
        val stationId = profileRepository.saveStation(
            context,
            EquipmentStation(
                profileId = context.profileId,
                equipmentId = EquipmentTag.CABLE_MACHINE,
                name = "Cable row A",
                loadBasis = StationLoadBasis.TOTAL,
                minWeightKg = 5.0,
                maxWeightKg = 120.0,
                incrementKg = 2.5
            )
        )
        val stored = profileRepository.current().active.stations.single()
        assertEquals(stationId, stored.id)
        assertEquals("Cable row A", stored.name)
        assertEquals(StationLoadBasis.TOTAL, stored.loadBasis)

        val logRepository = SqlDelightWorkoutLogRepository(database)
        logRepository.add(
            WorkoutSet(
                exerciseId = "cable-row",
                reps = 10,
                weightKg = 70.0,
                performedAtMillis = 200L,
                station = StationAttribution(stationId, "Cable row A", StationLoadBasis.TOTAL)
            )
        )
        val recorded = logRepository.all().single { it.performedAtMillis == 200L }
        assertEquals(
            StationAttribution(stationId, "Cable row A", StationLoadBasis.TOTAL),
            recorded.station
        )
    }
}
