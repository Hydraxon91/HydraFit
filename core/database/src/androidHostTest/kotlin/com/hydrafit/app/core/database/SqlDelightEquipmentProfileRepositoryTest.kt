package com.hydrafit.app.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.equipment.EquipmentStation
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.LoadingBar
import com.hydrafit.app.core.domain.equipment.LoadingInventory
import com.hydrafit.app.core.domain.equipment.OwnedPlate
import com.hydrafit.app.core.domain.equipment.StationLoadBasis
import com.hydrafit.app.core.userdata.equipment.StaleEquipmentProfileException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class SqlDelightEquipmentProfileRepositoryTest {
    private lateinit var driver: JdbcSqliteDriver
    private lateinit var database: HydraFitDatabase
    private lateinit var repository: SqlDelightEquipmentProfileRepository

    @BeforeTest
    fun setUp() {
        driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        database = HydraFitDatabase(driver)
        SeedEquipmentCatalog(database).seed()
        repository = SqlDelightEquipmentProfileRepository(database)
    }

    @AfterTest
    fun tearDown() = driver.close()

    @Test
    fun copiesConfigurationWithIndependentIdentityAndDeletingDoesNotTouchCatalog() = runTest {
        var state = repository.current()
        repository.saveEquipment(
            state.context,
            setOf(EquipmentTag.BARBELL),
            mapOf(EquipmentTag.BARBELL to 123.5)
        )
        state = repository.current()
        val inventory = LoadingInventory(
            plates = listOf(OwnedPlate(1_250, 3)),
            bars = listOf(LoadingBar("bar", "Bar", 20_000))
        )
        repository.saveLoadingInventory(state.context, inventory)
        state = repository.current()
        val copy = repository.create("Gym", state.active.id)
        val copied = repository.current().profiles.single { it.id == copy }
        assertEquals(state.active.copy(id = copy, name = "Gym"), copied)
        repository.activate(repository.current().context, copy)
        repository.saveEquipment(repository.current().context, emptySet(), emptyMap())
        val after = repository.current()
        assertEquals(setOf(EquipmentTag.BARBELL), after.profiles.first().selected)
        assertTrue(after.active.selected.isEmpty())
        repository.delete(after.context, copy, state.active.id)
        assertEquals(state.active.id, repository.current().active.id)
        assertTrue(database.equipmentQueries.selectById("BARBELL").executeAsOne().isBuiltIn != 0L)
    }

    @Test
    fun stationCopyGetsANewIdentityAndRejectsDuplicateActiveNames() = runTest {
        val context = repository.current().context
        val originalId = repository.saveStation(
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
        val copiedProfileId = repository.create("Travel", context.profileId)
        val copiedStation = repository.current().profiles
            .single { it.id == copiedProfileId }.stations.single()
        assertTrue(copiedStation.id != originalId)
        assertEquals("Cable row A", copiedStation.name)
        assertEquals(originalId, repository.current().active.stations.single().id)
        assertFailsWith<IllegalArgumentException> {
            repository.saveStation(
                repository.current().context,
                EquipmentStation(
                    profileId = repository.current().context.profileId,
                    equipmentId = EquipmentTag.CABLE_MACHINE,
                    name = "cable row a",
                    loadBasis = StationLoadBasis.TOTAL
                )
            )
        }
        repository.delete(
            repository.current().context,
            copiedProfileId,
            repository.current().active.id
        )
        assertEquals(originalId, repository.current().active.stations.single().id)
    }

    @Test
    fun switchAwayAndBackRejectsTheOriginalEditorAndLeavesStateUntouched() = runTest {
        val second = repository.create("Travel")
        val original = repository.current()
        repository.activate(original.context, second)
        repository.activate(repository.current().context, original.active.id)
        val before = repository.current()
        assertFailsWith<StaleEquipmentProfileException> {
            repository.saveEquipment(original.context, setOf(EquipmentTag.BARBELL), emptyMap())
        }
        assertFailsWith<StaleEquipmentProfileException> {
            repository.rename(original.context, "Stale")
        }
        assertEquals(before, repository.current())
    }

    @Test
    fun invalidWritesAndDeletionGuardsAreAtomic() = runTest {
        val original = repository.current()
        assertFailsWith<IllegalArgumentException> {
            repository.delete(original.context, original.active.id)
        }
        assertFailsWith<IllegalArgumentException> {
            repository.saveEquipment(original.context, setOf(EquipmentTag("missing")), emptyMap())
        }
        assertFailsWith<IllegalArgumentException> {
            repository.saveEquipment(
                original.context,
                emptySet(),
                mapOf(EquipmentTag.BARBELL to Double.NaN)
            )
        }
        assertFailsWith<IllegalArgumentException> {
            repository.saveLoadingInventory(
                original.context,
                LoadingInventory(plates = listOf(OwnedPlate(1, 101)))
            )
        }
        assertEquals(original, repository.current())
        assertEquals(original, repository.observe().first())
        val second = repository.create("Travel")
        val state = repository.current()
        assertFailsWith<IllegalArgumentException> {
            repository.delete(state.context, state.active.id)
        }
        assertEquals(state, repository.current())
        repository.delete(state.context, second)
        assertEquals(1, repository.current().profiles.size)
    }

    @Test
    fun adaptersFollowTheActiveProfileAndStaleCeilingEditsCannotCrossProfiles() = runTest {
        val equipment = SqlDelightEquipmentRepository(database)
        val selection = SqlDelightEquipmentSelectionRepository(database)
        selection.setSelected(setOf(EquipmentTag.BARBELL))
        equipment.setMaxWeight(EquipmentTag.BARBELL, 100.0)
        val home = repository.current()
        val travel = repository.create("Travel")
        repository.activate(repository.current().context, travel)
        selection.setSelected(setOf(EquipmentTag.DUMBBELL))
        equipment.setMaxWeight(EquipmentTag.DUMBBELL, 25.0)
        assertEquals(setOf(EquipmentTag.DUMBBELL), selection.selectedFlow().first())
        assertEquals(null, equipment.all().single { it.id == EquipmentTag.BARBELL }.maxWeightKg)
        assertFailsWith<StaleEquipmentProfileException> {
            equipment.setMaxWeight(home.context, EquipmentTag.DUMBBELL, 999.0)
        }
        repository.activate(repository.current().context, home.active.id)
        assertEquals(setOf(EquipmentTag.BARBELL), selection.selected())
        assertEquals(100.0, equipment.all().single { it.id == EquipmentTag.BARBELL }.maxWeightKg)
        assertEquals(null, equipment.all().single { it.id == EquipmentTag.DUMBBELL }.maxWeightKg)
    }

    @Test
    fun deletingCustomEquipmentGuardsOtherProfilesAndProtectedReferences() = runTest {
        val equipment = SqlDelightEquipmentRepository(database)
        val custom = equipment.add("Custom rack")
        repository.saveEquipment(repository.current().context, setOf(custom.id), emptyMap())
        val other = repository.create("Copy", repository.current().active.id)
        assertFailsWith<IllegalArgumentException> { equipment.remove(custom.id) }
        repository.delete(repository.current().context, other)
        database.exerciseQueries.insertCustom(
            "custom-exercise",
            "Custom",
            custom.id.id,
            "SQUAT",
            0L,
            "EXTERNAL",
            null
        )
        assertFailsWith<IllegalArgumentException> { equipment.remove(custom.id) }
        database.exerciseQueries.deleteById("custom-exercise")
        equipment.remove(custom.id)
        assertTrue(custom.id !in repository.current().active.selected)
        assertEquals(null, database.equipmentQueries.selectById(custom.id.id).executeAsOneOrNull())
    }
}
