package com.hydrafit.app.feature.cardio

import com.hydrafit.app.core.domain.cardio.CardioActivity
import com.hydrafit.app.core.domain.cardio.CardioActivityType
import com.hydrafit.app.core.domain.cardio.CardioRepository
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.time.isoLocalDateTime
import com.hydrafit.app.core.domain.time.localEpochDay
import com.hydrafit.app.core.domain.unit.DistanceUnit
import com.hydrafit.app.core.userdata.settings.DistanceUnitRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class CardioViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val nowMillis = 1_700_000_040_000L
    private val offsetMillis = 3_600_000L
    private val today = localEpochDay(nowMillis, offsetMillis)

    private val timeProvider = object : TimeProvider {
        override fun nowMillis(): Long = nowMillis

        override fun utcOffsetMillis(): Long = offsetMillis
    }

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
    fun anEmptyHistoryIsReportedAsEmptyRatherThanLoadingOrFailed() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.loading)
        assertFalse(viewModel.state.value.loadFailed)
        assertTrue(viewModel.state.value.isEmpty)
    }

    @Test
    fun aFailedLoadIsDistinctFromEmptyAndRetryResubscribes() = runTest(dispatcher) {
        val repository = FakeCardioRepository()
        repository.activitiesError = true
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.loadFailed)
        assertFalse(viewModel.state.value.loading)
        assertFalse(viewModel.state.value.isEmpty)

        repository.activitiesError = false
        repository.activities.value = listOf(activity(id = 7))
        viewModel.onRetryLoad()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.loadFailed)
        assertFalse(viewModel.state.value.isEmpty)
        assertEquals(1, viewModel.state.value.activities.size)
    }

    // ── Create ────────────────────────────────────────────────────────────

    @Test
    fun cancellationCannotHideAnInFlightSave() = runTest(dispatcher) {
        val repository = FakeCardioRepository()
        val gate = CompletableDeferred<Unit>()
        repository.addGate = gate
        val viewModel = viewModel(repository)
        runCurrent()
        viewModel.onAddActivity()
        viewModel.onEditorMinutesChanged("5")
        viewModel.onEditorSubmit()
        runCurrent()
        assertTrue(viewModel.state.value.submitting)
        viewModel.onEditorCancel()
        assertNotNull(viewModel.state.value.editor)
        gate.complete(Unit)
        runCurrent()
        assertFalse(viewModel.state.value.submitting)
        assertNull(viewModel.state.value.editor)
        assertEquals(1, repository.addCalls.size)
    }

    @Test
    fun aNewActivitySeedsNowAndWritesTheEnteredValues() = runTest(dispatcher) {
        val repository = FakeCardioRepository()
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onAddActivity()
        assertEquals(
            isoLocalDateTime(nowMillis, offsetMillis),
            viewModel.state.value.editor?.startText
        )
        viewModel.onEditorTypeChanged(CardioActivityType.CYCLING)
        viewModel.onEditorMinutesChanged("30")
        viewModel.onEditorSecondsChanged("15")
        viewModel.onEditorSubmit()
        advanceUntilIdle()

        val written = repository.addCalls.single()
        assertEquals(CardioActivityType.CYCLING, written.type)
        assertEquals(nowMillis, written.startedAtMillis)
        assertEquals(1815, written.durationSeconds)
        assertNull(written.distanceMeters)
        assertNull(viewModel.state.value.editor)
    }

    @Test
    fun aMissingDurationIsRefusedWithItsOwnErrorAndNothingIsWritten() = runTest(dispatcher) {
        val repository = FakeCardioRepository()
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onAddActivity()
        viewModel.onEditorSubmit()
        advanceUntilIdle()

        assertEquals(CardioEditorError.INVALID_DURATION, viewModel.state.value.editor?.error)
        assertTrue(repository.addCalls.isEmpty())
    }

    @Test
    fun aMalformedStartTimeIsRefusedWithItsOwnErrorAndNothingIsWritten() = runTest(dispatcher) {
        val repository = FakeCardioRepository()
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onAddActivity()
        viewModel.onEditorStartChanged("2024-02-30 10:00")
        viewModel.onEditorMinutesChanged("5")
        viewModel.onEditorSubmit()
        advanceUntilIdle()

        assertEquals(CardioEditorError.INVALID_START_TIME, viewModel.state.value.editor?.error)
        assertTrue(repository.addCalls.isEmpty())
    }

    @Test
    fun aFeetDistanceIsStoredAsWholeMetres() = runTest(dispatcher) {
        val repository = FakeCardioRepository()
        val viewModel = viewModel(
            repository = repository,
            unitRepository = FakeDistanceUnitRepository(DistanceUnit.FEET)
        )
        advanceUntilIdle()

        viewModel.onAddActivity()
        viewModel.onEditorMinutesChanged("10")
        viewModel.onEditorDistanceChanged("100")
        viewModel.onEditorSubmit()
        advanceUntilIdle()

        assertEquals(30, repository.addCalls.single().distanceMeters)
    }

    // ── Edit and delete ───────────────────────────────────────────────────

    @Test
    fun editingSeedsStoredValuesAndUpdatesTheSameIdentity() = runTest(dispatcher) {
        val stored = activity(
            id = 5,
            identity = "stored-identity",
            type = CardioActivityType.RUNNING,
            startedAtMillis = nowMillis - 86_400_000L,
            durationSeconds = 600,
            distanceMeters = 1000,
            note = "Easy"
        )
        val repository = FakeCardioRepository(listOf(stored))
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onEditActivity(5)
        val editor = assertNotNull(viewModel.state.value.editor)
        assertEquals(isoLocalDateTime(stored.startedAtMillis, offsetMillis), editor.startText)
        assertEquals("10", editor.minutesText)
        assertEquals("0", editor.secondsText)
        assertFalse(editor.pendingDelete)

        viewModel.onEditorMinutesChanged("20")
        viewModel.onEditorSubmit()
        advanceUntilIdle()

        val updated = repository.updateCalls.single()
        assertEquals(5, updated.id)
        assertEquals("stored-identity", updated.identity)
        assertEquals(1200, updated.durationSeconds)
        assertEquals(1000, updated.distanceMeters)
        assertNull(viewModel.state.value.editor)
    }

    @Test
    fun deletingRemovesExactlyTheEditedActivity() = runTest(dispatcher) {
        val repository = FakeCardioRepository(listOf(activity(id = 5), activity(id = 6)))
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onEditActivity(5)
        viewModel.onEditorDeleteRequested()
        assertTrue(viewModel.state.value.editor?.pendingDelete == true)

        viewModel.onEditorDeleteConfirmed()
        advanceUntilIdle()

        assertEquals(listOf(5L), repository.deleteCalls)
        assertNull(viewModel.state.value.editor)
    }

    // ── Charts ────────────────────────────────────────────────────────────

    @Test
    fun totalsAreBoundedToTheSelectedRangeWhileHistoryKeepsEveryActivity() = runTest(dispatcher) {
        val todayActivity = activity(
            id = 1,
            type = CardioActivityType.RUNNING,
            startedAtMillis = nowMillis,
            durationSeconds = 600,
            distanceMeters = 1000
        )
        val oldActivity = activity(
            id = 2,
            type = CardioActivityType.CYCLING,
            startedAtMillis = nowMillis - 60L * 86_400_000L,
            durationSeconds = 1200,
            distanceMeters = 5000
        )
        val repository = FakeCardioRepository(listOf(todayActivity, oldActivity))
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        assertEquals(
            listOf(CardioActivityType.RUNNING),
            viewModel.state.value.progress.totalsByType.map { it.type }
        )
        assertEquals(2, viewModel.state.value.sortedActivities.size)

        viewModel.onRangeSelected(CardioRange.DAYS_365)
        val totals = viewModel.state.value.progress.totalsByType
        assertEquals(
            listOf(CardioActivityType.RUNNING, CardioActivityType.CYCLING),
            totals.map { it.type }
        )
        val cycling = totals.single { it.type == CardioActivityType.CYCLING }
        assertEquals(5000L, cycling.distanceMeters)
        assertEquals(5000.0 / 1200.0, cycling.metresPerSecond)
    }

    @Test
    fun aRangeWithNoActivitiesIsNotTheSameAsAnEmptyHistory() = runTest(dispatcher) {
        val oldActivity = activity(
            id = 2,
            startedAtMillis = nowMillis - 60L * 86_400_000L,
            durationSeconds = 1200
        )
        val viewModel = viewModel(FakeCardioRepository(listOf(oldActivity)))
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isEmpty)
        assertTrue(viewModel.state.value.rangeIsEmpty)
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun viewModel(
        repository: FakeCardioRepository = FakeCardioRepository(),
        unitRepository: FakeDistanceUnitRepository =
            FakeDistanceUnitRepository(DistanceUnit.METERS)
    ): CardioViewModel = CardioViewModel(
        repository = repository,
        distanceUnitRepository = unitRepository,
        timeProvider = timeProvider
    )

    private fun activity(
        id: Long,
        identity: String = "identity-$id",
        type: CardioActivityType = CardioActivityType.WALKING,
        startedAtMillis: Long = nowMillis,
        durationSeconds: Int = 600,
        distanceMeters: Int? = null,
        note: String? = null
    ): CardioActivity = CardioActivity(
        id = id,
        identity = identity,
        type = type,
        startedAtMillis = startedAtMillis,
        durationSeconds = durationSeconds,
        distanceMeters = distanceMeters,
        note = note
    )

    private class FakeDistanceUnitRepository(private val unit: DistanceUnit) :
        DistanceUnitRepository {
        override suspend fun selectedUnit(): DistanceUnit = unit

        override fun unitFlow(): Flow<DistanceUnit> = flowOf(unit)

        override suspend fun setUnit(unit: DistanceUnit) = Unit
    }

    private class FakeCardioRepository(initial: List<CardioActivity> = emptyList()) :
        CardioRepository {
        val activities = MutableStateFlow(initial)
        var activitiesError = false
        var addGate: CompletableDeferred<Unit>? = null
        val addCalls = mutableListOf<CardioActivity>()
        val updateCalls = mutableListOf<CardioActivity>()
        val deleteCalls = mutableListOf<Long>()
        private var nextId = 100L

        override fun activitiesFlow(): Flow<List<CardioActivity>> = if (activitiesError) {
            flow { throw IllegalStateException("Could not read activities") }
        } else {
            activities
        }

        override suspend fun all(): List<CardioActivity> = activities.value

        override suspend fun add(activity: CardioActivity): Long {
            addGate?.await()
            addCalls += activity
            val stored = activity.copy(id = nextId++)
            activities.value = activities.value + stored
            return stored.id
        }

        override suspend fun update(activity: CardioActivity) {
            updateCalls += activity
        }

        override suspend fun delete(id: Long) {
            deleteCalls += id
        }

        override suspend fun byIdentity(identity: String): CardioActivity? =
            activities.value.firstOrNull { it.identity == identity }
    }
}
