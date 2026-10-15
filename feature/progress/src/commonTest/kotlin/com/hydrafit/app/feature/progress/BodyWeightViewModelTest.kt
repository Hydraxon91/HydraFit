package com.hydrafit.app.feature.progress

import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.time.localEpochDay
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.userdata.measurements.BodyWeightException
import com.hydrafit.app.core.userdata.measurements.BodyWeightFailureReason
import com.hydrafit.app.core.userdata.measurements.BodyWeightMeasurement
import com.hydrafit.app.core.userdata.measurements.BodyWeightRepository
import com.hydrafit.app.core.userdata.settings.WeightUnitRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class BodyWeightViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val nowMillis = 1_700_000_000_000L
    private val offsetMillis = 3_600_000L
    private val today = localEpochDay(nowMillis, offsetMillis)

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── Load ──────────────────────────────────────────────────────────────

    @Test
    fun loadingClearsOnlyOnceBothFlowsEmit() = runTest(dispatcher) {
        val repository = FakeBodyWeightRepository(
            initial = listOf(BodyWeightMeasurement(today, 80.0)),
            initialGoal = 75.0
        )
        val viewModel = viewModel(repository)
        assertTrue(viewModel.state.value.loading)

        advanceUntilIdle()

        assertFalse(viewModel.state.value.loading)
        assertEquals(listOf(BodyWeightMeasurement(today, 80.0)), viewModel.state.value.measurements)
        assertEquals(75.0, viewModel.state.value.goalKg)
    }

    @Test
    fun anEmptyHistoryIsReportedAsEmptyNotAsAMissingChart() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.loading)
        assertTrue(viewModel.state.value.isEmpty)
        assertTrue(viewModel.state.value.measurementsInRange.isEmpty())
    }

    @Test
    fun aFailedLoadShowsTheErrorAndRetryResubscribes() = runTest(dispatcher) {
        val repository = FakeBodyWeightRepository()
        repository.measurementsError = true
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.loadFailed)
        assertFalse(viewModel.state.value.loading)

        repository.measurementsError = false
        repository.measurementsFlow.value = listOf(BodyWeightMeasurement(today, 81.0))
        viewModel.onRetryLoad()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.loadFailed)
        assertEquals(listOf(BodyWeightMeasurement(today, 81.0)), viewModel.state.value.measurements)
    }

    // ── Create ────────────────────────────────────────────────────────────

    @Test
    fun aNewMeasurementDefaultsToTodayAndWritesTheEnteredKilograms() = runTest(dispatcher) {
        val repository = FakeBodyWeightRepository()
        val viewModel =
            viewModel(repository, unitRepository = FakeWeightUnitRepository(WeightUnit.KG))
        advanceUntilIdle()

        viewModel.onAddMeasurement()
        assertEquals(progressDayLabel(today), viewModel.state.value.editor?.dateText)
        viewModel.onEditorWeightChanged("80")
        viewModel.onEditorSubmit()
        advanceUntilIdle()

        assertEquals(listOf(AddCall(today, 80.0, false)), repository.addCalls)
        assertNull(viewModel.state.value.editor)
    }

    @Test
    fun aPoundEntryIsConvertedAtTheEdge() = runTest(dispatcher) {
        val repository = FakeBodyWeightRepository()
        val viewModel =
            viewModel(repository, unitRepository = FakeWeightUnitRepository(WeightUnit.LB))
        advanceUntilIdle()

        viewModel.onAddMeasurement()
        viewModel.onEditorWeightChanged("220")
        viewModel.onEditorSubmit()
        advanceUntilIdle()

        assertEquals(220.0 / WeightUnit.POUNDS_PER_KILOGRAM, repository.addCalls.single().weightKg)
    }

    @Test
    fun anInvalidWeightIsRejectedWithoutWriting() = runTest(dispatcher) {
        val repository = FakeBodyWeightRepository()
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        viewModel.onAddMeasurement()

        viewModel.onEditorWeightChanged("0")
        viewModel.onEditorSubmit()
        assertEquals(BodyWeightEditorError.INVALID_WEIGHT, viewModel.state.value.editor?.error)

        viewModel.onEditorWeightChanged("abc")
        viewModel.onEditorSubmit()
        assertEquals(BodyWeightEditorError.INVALID_WEIGHT, viewModel.state.value.editor?.error)

        assertTrue(repository.addCalls.isEmpty())
    }

    @Test
    fun aMalformedDateIsRejectedWithoutWriting() = runTest(dispatcher) {
        val repository = FakeBodyWeightRepository()
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        viewModel.onAddMeasurement()

        viewModel.onEditorDateChanged("2024-1-1")
        viewModel.onEditorWeightChanged("80")
        viewModel.onEditorSubmit()

        assertEquals(BodyWeightEditorError.INVALID_DATE, viewModel.state.value.editor?.error)
        assertTrue(repository.addCalls.isEmpty())
    }

    @Test
    fun aFutureDateIsRejectedWithoutWriting() = runTest(dispatcher) {
        val repository = FakeBodyWeightRepository()
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        viewModel.onAddMeasurement()

        viewModel.onEditorDateChanged(progressDayLabel(today + 1))
        viewModel.onEditorWeightChanged("80")
        viewModel.onEditorSubmit()

        assertEquals(BodyWeightEditorError.FUTURE_DATE, viewModel.state.value.editor?.error)
        assertTrue(repository.addCalls.isEmpty())
    }

    @Test
    fun aDuplicateDayAsksForReplacementAndReplaceCallsAddWithReplace() = runTest(dispatcher) {
        val repository =
            FakeBodyWeightRepository(initial = listOf(BodyWeightMeasurement(today, 80.0)))
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onAddMeasurement()
        viewModel.onEditorWeightChanged("82")
        viewModel.onEditorSubmit()
        advanceUntilIdle()

        assertEquals(listOf(AddCall(today, 82.0, false)), repository.addCalls)
        assertEquals(PendingBodyWeightAdd(today, 82.0), viewModel.state.value.pendingDuplicate)
        assertNotNull(viewModel.state.value.editor)

        viewModel.onReplaceConfirmed()
        advanceUntilIdle()

        assertEquals(AddCall(today, 82.0, true), repository.addCalls.last())
        assertNull(viewModel.state.value.pendingDuplicate)
        assertNull(viewModel.state.value.editor)
    }

    @Test
    fun dismissingTheDuplicateConfirmationKeepsTheDraftAndWritesNothing() = runTest(dispatcher) {
        val repository =
            FakeBodyWeightRepository(initial = listOf(BodyWeightMeasurement(today, 80.0)))
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        viewModel.onAddMeasurement()
        viewModel.onEditorWeightChanged("82")
        viewModel.onEditorSubmit()
        advanceUntilIdle()

        viewModel.onDuplicateDismissed()

        assertNull(viewModel.state.value.pendingDuplicate)
        assertNotNull(viewModel.state.value.editor)
        assertEquals(1, repository.addCalls.size)
    }

    // ── Edit ──────────────────────────────────────────────────────────────

    @Test
    fun anUnchangedWeightReusesTheExactStoredKilograms() = runTest(dispatcher) {
        val storedKg = 80.123456
        val free = today - 2L
        val repository =
            FakeBodyWeightRepository(initial = listOf(BodyWeightMeasurement(today, storedKg)))
        val viewModel =
            viewModel(repository, unitRepository = FakeWeightUnitRepository(WeightUnit.LB))
        advanceUntilIdle()

        viewModel.onEditMeasurement(today)
        // Only the date moves; the weight text is the exact string the editor opened with.
        viewModel.onEditorDateChanged(progressDayLabel(free))
        viewModel.onEditorSubmit()
        advanceUntilIdle()

        assertEquals(UpdateCall(today, free, storedKg), repository.updateCalls.single())
    }

    @Test
    fun aChangedWeightIsConvertedFromTheDisplayUnit() = runTest(dispatcher) {
        val repository =
            FakeBodyWeightRepository(initial = listOf(BodyWeightMeasurement(today, 80.0)))
        val viewModel =
            viewModel(repository, unitRepository = FakeWeightUnitRepository(WeightUnit.LB))
        advanceUntilIdle()

        viewModel.onEditMeasurement(today)
        viewModel.onEditorWeightChanged("180")
        viewModel.onEditorSubmit()
        advanceUntilIdle()

        assertEquals(
            180.0 / WeightUnit.POUNDS_PER_KILOGRAM,
            repository.updateCalls.single().weightKg
        )
    }

    @Test
    fun movingOntoAnOccupiedDaySurfacesAnErrorAndDoesNotOverwrite() = runTest(dispatcher) {
        val other = today - 1L
        val repository = FakeBodyWeightRepository(
            initial = listOf(
                BodyWeightMeasurement(other, 79.0),
                BodyWeightMeasurement(today, 80.0)
            )
        )
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onEditMeasurement(other)
        viewModel.onEditorDateChanged(progressDayLabel(today))
        viewModel.onEditorSubmit()
        advanceUntilIdle()

        assertEquals(UpdateCall(other, today, 79.0), repository.updateCalls.single())
        assertEquals(BodyWeightEditorError.DATE_OCCUPIED, viewModel.state.value.editor?.error)
        // The occupant keeps its stored value; nothing was overwritten.
        assertEquals(
            80.0,
            viewModel.state.value.measurements.first {
                it.localEpochDay == today
            }.weightKg
        )
    }

    // ── Delete ────────────────────────────────────────────────────────────

    @Test
    fun deletionIsConfirmedBeforeItWrites() = runTest(dispatcher) {
        val repository =
            FakeBodyWeightRepository(initial = listOf(BodyWeightMeasurement(today, 80.0)))
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onEditMeasurement(today)
        viewModel.onDeleteRequested()
        assertEquals(today, viewModel.state.value.pendingDeleteDay)
        assertTrue(repository.deleteCalls.isEmpty())

        viewModel.onDeleteDismissed()
        assertNull(viewModel.state.value.pendingDeleteDay)
        assertTrue(repository.deleteCalls.isEmpty())
        assertNotNull(viewModel.state.value.editor)

        viewModel.onDeleteRequested()
        viewModel.onDeleteConfirmed()
        advanceUntilIdle()

        assertEquals(listOf(today), repository.deleteCalls)
        assertNull(viewModel.state.value.editor)
    }

    @Test
    fun aCancelledDeletePropagatesInsteadOfSilentlyCompleting() = runTest(dispatcher) {
        val repository =
            FakeBodyWeightRepository(initial = listOf(BodyWeightMeasurement(today, 80.0)))
        repository.deleteFailure = CancellationException("cancelled")
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onEditMeasurement(today)
        viewModel.onDeleteRequested()
        viewModel.onDeleteConfirmed()
        advanceUntilIdle()

        // The cancellation is rethrown, so no completion handler runs: the delete was issued but
        // never treated as a finished write, and the draft is not closed.
        assertEquals(listOf(today), repository.deleteCalls)
        assertNotNull(viewModel.state.value.editor)
        assertTrue(viewModel.state.value.submitting)
    }

    // ── Goal ──────────────────────────────────────────────────────────────

    @Test
    fun goalSetReplaceClearAndInvalidInput() = runTest(dispatcher) {
        val repository = FakeBodyWeightRepository()
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onGoalInputChanged("75")
        viewModel.onSaveGoal()
        advanceUntilIdle()
        assertEquals(listOf<Double?>(75.0), repository.goalCalls)

        viewModel.onGoalInputChanged("77")
        viewModel.onSaveGoal()
        advanceUntilIdle()
        assertEquals(listOf<Double?>(75.0, 77.0), repository.goalCalls)

        viewModel.onGoalInputChanged("-1")
        viewModel.onSaveGoal()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.goalError)
        assertEquals(listOf<Double?>(75.0, 77.0), repository.goalCalls)

        viewModel.onClearGoal()
        advanceUntilIdle()
        assertEquals(listOf(75.0, 77.0, null), repository.goalCalls)
    }

    @Test
    fun aGoalIsConvertedFromTheDisplayUnit() = runTest(dispatcher) {
        val repository = FakeBodyWeightRepository()
        val viewModel =
            viewModel(repository, unitRepository = FakeWeightUnitRepository(WeightUnit.LB))
        advanceUntilIdle()

        viewModel.onGoalInputChanged("165")
        viewModel.onSaveGoal()
        advanceUntilIdle()

        assertEquals(165.0 / WeightUnit.POUNDS_PER_KILOGRAM, repository.goalCalls.single())
    }

    @Test
    fun aCancelledGoalWriteIsNotReportedAsAGoalError() = runTest(dispatcher) {
        val repository = FakeBodyWeightRepository()
        repository.goalFailure = CancellationException("cancelled")
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onGoalInputChanged("75")
        viewModel.onSaveGoal()
        advanceUntilIdle()

        // A cancelled write is not a user-facing goal failure: the cancellation is rethrown,
        // so the generic failure handler never marks the goal input invalid.
        assertEquals(listOf<Double?>(75.0), repository.goalCalls)
        assertFalse(viewModel.state.value.goalError)
    }

    // ── Unit and range ────────────────────────────────────────────────────

    @Test
    fun switchingTheDisplayUnitWritesNothing() = runTest(dispatcher) {
        val repository =
            FakeBodyWeightRepository(initial = listOf(BodyWeightMeasurement(today, 80.0)))
        val units = FakeWeightUnitRepository(WeightUnit.KG)
        val viewModel = viewModel(repository, units)
        advanceUntilIdle()

        units.units.value = WeightUnit.LB
        advanceUntilIdle()

        assertEquals(WeightUnit.LB, viewModel.state.value.weightUnit)
        assertTrue(repository.addCalls.isEmpty())
        assertTrue(repository.updateCalls.isEmpty())
        assertTrue(repository.deleteCalls.isEmpty())
        assertTrue(repository.goalCalls.isEmpty())
    }

    @Test
    fun theSelectedRangeFiltersTheChartWindow() = runTest(dispatcher) {
        val repository = FakeBodyWeightRepository(
            initial = listOf(
                BodyWeightMeasurement(today, 80.0),
                BodyWeightMeasurement(today - 30L, 79.0),
                BodyWeightMeasurement(today - 400L, 78.0)
            )
        )
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        assertEquals(
            listOf(today),
            viewModel.state.value.measurementsInRange.map {
                it.localEpochDay
            }
        )

        viewModel.onRangeSelected(ProgressRange.DAYS_365)
        assertEquals(
            listOf(today - 30L, today),
            viewModel.state.value.measurementsInRange.map { it.localEpochDay }
        )
    }

    // ── Submission guards ─────────────────────────────────────────────────

    @Test
    fun aRepeatedSubmitWhileTheWriteIsInFlightWritesOnce() = runTest(dispatcher) {
        val repository = FakeBodyWeightRepository()
        val gate = CompletableDeferred<Unit>()
        repository.addGate = gate
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onAddMeasurement()
        viewModel.onEditorWeightChanged("80")
        viewModel.onEditorSubmit()
        runCurrent()
        assertTrue(viewModel.state.value.submitting)

        viewModel.onEditorSubmit()
        runCurrent()
        assertEquals(1, repository.addCalls.size)

        gate.complete(Unit)
        advanceUntilIdle()
        assertNull(viewModel.state.value.editor)
    }

    @Test
    fun aStaleCompletionDoesNotCloseANewerDraft() = runTest(dispatcher) {
        val repository = FakeBodyWeightRepository()
        val gate = CompletableDeferred<Unit>()
        repository.addGate = gate
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onAddMeasurement()
        viewModel.onEditorWeightChanged("80")
        viewModel.onEditorSubmit()
        runCurrent()
        assertTrue(viewModel.state.value.submitting)

        // Replace the draft while the first write is still in flight.
        viewModel.onEditorCancel()
        viewModel.onAddMeasurement()
        viewModel.onEditorWeightChanged("81")
        assertNotNull(viewModel.state.value.editor)

        gate.complete(Unit)
        advanceUntilIdle()

        assertNotNull(viewModel.state.value.editor)
        assertEquals("81", viewModel.state.value.editor?.weightText)
        assertFalse(viewModel.state.value.submitting)
    }

    // ── Builders and fakes ────────────────────────────────────────────────

    private fun viewModel(
        repository: FakeBodyWeightRepository = FakeBodyWeightRepository(),
        unitRepository: FakeWeightUnitRepository = FakeWeightUnitRepository(),
        clock: TimeProvider = FakeTimeProvider(nowMillis, offsetMillis)
    ) = BodyWeightViewModel(
        bodyWeightRepository = repository,
        weightUnitRepository = unitRepository,
        timeProvider = clock
    )
}

