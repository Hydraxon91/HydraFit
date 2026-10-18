package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest

class EquipmentProfileMigrationTest {
    private lateinit var driver: SqlDriver

    @AfterTest
    fun tearDown() {
        if (::driver.isInitialized) driver.close()
    }

    @Test
    fun migratesExistingSelectionsAndUnselectedCeilingsWithoutChangingLegacyRows() = runTest {
        driver = HistoricalDatabaseFixtures.v1()
        HydraFitDatabase.Schema.migrate(driver, 1, 49)
        val database = HydraFitDatabase(driver)
        database.equipmentQueries.insert("CUSTOM", "Custom", 0)
        database.equipmentQueries.insert("BARBELL", "Barbell", 1)
        database.equipmentQueries.updateMaxWeight(40.5, "CUSTOM")
        database.userEquipmentQueries.insertSelected("BARBELL")
        val before = database.equipmentQueries.selectAll().executeAsList()

        HydraFitDatabase.Schema.migrate(driver, 49, 50)

        val profile = SqlDelightEquipmentProfileRepository(database).current()
        assertEquals(1, profile.profiles.size)
        assertEquals(setOf("BARBELL"), profile.active.selected.map { it.id }.toSet())
        assertEquals(40.5, profile.active.maxWeightsKg.entries.single().value)
        assertEquals(before, database.equipmentQueries.selectAll().executeAsList())
        assertEquals(
            listOf("BARBELL"),
            database.userEquipmentQueries.selectAllSelected().executeAsList()
        )
    }

    @Test
    fun freshDatabaseHasOneEmptyActiveProfile() = runTest {
        driver = HistoricalDatabaseFixtures.empty()
        HydraFitDatabase.Schema.create(driver)
        val state = SqlDelightEquipmentProfileRepository(HydraFitDatabase(driver)).current()
        assertEquals(59L, HydraFitDatabase.Schema.version)
        assertEquals(1L, state.active.id)
        assertEquals(emptySet(), state.active.selected)
        assertEquals(emptyMap(), state.active.maxWeightsKg)
    }
}
