package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.equipment.LoadingBar
import com.hydrafit.app.core.domain.equipment.LoadingInventory
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.workout.LoadKind
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * The helper's lifecycle: one coherent profile snapshot, explicit selection, a rejected invalid
 * target, invalidation on any change and no write of any kind.
 */
class LoggerLoadingCalculatorViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun vm(profiles: LoggerLoadingProfileTestRepository) =
        LoggerLoadingCalculatorViewModel(profiles, dispatcher)

    private fun request(
        loadKind: LoadKind? = LoadKind.EXTERNAL,
        weightKg: Double? = 45.0,
        source: LoggerLoadingTargetSource = LoggerLoadingTargetSource.DRAFT,
        sourceId: String = "7",
        occurrenceId: Long? = 3L
    ) = LoggerLoadingCalculatorRequest(
        source = source,
        sourceId = sourceId,
        sourceName = "Bench Press",
        loadKind = loadKind,
        weightKg = weightKg,
        occurrenceId = occurrenceId
    )

    @Test
    fun manualOpenShowsTheProfileAndRequiresAnExplicitSelection() = runTest(dispatcher) {
        val profiles = LoggerLoadingProfileTestRepository()
        val viewModel = vm(profiles)
        runCurrent()
        viewModel.openManual("ex-1", "session-1")

        val opened = viewModel.state.value
        assertTrue(opened.open)
        assertEquals("Home", opened.profileName)
        assertEquals(WeightUnit.KG, opened.inventoryUnit)
        assertEquals(listOf("bar"), opened.options.map { it.id })
        assertEquals("", opened.targetInput)
        assertNull(opened.seed)
        assertNull(opened.selectedId)
        assertFalse(opened.canRun)
    }

    @Test
    fun aKgDraftSeedsExactNativeTicksAndALbInventoryDisclosesAnApproximation() =
        runTest(dispatcher) {
            val kgProfiles = LoggerLoadingProfileTestRepository(
                listOf(loadingProfile(1L, "Home", loadingInventory(unit = "KG")))
            )
            val kilos = vm(kgProfiles)
            runCurrent()
            kilos.openTarget(request(weightKg = 45.0))
            assertEquals("45", kilos.state.value.targetInput)
            assertNull(kilos.state.value.seed?.approximation)

            val lbProfiles = LoggerLoadingProfileTestRepository(
                listOf(loadingProfile(1L, "Home", loadingInventory(unit = "LB")))
            )
            val pounds = vm(lbProfiles)
            runCurrent()
            pounds.openTarget(request(weightKg = 45.0))
            val state = pounds.state.value
            assertEquals(WeightUnit.LB, state.inventoryUnit)
            assertEquals("99.208", state.targetInput)
            assertEquals(99_208, state.seed?.targetTicks)
            assertEquals("lb", state.seed?.approximation?.unitLabel)
            assertEquals("45", state.seed?.approximation?.prescribedKg)
        }

    @Test
    fun onlyANumericExternalOrAddedLoadSeedsTheHelper() = runTest(dispatcher) {
        val profiles = LoggerLoadingProfileTestRepository()
        val viewModel = vm(profiles)
        runCurrent()

        viewModel.openTarget(request(loadKind = LoadKind.BODYWEIGHT, weightKg = 45.0))
        assertFalse(viewModel.state.value.open)
        viewModel.openTarget(request(loadKind = LoadKind.LEGACY_UNSPECIFIED, weightKg = 45.0))
        assertFalse(viewModel.state.value.open)
        viewModel.openTarget(request(loadKind = LoadKind.EXTERNAL, weightKg = null))
        assertFalse(viewModel.state.value.open)

        viewModel.openTarget(request(loadKind = LoadKind.ADDED, weightKg = 10.0))
        assertTrue(viewModel.state.value.open)
        assertEquals(LoadKind.ADDED, viewModel.state.value.seed?.loadKind)
    }

    @Test
    fun aProfileWithoutSavedEquipmentExplainsWhereToConfigureIt() = runTest(dispatcher) {
        val profiles = LoggerLoadingProfileTestRepository(
            listOf(
                loadingProfile(
                    1L,
                    "Home",
                    LoadingInventory(bars = emptyList(), plates = emptyList())
                )
            )
        )
        val viewModel = vm(profiles)
        runCurrent()
        viewModel.openManual("ex-1", "session-1")

        val state = viewModel.state.value
        assertTrue(state.open)
        assertFalse(state.hasSavedEquipment)
        assertEquals(LoggerLoadingCalculatorFailure.NO_EQUIPMENT, state.failure)
        assertFalse(state.canRun)
        assertNull(state.result)
    }

    @Test
    fun invalidAndOutOfRangeTargetsAreRefusedWithoutRunning() = runTest(dispatcher) {
        val profiles = LoggerLoadingProfileTestRepository()
        val viewModel = vm(profiles)
        runCurrent()
        viewModel.openManual("ex-1", "session-1")
        viewModel.onEquipmentSelected("bar")

        viewModel.onTargetChanged("1.0005")
        viewModel.onRun()
        assertEquals(LoggerLoadingCalculatorFailure.INVALID_TARGET, viewModel.state.value.failure)
        assertFalse(viewModel.state.value.isRunning)
        assertNull(viewModel.state.value.result)

        viewModel.onTargetChanged("2000.001")
        viewModel.onRun()
        assertEquals(LoggerLoadingCalculatorFailure.OUT_OF_RANGE, viewModel.state.value.failure)

        viewModel.onTargetChanged("30")
        viewModel.onRun()
        runCurrent()
        assertTrue(assertNotNull(viewModel.state.value.result).isExact)
        assertNull(viewModel.state.value.failure)
    }

    @Test
    fun aRepeatSubmitWhileTheFirstCalculationRunsPublishesOneResult() = runTest(dispatcher) {
        val profiles = LoggerLoadingProfileTestRepository()
        val viewModel = vm(profiles)
        runCurrent()
        viewModel.openManual("ex-1", "session-1")
        viewModel.onEquipmentSelected("bar")
        viewModel.onTargetChanged("30")

        viewModel.onRun()
        assertTrue(viewModel.state.value.isRunning)
        viewModel.onRun()
        assertTrue(viewModel.state.value.isRunning)
        runCurrent()
        assertFalse(viewModel.state.value.isRunning)
        assertTrue(assertNotNull(viewModel.state.value.result).isExact)
    }

    @Test
    fun anInventoryEditClosesTheOpenHelper() = runTest(dispatcher) {
        val profiles = LoggerLoadingProfileTestRepository()
        val viewModel = vm(profiles)
        runCurrent()
        viewModel.openManual("ex-1", "session-1")
        assertTrue(viewModel.state.value.open)

        profiles.editInventory(
            1L,
            loadingInventory(bars = listOf(LoadingBar("bar", "Bar", 20_000)))
        )
        runCurrent()
        assertFalse(viewModel.state.value.open)
        assertNull(viewModel.state.value.result)
    }

    @Test
    fun switchingProfileAwayAndBackLeavesNothingReinterpreted() = runTest(dispatcher) {
        val profiles = LoggerLoadingProfileTestRepository(
            listOf(
                loadingProfile(1L, "Home", loadingInventory(unit = "KG")),
                loadingProfile(2L, "Gym", loadingInventory(unit = "LB"))
            )
        )
        val viewModel = vm(profiles)
        runCurrent()
        viewModel.openTarget(request(weightKg = 45.0))
        assertEquals("45", viewModel.state.value.targetInput)

        profiles.activate(2L)
        runCurrent()
        assertFalse(viewModel.state.value.open)

        viewModel.openManual("ex-1", "session-1")
        assertEquals("Gym", viewModel.state.value.profileName)
        assertEquals(WeightUnit.LB, viewModel.state.value.inventoryUnit)

        profiles.activate(1L)
        runCurrent()
        assertFalse(viewModel.state.value.open)

        profiles.activate(2L)
        runCurrent()
        assertFalse(viewModel.state.value.open)
        assertNull(viewModel.state.value.seed)
        assertNull(viewModel.state.value.result)
    }

    @Test
    fun targetSelectionAndKindChangesDropThePreviousResult() = runTest(dispatcher) {
        val profiles = LoggerLoadingProfileTestRepository(
            listOf(
                loadingProfile(
                    1L,
                    "Home",
                    loadingInventory(
                        bars = listOf(
                            LoadingBar("bar", "Bar", 20_000),
                            LoadingBar("bar2", "Second bar", 10_000)
                        )
                    )
                )
            )
        )
        val viewModel = vm(profiles)
        runCurrent()
        viewModel.openManual("ex-1", "session-1")
        viewModel.onEquipmentSelected("bar")
        viewModel.onTargetChanged("30")
        viewModel.onRun()
        runCurrent()
        assertNotNull(viewModel.state.value.result)

        viewModel.onTargetChanged("30")
        assertNull(viewModel.state.value.result)

        viewModel.onRun()
        runCurrent()
        assertNotNull(viewModel.state.value.result)
        viewModel.onEquipmentSelected("bar2")
        assertNull(viewModel.state.value.result)

        viewModel.onRun()
        runCurrent()
        assertNotNull(viewModel.state.value.result)
        viewModel.onKindChanged(LoggerLoadingCalculatorKind.DUMBBELL)
        assertNull(viewModel.state.value.result)
        assertNull(viewModel.state.value.selectedId)
        assertEquals(listOf("db"), viewModel.state.value.options.map { it.id })
    }

    @Test
    fun aLateResultAfterDismissOrAfterReopeningAnotherSourceIsNotPublished() = runTest(dispatcher) {
        val profiles = LoggerLoadingProfileTestRepository()
        val viewModel = vm(profiles)
        runCurrent()
        viewModel.openManual("ex-1", "session-1")
        viewModel.onEquipmentSelected("bar")
        viewModel.onTargetChanged("30")
        viewModel.onRun()

        viewModel.dismiss()
        runCurrent()
        assertFalse(viewModel.state.value.open)
        assertNull(viewModel.state.value.result)
        assertFalse(viewModel.state.value.isRunning)

        viewModel.openTarget(
            request(source = LoggerLoadingTargetSource.GUIDED, sourceId = "11")
        )
        assertTrue(viewModel.state.value.open)
        runCurrent()
        assertEquals("11", viewModel.state.value.seed?.sourceId)
        assertNull(viewModel.state.value.result)
    }

    @Test
    fun aMovedSourceContextClosesTheHelperInsteadOfReinterpretingIt() = runTest(dispatcher) {
        val profiles = LoggerLoadingProfileTestRepository()
        val viewModel = vm(profiles)
        runCurrent()
        viewModel.openManual("ex-1", "session-1")

        viewModel.onSourceContextChanged(
            LoggerLoadingSourceContext("ex-1", "session-1", null, emptySet())
        )
        assertTrue(viewModel.state.value.open)
        viewModel.onSourceContextChanged(
            LoggerLoadingSourceContext("ex-2", "session-1", null, emptySet())
        )
        assertFalse(viewModel.state.value.open)

        viewModel.openTarget(request(occurrenceId = 3L))
        viewModel.onSourceContextChanged(
            LoggerLoadingSourceContext(null, null, 3L, setOf("7"))
        )
        assertTrue(viewModel.state.value.open)
        viewModel.onSourceContextChanged(
            LoggerLoadingSourceContext(null, null, 4L, setOf("7"))
        )
        assertFalse(viewModel.state.value.open)

        viewModel.openTarget(request(occurrenceId = 3L))
        viewModel.onSourceContextChanged(
            LoggerLoadingSourceContext(null, null, 3L, setOf("9"))
        )
        assertFalse(viewModel.state.value.open)
    }

    @Test
    fun openingCalculatingAndClosingNeverWrites() = runTest(dispatcher) {
        val profiles = LoggerLoadingProfileTestRepository()
        val viewModel = vm(profiles)
        runCurrent()
        viewModel.openTarget(request(weightKg = 45.0))
        viewModel.onEquipmentSelected("bar")
        viewModel.onTargetChanged("45")
        viewModel.onRun()
        runCurrent()
        viewModel.dismiss()

        assertTrue(profiles.writes.isEmpty())
    }
}