private data class AddCall(val day: Long, val weightKg: Double, val replace: Boolean)

private data class UpdateCall(val previous: Long, val day: Long, val weightKg: Double)

private class FakeBodyWeightRepository(
    initial: List<BodyWeightMeasurement> = emptyList(),
    initialGoal: Double? = null
) : BodyWeightRepository {
    val measurementsFlow = MutableStateFlow(initial.sortedBy { it.localEpochDay })
    val goalFlow = MutableStateFlow(initialGoal)
    val addCalls = mutableListOf<AddCall>()
    val updateCalls = mutableListOf<UpdateCall>()
    val deleteCalls = mutableListOf<Long>()
    val goalCalls = mutableListOf<Double?>()

    var measurementsError = false

    /** When set, a delete records its call then fails with this throwable. */
    var deleteFailure: Throwable? = null

    /** When set, a goal write records its call then fails with this throwable. */
    var goalFailure: Throwable? = null

    /** When set, an add records its call then suspends until it is completed. */
    var addGate: CompletableDeferred<Unit>? = null

    override fun observeMeasurements(): Flow<List<BodyWeightMeasurement>> = if (measurementsError) {
        flow { throw IllegalStateException("measurements read failed") }
    } else {
        measurementsFlow
    }

    override suspend fun measurements(): List<BodyWeightMeasurement> = measurementsFlow.value

    override suspend fun add(localEpochDay: Long, weightKg: Double, replace: Boolean) {
        addCalls += AddCall(localEpochDay, weightKg, replace)
        addGate?.await()
        if (!replace && measurementsFlow.value.any { it.localEpochDay == localEpochDay }) {
            throw BodyWeightException("duplicate", BodyWeightFailureReason.DUPLICATE_DATE)
        }
        measurementsFlow.value = (
            measurementsFlow.value.filterNot { it.localEpochDay == localEpochDay } +
                BodyWeightMeasurement(localEpochDay, weightKg)
            ).sortedBy { it.localEpochDay }
    }

    override suspend fun update(
        previousLocalEpochDay: Long,
        localEpochDay: Long,
        weightKg: Double
    ) {
        updateCalls += UpdateCall(previousLocalEpochDay, localEpochDay, weightKg)
        val existing = measurementsFlow.value
        if (existing.none { it.localEpochDay == previousLocalEpochDay }) {
            throw BodyWeightException("missing", BodyWeightFailureReason.MISSING_MEASUREMENT)
        }
        if (localEpochDay != previousLocalEpochDay &&
            existing.any { it.localEpochDay == localEpochDay }
        ) {
            throw BodyWeightException("occupied", BodyWeightFailureReason.DATE_OCCUPIED)
        }
        measurementsFlow.value = (
            existing.filterNot { it.localEpochDay == previousLocalEpochDay } +
                BodyWeightMeasurement(localEpochDay, weightKg)
            ).sortedBy { it.localEpochDay }
    }

    override suspend fun delete(localEpochDay: Long) {
        deleteCalls += localEpochDay
        deleteFailure?.let { throw it }
        measurementsFlow.value =
            measurementsFlow.value.filterNot { it.localEpochDay == localEpochDay }
    }

    override fun observeGoal(): Flow<Double?> = goalFlow

    override suspend fun setGoal(weightKg: Double?) {
        goalCalls += weightKg
        goalFailure?.let { throw it }
        goalFlow.value = weightKg
    }
}

private class FakeWeightUnitRepository(initial: WeightUnit = WeightUnit.KG) : WeightUnitRepository {
    val units = MutableStateFlow(initial)
    override suspend fun selectedUnit(): WeightUnit = units.value
    override fun unitFlow(): Flow<WeightUnit> = units
    override suspend fun setUnit(unit: WeightUnit) {
        units.value = unit
    }
}

private class FakeTimeProvider(var now: Long, var offset: Long) : TimeProvider {
    override fun nowMillis(): Long = now

    override fun utcOffsetMillis(): Long = offset
}
