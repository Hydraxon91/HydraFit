package com.hydrafit.app.core.database

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class SqlDelightEquipmentSelectionRepositoryTest {

    private lateinit var driver: SqlDriver
    private lateinit var database: HydraFitDatabase
    private lateinit var repository: SqlDelightEquipmentSelectionRepository

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        database = HydraFitDatabase(driver)
        repository = SqlDelightEquipmentSelectionRepository(database)
    }

    @AfterTest
    fun tearDown() {
        driver.close()
    }

    @Test
    fun startsEmpty() = runTest {
        assertTrue(repository.selected().isEmpty())
    }

    @Test
    fun setSelectedReplacesPreviousContents() = runTest {
        repository.setSelected(setOf(EquipmentTag.BARBELL, EquipmentTag.DUMBBELL))
        assertEquals(setOf(EquipmentTag.BARBELL, EquipmentTag.DUMBBELL), repository.selected())

        repository.setSelected(setOf(EquipmentTag.KETTLEBELL))

        assertEquals(setOf(EquipmentTag.KETTLEBELL), repository.selected())
    }

    @Test
    fun selectedFlowEmitsTheCurrentSelection() = runTest {
        assertEquals(emptySet(), repository.selectedFlow().first())

        repository.setSelected(setOf(EquipmentTag.BARBELL))

        assertEquals(setOf(EquipmentTag.BARBELL), repository.selectedFlow().first())
    }

    @Test
    fun selectionIsVisibleToAnotherInstanceOnTheSameDatabase() = runTest {
        repository.setSelected(setOf(EquipmentTag.BENCH))

        val other = SqlDelightEquipmentSelectionRepository(database)

        assertEquals(setOf(EquipmentTag.BENCH), other.selected())
    }
}
