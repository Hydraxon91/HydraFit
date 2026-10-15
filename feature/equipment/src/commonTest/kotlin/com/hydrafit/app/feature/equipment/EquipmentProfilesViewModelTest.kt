package com.hydrafit.app.feature.equipment

import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.PersonalRecord
import com.hydrafit.app.core.domain.equipment.Equipment
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.LoadingBar
import com.hydrafit.app.core.domain.equipment.LoadingDumbbell
import com.hydrafit.app.core.domain.equipment.LoadingInventory
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.equipment.OwnedPlate
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.userdata.equipment.CustomExerciseRepository
import com.hydrafit.app.core.userdata.equipment.EquipmentRepository
import com.hydrafit.app.core.userdata.equipment.ExerciseOverrideRepository
import com.hydrafit.app.core.userdata.equipment.PersonalRecordRepository
import com.hydrafit.app.core.userdata.equipment.StaleEquipmentProfileException
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/**
 * The equipment-profile lifecycle, per-profile loading configuration and manual calculators, driven
 * through the ViewModel over a controllable in-memory profile repository.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EquipmentProfilesViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // --- Create, copy, rename ---

    @Test
    fun creatingAProfileOpensAnEmptyCreateDraftAndWritesItOnce() = runTest(dispatcher) {
        val profiles = EquipmentProfileTestRepository()
        val viewModel = viewModel(profiles)
        advanceUntilIdle()

        viewModel.onNewProfile()
        assertEquals(EquipmentProfileEditorMode.CREATE, viewModel.state.value.profileEditor.mode)
        assertNull(viewModel.state.value.profileEditor.targetProfileId)

        viewModel.onProfileNameChanged("Push day")
        viewModel.onSaveProfile()
        advanceUntilIdle()

        assertEquals(1, profiles.createCalls)
        assertNull(profiles.lastCopyFrom)
        assertFalse(viewModel.state.value.profileEditor.isOpen)
    }

    @Test
    fun copyingTheActiveProfilePassesItsIdAsTheCopySource() = runTest(dispatcher) {
        val profiles = EquipmentProfileTestRepository()
        val viewModel = viewModel(profiles)
        advanceUntilIdle()

        viewModel.onCopyActiveProfile()
        assertEquals(EquipmentProfileEditorMode.COPY, viewModel.state.value.profileEditor.mode)
        assertEquals(1L, viewModel.state.value.profileEditor.targetProfileId)

        viewModel.onProfileNameChanged("Copy of Default")
        viewModel.onSaveProfile()
        advanceUntilIdle()

        assertEquals(1L, profiles.lastCopyFrom)
    }

    @Test
    fun renamingTargetsTheActiveProfileOnly() = runTest(dispatcher) {
        val profiles = EquipmentProfileTestRepository(
            listOf(testProfile(1L, "Default"), testProfile(2L, "Travel"))
        )
        val viewModel = viewModel(profiles)
        advanceUntilIdle()

        viewModel.onRenameActiveProfile()
        assertEquals(EquipmentProfileEditorMode.RENAME, viewModel.state.value.profileEditor.mode)
        assertEquals("Default", viewModel.state.value.profileEditor.name)

        viewModel.onProfileNameChanged("Home")
        viewModel.onSaveProfile()
        advanceUntilIdle()

        assertEquals("Home", profiles.state.profiles.first { it.id == 1L }.name)
        assertEquals("Travel", profiles.state.profiles.first { it.id == 2L }.name)
    }

    @Test
    fun aBlankOrOverlongNameIsRefusedWithoutWriting() = runTest(dispatcher) {
        val profiles = EquipmentProfileTestRepository()
        val viewModel = viewModel(profiles)
        advanceUntilIdle()

        viewModel.onNewProfile()
        viewModel.onSaveProfile()
        assertEquals(
            EquipmentProfileFailure.NAME_REQUIRED,
            viewModel.state.value.profileEditor.failure
        )
        assertEquals(0, profiles.createCalls)

        viewModel.onProfileNameChanged("x".repeat(81))
        viewModel.onSaveProfile()
        assertEquals(
            EquipmentProfileFailure.NAME_TOO_LONG,
            viewModel.state.value.profileEditor.failure
        )
        assertEquals(0, profiles.createCalls)
        assertTrue(viewModel.state.value.profileEditor.isOpen)
    }

    @Test
    fun aPendingSaveBlocksASecondSubmitAndCancel() = runTest(dispatcher) {
        val profiles = EquipmentProfileTestRepository()
        val viewModel = viewModel(profiles)
        advanceUntilIdle()

        profiles.gate = CompletableDeferred()
        viewModel.onNewProfile()
        viewModel.onProfileNameChanged("Push day")
        viewModel.onSaveProfile()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.profileEditor.isSaving)
        viewModel.onSaveProfile()
        assertEquals(1, profiles.createCalls)

        viewModel.onCancelProfileEditor()
        assertTrue(viewModel.state.value.profileEditor.isOpen)

        profiles.gate?.complete(Unit)
        profiles.gate = null
        advanceUntilIdle()
        assertEquals(1, profiles.createCalls)
        assertFalse(viewModel.state.value.profileEditor.isOpen)
    }

    // --- Switching ---

    @Test
    fun switchingActivatesTheChosenProfile() = runTest(dispatcher) {
        val profiles = EquipmentProfileTestRepository(
            listOf(testProfile(1L, "Default"), testProfile(2L, "Travel"))
        )
        val viewModel = viewModel(profiles)
        advanceUntilIdle()

        viewModel.onActivateProfile(2L)
        advanceUntilIdle()

        assertEquals(1, profiles.activateCalls)
        assertEquals(2L, profiles.state.context.profileId)
        assertEquals(2L, viewModel.state.value.profile?.activeId)
        assertNull(viewModel.state.value.profileFailure)
    }

    @Test
    fun aRefusedUnlikeSwitchSurfacesAFailure() = runTest(dispatcher) {
        val profiles = EquipmentProfileTestRepository(
            listOf(testProfile(1L, "Default"), testProfile(2L, "Travel"))
        )
        profiles.activateFailure = IllegalStateException("boom")
        val viewModel = viewModel(profiles)
        advanceUntilIdle()

        viewModel.onActivateProfile(2L)
        advanceUntilIdle()

        assertEquals(EquipmentProfileFailure.FAILED, viewModel.state.value.profileFailure)
    }

    @Test
    fun aStaleSwitchSurfacesTheStaleFailureAndResyncs() = runTest(dispatcher) {
        val profiles = EquipmentProfileTestRepository(
            listOf(testProfile(1L, "Default"), testProfile(2L, "Travel"))
        )
        profiles.activateFailure = StaleEquipmentProfileException()
        val viewModel = viewModel(profiles)
        advanceUntilIdle()
        val readsBefore = profiles.currentCalls

        viewModel.onActivateProfile(2L)
        advanceUntilIdle()

        assertEquals(EquipmentProfileFailure.STALE, viewModel.state.value.profileFailure)
        assertTrue(profiles.currentCalls > readsBefore)
    }

    // --- Confirmed delete ---

    @Test
    fun deletingTheLastProfileIsRefusedBeforeConfirmation() = runTest(dispatcher) {
        val profiles = EquipmentProfileTestRepository()
        val viewModel = viewModel(profiles)
        advanceUntilIdle()

        viewModel.onRequestDeleteProfile(1L)

        assertNull(viewModel.state.value.profileDelete)
        assertEquals(EquipmentProfileFailure.LAST_PROFILE, viewModel.state.value.profileFailure)
        assertEquals(0, profiles.deleteCalls)
    }

    @Test
    fun deletingTheActiveProfileRequiresAnExplicitReplacement() = runTest(dispatcher) {
        val profiles = EquipmentProfileTestRepository(
            listOf(testProfile(1L, "Default"), testProfile(2L, "Travel"))
        )
        val viewModel = viewModel(profiles)
        advanceUntilIdle()

        viewModel.onRequestDeleteProfile(1L)
        val prompt = assertNotNull(viewModel.state.value.profileDelete)
        assertTrue(prompt.requiresReplacement)
        assertEquals(listOf(2L), prompt.candidates.map { it.id })
        assertFalse(prompt.canConfirm)

        viewModel.onConfirmDeleteProfile()
        assertEquals(0, profiles.deleteCalls)

        viewModel.onSelectReplacementProfile(2L)
        assertTrue(requireNotNull(viewModel.state.value.profileDelete).canConfirm)
        viewModel.onConfirmDeleteProfile()
        advanceUntilIdle()

        assertEquals(2L, profiles.state.profiles.single().id)
        assertEquals(2L, profiles.state.context.profileId)
        assertNull(viewModel.state.value.profileDelete)
    }

    @Test
    fun deletingAnInactiveProfileNeedsNoReplacement() = runTest(dispatcher) {
        val profiles = EquipmentProfileTestRepository(
            listOf(testProfile(1L, "Default"), testProfile(2L, "Travel"))
        )
        val viewModel = viewModel(profiles)
        advanceUntilIdle()

        viewModel.onRequestDeleteProfile(2L)
        assertFalse(viewModel.state.value.profileDelete?.requiresReplacement ?: true)
        viewModel.onConfirmDeleteProfile()
        advanceUntilIdle()

        assertEquals(listOf(1L), profiles.state.profiles.map { it.id })
        assertEquals(1L, profiles.state.context.profileId)
    }

    @Test
    fun aFailedOrStaleDeleteKeepsTheConfirmationVisible() = runTest(dispatcher) {
        val profiles = EquipmentProfileTestRepository(
            listOf(testProfile(1L, "Default"), testProfile(2L, "Travel"))
        )
        profiles.deleteFailure = IllegalStateException("boom")
        val viewModel = viewModel(profiles)
        advanceUntilIdle()

        viewModel.onRequestDeleteProfile(2L)
        viewModel.onConfirmDeleteProfile()
        advanceUntilIdle()

        val prompt = assertNotNull(viewModel.state.value.profileDelete)
        assertEquals(EquipmentProfileFailure.FAILED, prompt.failure)
        assertFalse(prompt.isDeleting)

        profiles.deleteFailure = StaleEquipmentProfileException()
        viewModel.onConfirmDeleteProfile()
        advanceUntilIdle()
        assertEquals(
            EquipmentProfileFailure.STALE,
            viewModel.state.value.profileDelete?.failure
        )
    }

    // --- Per-profile loading configuration ---

    @Test
    fun openingTheLoadingEditorCopiesStoredAmountsInTheirOwnUnit() = runTest(dispatcher) {
        val profiles = EquipmentProfileTestRepository(
            listOf(
                testProfile(
                    1L,
                    "Default",
                    loadingInventory = LoadingInventory(
                        unit = "LB",
                        plates = listOf(OwnedPlate(45_000, 2))
                    )
                )
            )
        )
        val viewModel = viewModel(profiles)
        advanceUntilIdle()

        viewModel.onOpenLoadingEditor()

        val editor = viewModel.state.value.loadingEditor
        assertTrue(editor.isOpen)
        assertEquals(LoadingUnitOption.LB, editor.unit)
        assertTrue(editor.unitLocked)
        assertEquals(listOf(OwnedPlateDraft("45", "2")), editor.plates)
    }

    @Test
    fun aPopulatedInventoryLocksItsUnit() = runTest(dispatcher) {
        val empty = EquipmentProfileTestRepository()
        val emptyViewModel = viewModel(empty)
        advanceUntilIdle()
        emptyViewModel.onOpenLoadingEditor()
        emptyViewModel.onLoadingUnitSelected(LoadingUnitOption.LB)
        assertEquals(LoadingUnitOption.LB, emptyViewModel.state.value.loadingEditor.unit)

        val populated = EquipmentProfileTestRepository(
            listOf(
                testProfile(
                    1L,
                    "Default",
                    loadingInventory = LoadingInventory(
                        plates = listOf(OwnedPlate(20_000, 2))
                    )
                )
            )
        )
        val populatedViewModel = viewModel(populated)
        advanceUntilIdle()
        populatedViewModel.onOpenLoadingEditor()
        populatedViewModel.onLoadingUnitSelected(LoadingUnitOption.LB)
        assertEquals(LoadingUnitOption.KG, populatedViewModel.state.value.loadingEditor.unit)
    }

    @Test
    fun savingTheLoadingInventoryWritesOnceAndCloses() = runTest(dispatcher) {
        val profiles = EquipmentProfileTestRepository()
        val viewModel = viewModel(profiles)
        advanceUntilIdle()

        viewModel.onOpenLoadingEditor()
        viewModel.onLoadingPlateAdded()
        viewModel.onLoadingPlateChanged(0, OwnedPlateDraft("20", "2"))
        viewModel.onSaveLoadingInventory()
        advanceUntilIdle()

        assertEquals(1, profiles.saveInventoryCalls)
        assertEquals(
            LoadingInventory(unit = "KG", plates = listOf(OwnedPlate(20_000, 2))),
            profiles.lastSavedInventory
        )
        assertFalse(viewModel.state.value.loadingEditor.isOpen)
    }

    @Test
    fun anOverpreciseAmountIsRefusedWithoutWriting() = runTest(dispatcher) {
        val profiles = EquipmentProfileTestRepository()
        val viewModel = viewModel(profiles)
        advanceUntilIdle()

        viewModel.onOpenLoadingEditor()
        viewModel.onLoadingPlateAdded()
        viewModel.onLoadingPlateChanged(0, OwnedPlateDraft("1.2345", "2"))
        viewModel.onSaveLoadingInventory()
        advanceUntilIdle()

        assertEquals(LoadingEditorFailure.PLATE_VALUE, viewModel.state.value.loadingEditor.failure)
        assertEquals(0, profiles.saveInventoryCalls)
        assertTrue(viewModel.state.value.loadingEditor.isOpen)
    }

    @Test
    fun aPendingLoadingSaveBlocksASecondSubmitAndCancel() = runTest(dispatcher) {
        val profiles = EquipmentProfileTestRepository()
        val viewModel = viewModel(profiles)
        advanceUntilIdle()

        val gate = CompletableDeferred<Unit>()
        profiles.gate = gate
        viewModel.onOpenLoadingEditor()
        viewModel.onLoadingPlateAdded()
        viewModel.onLoadingPlateChanged(0, OwnedPlateDraft("20", "2"))
        viewModel.onSaveLoadingInventory()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.loadingEditor.isSaving)
        viewModel.onSaveLoadingInventory()
        assertEquals(1, profiles.saveInventoryCalls)
        viewModel.onCancelLoadingEditor()
        assertTrue(viewModel.state.value.loadingEditor.isOpen)
        viewModel.onOpenLoadingEditor()
        assertTrue(viewModel.state.value.loadingEditor.isSaving)

        profiles.gate = null
        gate.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, profiles.saveInventoryCalls)
        assertFalse(viewModel.state.value.loadingEditor.isOpen)
    }

    @Test
    fun cancellingTheLoadingEditorDiscardsTheDraftWithoutWriting() = runTest(dispatcher) {
        val profiles = EquipmentProfileTestRepository()
        val viewModel = viewModel(profiles)
        advanceUntilIdle()

        viewModel.onOpenLoadingEditor()
        viewModel.onLoadingPlateAdded()
        viewModel.onLoadingPlateChanged(0, OwnedPlateDraft("20", "2"))
        viewModel.onCancelLoadingEditor()

        assertFalse(viewModel.state.value.loadingEditor.isOpen)
        assertEquals(0, profiles.saveInventoryCalls)
    }

    @Test
    fun aSaveRefusedAfterTheProfileChangedReopensInsteadOfRetrying() = runTest(dispatcher) {
        val profiles = EquipmentProfileTestRepository(
            listOf(testProfile(1L, "Default"), testProfile(2L, "Travel"))
        )
        val viewModel = viewModel(profiles)
        advanceUntilIdle()

        val gate = CompletableDeferred<Unit>()
        profiles.gate = gate
        viewModel.onOpenLoadingEditor()
        viewModel.onLoadingPlateAdded()
        viewModel.onLoadingPlateChanged(0, OwnedPlateDraft("20", "2"))
        viewModel.onSaveLoadingInventory()
        advanceUntilIdle()

        // Another writer switches the active profile while the captured write is still in flight.
        profiles.gate = null
        profiles.activate(profiles.state.context, 2L)
        gate.complete(Unit)
        advanceUntilIdle()

        val editor = viewModel.state.value.loadingEditor
        assertTrue(editor.staleContext)
        assertEquals(LoadingEditorFailure.STALE, editor.failure)
        assertTrue(editor.isOpen)
        assertFalse(editor.canSave)
        assertEquals(1, profiles.saveInventoryCalls)
    }

    // --- Manual calculators ---

    @Test
    fun aPlateCalculationReportsPerSideCountsAndTheFullBarWeight() = runTest(dispatcher) {
        val viewModel = calculatorViewModel()
        advanceUntilIdle()

        viewModel.onOpenLoadingCalculator()
        viewModel.onCalculatorEquipmentSelected("bar")
        viewModel.onCalculatorTargetChanged("42.5")
        viewModel.onRunLoadingCalculator()
        advanceUntilIdle()

        val result = assertNotNull(viewModel.state.value.calculator.result)
        assertTrue(result.isExact)
        val option = result.options.single()
        assertEquals(LoadingResultLabel.EXACT, option.label)
        assertEquals("42.5 KG", option.totalWeight)
        assertEquals(listOf(PlateSideLine("10 KG", 1)), option.perSidePlates)
    }

    @Test
    fun aPlateCalculationLabelsTheNearestReachableLoads() = runTest(dispatcher) {
        val viewModel = calculatorViewModel()
        advanceUntilIdle()

        viewModel.onOpenLoadingCalculator()
        viewModel.onCalculatorEquipmentSelected("bar")
        viewModel.onCalculatorTargetChanged("30")
        viewModel.onRunLoadingCalculator()
        advanceUntilIdle()

        val result = assertNotNull(viewModel.state.value.calculator.result)
        assertFalse(result.isExact)
        assertEquals(
            listOf(LoadingResultLabel.LOWER, LoadingResultLabel.UPPER),
            result.options.map { it.label }
        )
        assertEquals("22.5 KG", result.options[0].totalWeight)
        assertEquals("42.5 KG", result.options[1].totalWeight)
        assertFalse(result.missingLower)
        assertFalse(result.missingUpper)
    }

    @Test
    fun aDumbbellCalculationReportsPerDumbbellWeight() = runTest(dispatcher) {
        val viewModel = calculatorViewModel()
        advanceUntilIdle()

        viewModel.onOpenLoadingCalculator()
        viewModel.onCalculatorKindChanged(LoadingCalculatorKind.DUMBBELL)
        viewModel.onCalculatorEquipmentSelected("db")
        viewModel.onCalculatorTargetChanged("15")
        viewModel.onRunLoadingCalculator()
        advanceUntilIdle()

        val result = assertNotNull(viewModel.state.value.calculator.result)
        assertFalse(result.isExact)
        assertTrue(result.options.all { it.perDumbbell })
        assertEquals("10 KG", result.options[0].totalWeight)
        assertEquals("20 KG", result.options[1].totalWeight)
    }

    @Test
    fun anUnreachableLowerOrUpperSideIsFlagged() = runTest(dispatcher) {
        val viewModel = calculatorViewModel()
        advanceUntilIdle()

        viewModel.onOpenLoadingCalculator()
        viewModel.onCalculatorEquipmentSelected("bar")
        viewModel.onCalculatorTargetChanged("20")
        viewModel.onRunLoadingCalculator()
        advanceUntilIdle()

        val result = assertNotNull(viewModel.state.value.calculator.result)
        assertTrue(result.missingLower)
        assertFalse(result.missingUpper)
        assertEquals("22.5 KG", result.options.single().totalWeight)
    }

    @Test
    fun anInvalidTargetSurfacesATypedFailureWithoutRunningTheSolver() = runTest(dispatcher) {
        val viewModel = calculatorViewModel()
        advanceUntilIdle()

        viewModel.onOpenLoadingCalculator()
        viewModel.onCalculatorEquipmentSelected("bar")
        viewModel.onCalculatorTargetChanged("")
        viewModel.onRunLoadingCalculator()
        assertEquals(
            LoadingCalculatorFailure.INVALID_TARGET,
            viewModel.state.value.calculator.failure
        )
        assertNull(viewModel.state.value.calculator.result)

        viewModel.onCalculatorTargetChanged("3000")
        viewModel.onRunLoadingCalculator()
        assertEquals(
            LoadingCalculatorFailure.OUT_OF_RANGE,
            viewModel.state.value.calculator.failure
        )
        assertNull(viewModel.state.value.calculator.result)
    }

    @Test
    fun theSolverRunsOnTheInjectedCalculationDispatcher() = runTest(dispatcher) {
        val dispatches = RecordingDispatcher(dispatcher)
        val viewModel = calculatorViewModel(calculationDispatcher = dispatches)
        advanceUntilIdle()

        viewModel.onOpenLoadingCalculator()
        viewModel.onCalculatorEquipmentSelected("bar")
        viewModel.onCalculatorTargetChanged("42.5")
        viewModel.onRunLoadingCalculator()
        advanceUntilIdle()

        assertTrue(dispatches.dispatches > 0)
        assertNotNull(viewModel.state.value.calculator.result)
    }

    @Test
    fun anEmptyProfileCalculatesGracefullyWithoutSavedEquipment() = runTest(dispatcher) {
        val profiles = EquipmentProfileTestRepository()
        val viewModel = viewModel(profiles)
        advanceUntilIdle()

        assertEquals(
            emptyList(),
            loadingCalculatorOptions(LoadingInventory(), LoadingCalculatorKind.PLATE)
        )
        viewModel.onOpenLoadingCalculator()
        viewModel.onRunLoadingCalculator()
        advanceUntilIdle()

        assertNull(viewModel.state.value.calculator.result)
        assertNull(viewModel.state.value.calculator.failure)
    }

    @Test
    fun dismissingTheCalculatorClearsItsResult() = runTest(dispatcher) {
        val viewModel = calculatorViewModel()
        advanceUntilIdle()

        viewModel.onOpenLoadingCalculator()
        viewModel.onCalculatorEquipmentSelected("bar")
        viewModel.onCalculatorTargetChanged("42.5")
        viewModel.onRunLoadingCalculator()
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.calculator.result)

        viewModel.onDismissLoadingCalculator()
        assertFalse(viewModel.state.value.calculator.isOpen)
        assertNull(viewModel.state.value.calculator.result)
    }

    @Test
    fun changingTheTargetInvalidatesThePreviousResult() = runTest(dispatcher) {
        val viewModel = calculatorViewModel()
        advanceUntilIdle()

        viewModel.onOpenLoadingCalculator()
        viewModel.onCalculatorEquipmentSelected("bar")
        viewModel.onCalculatorTargetChanged("42.5")
        viewModel.onRunLoadingCalculator()
        advanceUntilIdle()
        assertNotNull(viewModel.state.value.calculator.result)

        viewModel.onCalculatorTargetChanged("42.6")
        assertNull(viewModel.state.value.calculator.result)
    }

    // --- Fixtures ---

    private val plateAndDumbbellInventory = LoadingInventory(
        unit = "KG",
        plates = listOf(OwnedPlate(10_000, 2)),
        bars = listOf(LoadingBar("bar", "Barbell", 20_000, collarTicks = 2_500)),
        dumbbells = listOf(LoadingDumbbell("db", "Dumbbells", fixedTicks = listOf(10_000, 20_000)))
    )

    private fun calculatorViewModel(
        calculationDispatcher: CoroutineDispatcher = dispatcher
    ): EquipmentProfilerViewModel {
        val profiles = EquipmentProfileTestRepository(
            listOf(testProfile(1L, "Default", loadingInventory = plateAndDumbbellInventory))
        )
        return viewModel(profiles, calculationDispatcher)
    }

    private fun viewModel(
        profiles: EquipmentProfileTestRepository,
        calculationDispatcher: CoroutineDispatcher = dispatcher
    ) = EquipmentProfilerViewModel(
        equipmentRepository = TestEquipmentRepository,
        profileRepository = profiles,
        exerciseCatalog = EmptyExerciseCatalog,
        exerciseOverrideRepository = NoopExerciseOverrideRepository,
        customExerciseRepository = NoopCustomExerciseRepository,
        personalRecordRepository = NoopPersonalRecordRepository,
        calculationDispatcher = calculationDispatcher
    )

    /** Counts dispatches so a test can prove the solver left the calling (UI) dispatcher. */
    private class RecordingDispatcher(private val delegate: CoroutineDispatcher) :
        CoroutineDispatcher() {
        var dispatches = 0
            private set

        override fun dispatch(context: kotlin.coroutines.CoroutineContext, block: Runnable) {
            dispatches++
            delegate.dispatch(context, block)
        }
    }

    private object TestEquipmentRepository : EquipmentRepository {
        private val items = listOf(
            Equipment(EquipmentTag.BARBELL, "Barbell", isBuiltIn = true),
            Equipment(EquipmentTag.DUMBBELL, "Dumbbells", isBuiltIn = true)
        )

        override fun observeAll(): Flow<List<Equipment>> = flowOf(items)

        override suspend fun all(): List<Equipment> = items

        override suspend fun add(name: String): Equipment = error("unused")

        override suspend fun remove(id: EquipmentTag) = error("unused")

        override suspend fun rename(
            id: EquipmentTag,
            name: String,
            maxWeightKg: Double?
        ): Equipment = error("unused")

        override suspend fun setMaxWeight(id: EquipmentTag, maxWeightKg: Double?) = Unit
    }

    private object EmptyExerciseCatalog : ExerciseCatalog {
        override suspend fun all(): List<Exercise> = emptyList()
    }

    private object NoopExerciseOverrideRepository : ExerciseOverrideRepository {
        override suspend fun update(
            exerciseId: String,
            name: String?,
            requiredEquipment: Set<EquipmentTag>,
            movementPattern: MovementPattern?,
            unilateral: Boolean?,
            loadCapability: ExerciseLoadCapability?,
            involvements: Map<MuscleGroup, Double>
        ) = Unit

        override suspend fun reset(exerciseId: String) = Unit
    }

    private object NoopCustomExerciseRepository : CustomExerciseRepository {
        override suspend fun add(
            name: String,
            requiredEquipment: Set<EquipmentTag>,
            involvements: Map<MuscleGroup, Double>,
            movementPattern: MovementPattern,
            isUnilateral: Boolean,
            loadCapability: ExerciseLoadCapability
        ): Exercise = error("unused")

        override suspend fun update(
            id: String,
            name: String,
            requiredEquipment: Set<EquipmentTag>,
            involvements: Map<MuscleGroup, Double>,
            movementPattern: MovementPattern,
            isUnilateral: Boolean,
            loadCapability: ExerciseLoadCapability
        ) = Unit

        override suspend fun delete(id: String) = Unit
    }

    private object NoopPersonalRecordRepository : PersonalRecordRepository {
        override fun observe(): Flow<List<PersonalRecord>> = flowOf(emptyList())

        override suspend fun set(record: PersonalRecord) = Unit

        override suspend fun clear(exerciseId: String) = Unit
    }
}
