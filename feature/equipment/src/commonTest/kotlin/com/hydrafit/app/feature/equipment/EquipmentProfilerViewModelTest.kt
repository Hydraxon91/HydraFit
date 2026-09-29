package com.hydrafit.app.feature.equipment

import com.hydrafit.app.core.domain.equipment.Equipment
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.userdata.equipment.EquipmentRepository
import com.hydrafit.app.core.userdata.equipment.EquipmentSelectionRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class EquipmentProfilerViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadsStoredSelectionAndCatalogOnCreation() = runTest(dispatcher) {
        val selection = FakeSelectionRepository(setOf(EquipmentTag.BARBELL))
        val viewModel = EquipmentProfilerViewModel(FakeEquipmentRepository(), selection)
        advanceUntilIdle()

        assertEquals(setOf(EquipmentTag.BARBELL), viewModel.state.value.selectedTags)
        assertEquals(
            listOf(EquipmentTag.BARBELL, EquipmentTag.DUMBBELL),
            viewModel.state.value.equipment.map { it.id }
        )
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun togglingAddsThenRemovesATagAndPersists() = runTest(dispatcher) {
        val selection = FakeSelectionRepository(emptySet())
        val viewModel = EquipmentProfilerViewModel(FakeEquipmentRepository(), selection)
        advanceUntilIdle()

        viewModel.onTagToggled(EquipmentTag.DUMBBELL)
        advanceUntilIdle()
        assertEquals(setOf(EquipmentTag.DUMBBELL), viewModel.state.value.selectedTags)
        assertEquals(setOf(EquipmentTag.DUMBBELL), selection.stored)

        viewModel.onTagToggled(EquipmentTag.DUMBBELL)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.selectedTags.isEmpty())
        assertTrue(selection.stored.isEmpty())
    }

    @Test
    fun addingEquipmentAddsItToTheCatalogAndClearsTheInput() = runTest(dispatcher) {
        val equipment = FakeEquipmentRepository()
        val viewModel = EquipmentProfilerViewModel(equipment, FakeSelectionRepository(emptySet()))
        advanceUntilIdle()

        viewModel.onNewEquipmentNameChanged("  Trap Bar  ")
        assertTrue(viewModel.state.value.canAdd)
        viewModel.onAddEquipment()
        advanceUntilIdle()

        assertEquals("", viewModel.state.value.newEquipmentName)
        assertTrue(viewModel.state.value.equipment.any { it.name == "Trap Bar" })
        assertEquals(listOf("Trap Bar"), equipment.added)
    }

    @Test
    fun removingEquipmentDropsItFromTheSelectionToo() = runTest(dispatcher) {
        val equipment = FakeEquipmentRepository()
        val selection = FakeSelectionRepository(setOf(EquipmentTag.BARBELL))
        val viewModel = EquipmentProfilerViewModel(equipment, selection)
        advanceUntilIdle()

        viewModel.onRemoveEquipment(EquipmentTag.BARBELL)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.selectedTags.isEmpty())
        assertTrue(selection.stored.isEmpty())
        assertEquals(listOf(EquipmentTag.BARBELL), equipment.removed)
    }

    @Test
    fun blankEquipmentNameIsNotAdded() = runTest(dispatcher) {
        val equipment = FakeEquipmentRepository()
        val viewModel = EquipmentProfilerViewModel(equipment, FakeSelectionRepository(emptySet()))
        advanceUntilIdle()

        viewModel.onNewEquipmentNameChanged("   ")
        assertFalse(viewModel.state.value.canAdd)
        viewModel.onAddEquipment()
        advanceUntilIdle()

        assertTrue(equipment.added.isEmpty())
    }

    private class FakeSelectionRepository(initial: Set<EquipmentTag>) :
        EquipmentSelectionRepository {
        var stored: Set<EquipmentTag> = initial

        override suspend fun selected(): Set<EquipmentTag> = stored

        override fun selectedFlow(): Flow<Set<EquipmentTag>> = flowOf(stored)

        override suspend fun setSelected(tags: Set<EquipmentTag>) {
            stored = tags
        }
    }

    private class FakeEquipmentRepository : EquipmentRepository {
        private val state = MutableStateFlow(
            listOf(
                Equipment(EquipmentTag.BARBELL, "Barbell", isBuiltIn = true),
                Equipment(EquipmentTag.DUMBBELL, "Dumbbells", isBuiltIn = true)
            )
        )
        val added = mutableListOf<String>()
        val removed = mutableListOf<EquipmentTag>()

        override fun observeAll(): Flow<List<Equipment>> = state.asStateFlow()

        override suspend fun all(): List<Equipment> = state.value

        override suspend fun add(name: String): Equipment {
            val created = Equipment(EquipmentTag(name.uppercase()), name, isBuiltIn = false)
            added += name
            state.value = state.value + created
            return created
        }

        override suspend fun remove(id: EquipmentTag) {
            removed += id
            state.value = state.value.filterNot { it.id == id }
        }
    }
}
