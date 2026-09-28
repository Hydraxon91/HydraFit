package com.hydrafit.app.feature.equipment

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.userdata.equipment.EquipmentSelectionRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
    fun loadsStoredSelectionOnCreation() = runTest(dispatcher) {
        val repository = FakeSelectionRepository(setOf(EquipmentTag.BARBELL))

        val viewModel = EquipmentProfilerViewModel(repository)
        advanceUntilIdle()

        assertEquals(setOf(EquipmentTag.BARBELL), viewModel.state.value.selectedTags)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun togglingAddsThenRemovesATagAndPersists() = runTest(dispatcher) {
        val repository = FakeSelectionRepository(emptySet())
        val viewModel = EquipmentProfilerViewModel(repository)
        advanceUntilIdle()

        viewModel.onTagToggled(EquipmentTag.DUMBBELL)
        advanceUntilIdle()
        assertEquals(setOf(EquipmentTag.DUMBBELL), viewModel.state.value.selectedTags)
        assertEquals(setOf(EquipmentTag.DUMBBELL), repository.stored)

        viewModel.onTagToggled(EquipmentTag.DUMBBELL)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.selectedTags.isEmpty())
        assertTrue(repository.stored.isEmpty())
    }

    @Test
    fun exposesEveryEquipmentTagAsAvailable() = runTest(dispatcher) {
        val viewModel = EquipmentProfilerViewModel(FakeSelectionRepository(emptySet()))
        advanceUntilIdle()

        assertEquals(EquipmentTag.entries.toList(), viewModel.state.value.availableTags)
    }

    private class FakeSelectionRepository(initial: Set<EquipmentTag>) :
        EquipmentSelectionRepository {
        var stored: Set<EquipmentTag> = initial

        override suspend fun selected(): Set<EquipmentTag> = stored

        override suspend fun setSelected(tags: Set<EquipmentTag>) {
            stored = tags
        }
    }
}
