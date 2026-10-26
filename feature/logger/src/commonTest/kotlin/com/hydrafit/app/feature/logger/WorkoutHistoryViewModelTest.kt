package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.unit.DistanceUnit
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.workout.ExerciseNoteRepository
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.WorkoutHistoryEntry
import com.hydrafit.app.core.domain.workout.WorkoutHistoryKey
import com.hydrafit.app.core.domain.workout.WorkoutHistoryNameSource
import com.hydrafit.app.core.domain.workout.WorkoutHistoryPage
import com.hydrafit.app.core.domain.workout.WorkoutHistoryRepository
import com.hydrafit.app.core.domain.workout.WorkoutHistoryRequest
import com.hydrafit.app.core.domain.workout.WorkoutHistorySet
import com.hydrafit.app.core.domain.workout.WorkoutHistorySetPage
import com.hydrafit.app.core.domain.workout.WorkoutHistorySetRequest
import com.hydrafit.app.core.domain.workout.WorkoutTimingProvenance
import com.hydrafit.app.core.userdata.settings.DistanceUnitRepository
import com.hydrafit.app.core.userdata.settings.WeightUnitRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

/** Bounded history reading, paging and detail reconciliation for the C6 browser. */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutHistoryViewModelTest {

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
    fun loadsTheFirstPageWithoutACursor() = runTest(dispatcher) {
        val repository = FakeWorkoutHistoryRepository(listOf(session("s1")))
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        assertEquals(1, repository.entryRequests.size)
        assertNull(repository.entryRequests.single().cursor)
        assertEquals(listOf<WorkoutHistoryKey>(WorkoutHistoryKey.Session("s1")), viewModel.keys())
        assertFalse(viewModel.state.value.loading)
    }

    @Test
    fun applyingAFilterDropsTheCursorAndReplacesTheLoadedPages() = runTest(dispatcher) {
        val repository = FakeWorkoutHistoryRepository(thirtyFiveSessions())
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        viewModel.onLoadMore()
        advanceUntilIdle()
        assertNotNull(repository.entryRequests.last().cursor)
        assertEquals(35, viewModel.state.value.entries.size)

        viewModel.onDayFromChanged(4L)
        advanceUntilIdle()

        val applied = repository.entryRequests.last()
        assertNull(applied.cursor)
        assertEquals(4L, applied.dayFrom)
        assertEquals(30, viewModel.state.value.entries.size)
    }

    @Test
    fun searchMatchesTheNoteTextAndStartsFromTheFirstPage() = runTest(dispatcher) {
        val repository = FakeWorkoutHistoryRepository(thirtyFiveSessions())
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onSearchDraftChanged("session 34")
        viewModel.onApplySearch()
        advanceUntilIdle()

        assertEquals("session 34", repository.entryRequests.last().search)
        assertEquals(
            listOf<WorkoutHistoryKey>(WorkoutHistoryKey.Session("s34")),
            viewModel.keys()
        )
    }

    @Test
    fun anInvertedDayRangeIsReportedWithoutQuerying() = runTest(dispatcher) {
        val repository = FakeWorkoutHistoryRepository(listOf(session("s1")))
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        // A valid lower bound is applied and keeps the one session (its local day is 5).
        viewModel.onDayFromChanged(1L)
        advanceUntilIdle()
        val queriesBefore = repository.entryRequests.size
        val entriesBefore = viewModel.state.value.entries

        // An upper bound below it inverts the range: the filter is reported and never queried.
        viewModel.onDayToChanged(0L)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.filter.hasInvalidDayRange)
        assertEquals(queriesBefore, repository.entryRequests.size)
        assertEquals(entriesBefore, viewModel.state.value.entries)
    }

    @Test
    fun clearingFiltersAndDatesReturnsToTheUnfilteredQuery() = runTest(dispatcher) {
        val repository = FakeWorkoutHistoryRepository(listOf(session("s1")))
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onSearchDraftChanged("squat")
        viewModel.onApplySearch()
        viewModel.onDayFromChanged(3L)
        viewModel.onDayToChanged(7L)
        advanceUntilIdle()
        viewModel.onClearFilters()
        advanceUntilIdle()

        val applied = repository.entryRequests.last()
        assertEquals("", applied.search)
        assertNull(applied.dayFrom)
        assertNull(applied.dayTo)
        assertEquals("", viewModel.state.value.searchDraft)
    }

    @Test
    fun clearingDatesDropsBothBoundsInOneApply() = runTest(dispatcher) {
        val repository = FakeWorkoutHistoryRepository(listOf(session("s1")))
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onDayFromChanged(3L)
        viewModel.onDayToChanged(7L)
        advanceUntilIdle()
        val queriesBefore = repository.entryRequests.size
        viewModel.onClearDates()
        advanceUntilIdle()

        assertEquals(queriesBefore + 1, repository.entryRequests.size)
        assertNull(repository.entryRequests.last().dayFrom)
        assertNull(repository.entryRequests.last().dayTo)
    }

    @Test
    fun loadMoreAppendsEveryRemainingEntryExactlyOnce() = runTest(dispatcher) {
        val repository = FakeWorkoutHistoryRepository(thirtyFiveSessions())
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        assertEquals(30, viewModel.state.value.entries.size)
        assertTrue(viewModel.state.value.hasMore)

        viewModel.onLoadMore()
        advanceUntilIdle()

        assertEquals(35, viewModel.state.value.entries.size)
        assertEquals(35, viewModel.keys().distinct().size)
        assertFalse(viewModel.state.value.hasMore)
    }

    @Test
    fun repeatedLoadMoreWhileOneIsInFlightDoesNotDuplicateRows() = runTest(dispatcher) {
        val repository = FakeWorkoutHistoryRepository(sixtyFiveSessions())
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onLoadMore()
        viewModel.onLoadMore()
        viewModel.onLoadMore()
        advanceUntilIdle()

        assertEquals(60, viewModel.state.value.entries.size)
        assertEquals(60, viewModel.keys().distinct().size)
    }

    @Test
    fun aNewLiveFirstPageDropsPagesDerivedFromTheOldOne() = runTest(dispatcher) {
        val repository = FakeWorkoutHistoryRepository(thirtyFiveSessions())
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        viewModel.onLoadMore()
        advanceUntilIdle()
        assertEquals(35, viewModel.state.value.entries.size)

        // A recorded set is removed elsewhere, so the first page legitimately changes.
        repository.entries.value = thirtyFiveSessions().dropLast(1)
        advanceUntilIdle()

        assertEquals(30, viewModel.state.value.entries.size)
        assertTrue(viewModel.state.value.hasMore)
    }

    @Test
    fun aFailedFirstPageIsRecoverableAndKeepsNothingStale() = runTest(dispatcher) {
        val repository = FakeWorkoutHistoryRepository(listOf(session("s1")))
        repository.failEntryReads = true
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.failed)
        assertFalse(viewModel.state.value.loading)
        assertTrue(viewModel.state.value.entries.isEmpty())

        repository.failEntryReads = false
        viewModel.onRetry()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.failed)
        assertEquals(1, viewModel.state.value.entries.size)
    }

    @Test
    fun aFailedLoadMoreKeepsLoadedRowsAndCanBeRetried() = runTest(dispatcher) {
        val repository = FakeWorkoutHistoryRepository(thirtyFiveSessions())
        repository.failEntryReadsAfterTheFirst = true
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onLoadMore()
        advanceUntilIdle()
        assertEquals(30, viewModel.state.value.entries.size)
        assertTrue(viewModel.state.value.loadMoreFailed)

        repository.failEntryReadsAfterTheFirst = false
        viewModel.onLoadMore()
        advanceUntilIdle()

        assertEquals(35, viewModel.state.value.entries.size)
        assertFalse(viewModel.state.value.loadMoreFailed)
    }

    @Test
    fun anEmptyUnfilteredResultIsDistinctFromNoMatches() = runTest(dispatcher) {
        val repository = FakeWorkoutHistoryRepository(emptyList())
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.showEmptyHistory)
        assertFalse(viewModel.state.value.showNoMatches)

        viewModel.onSearchDraftChanged("squat")
        viewModel.onApplySearch()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.showNoMatches)
        assertFalse(viewModel.state.value.showEmptyHistory)
    }

    @Test
    fun openingAnEntryReadsOnlyItsExplicitIdentityAndBoundedSets() = runTest(dispatcher) {
        val key = WorkoutHistoryKey.Session("s1")
        val repository = FakeWorkoutHistoryRepository(listOf(session("s1"), session("s2")))
        repository.sets.value = listOf(set(1L), set(2L))
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onEntrySelected(key)
        advanceUntilIdle()

        assertEquals(key, viewModel.state.value.selectedKey)
        assertEquals(listOf<WorkoutHistoryKey>(key), repository.entryKeys)
        assertEquals(listOf(1L, 2L), viewModel.state.value.sets.map { it.id })
        assertEquals(1, repository.setRequests.size)
        assertNull(repository.setRequests.single().cursor)
    }

    @Test
    fun anUnresolvedIdentityRendersAnUnavailableEntryRatherThanGuessingOne() = runTest(dispatcher) {
        val repository = FakeWorkoutHistoryRepository(listOf(session("s1")))
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onEntrySelected(WorkoutHistoryKey.Session("missing"))
        advanceUntilIdle()

        assertTrue(viewModel.state.value.showDetailUnavailable)
        assertNull(viewModel.state.value.detailEntry)
    }

    @Test
    fun aNotedSessionWithNoRecordedSetsStaysVisibleWithItsNote() = runTest(dispatcher) {
        val key = WorkoutHistoryKey.Session("noted")
        val repository = FakeWorkoutHistoryRepository(
            listOf(
                WorkoutHistoryEntry(
                    key = key,
                    timestampMillis = 500L,
                    localEpochDay = 0L,
                    sessionMissing = false,
                    note = "",
                    recordedSetCount = 0,
                    nameSource = WorkoutHistoryNameSource.NONE
                )
            )
        )
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onEntrySelected(key)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.showNoRecordedSets)
        assertEquals(0, viewModel.state.value.detailEntry?.recordedSetCount)
        assertEquals(
            HistoryNoteState.CLEARED,
            noteStateOf(viewModel.state.value.detailEntry?.note)
        )
    }

    @Test
    fun detailPagingAppendsAndAFreshFirstPageDropsLaterPages() = runTest(dispatcher) {
        val key = WorkoutHistoryKey.Session("s1")
        val repository = FakeWorkoutHistoryRepository(listOf(session("s1")))
        repository.sets.value = (1L..35L).map { set(it) }
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onEntrySelected(key)
        advanceUntilIdle()
        assertEquals(30, viewModel.state.value.sets.size)
        assertTrue(viewModel.state.value.hasMoreSets)

        viewModel.onLoadMoreSets()
        advanceUntilIdle()
        assertEquals(35, viewModel.state.value.sets.size)
        assertFalse(viewModel.state.value.hasMoreSets)

        // A correction removes one recorded set; the first page is re-read and later pages dropped.
        repository.sets.value = (1L..34L).map { set(it) }
        advanceUntilIdle()

        assertEquals(30, viewModel.state.value.sets.size)
        assertTrue(viewModel.state.value.hasMoreSets)
        assertFalse(viewModel.state.value.loadMoreSetsFailed)
    }

    @Test
    fun repeatedSetLoadMoreWhileOneIsInFlightDoesNotDuplicateRows() = runTest(dispatcher) {
        val key = WorkoutHistoryKey.Session("s1")
        val repository = FakeWorkoutHistoryRepository(listOf(session("s1")))
        repository.sets.value = (1L..65L).map { set(it) }
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onEntrySelected(key)
        advanceUntilIdle()
        viewModel.onLoadMoreSets()
        viewModel.onLoadMoreSets()
        advanceUntilIdle()

        assertEquals(60, viewModel.state.value.sets.size)
        assertEquals(60, viewModel.state.value.sets.map { it.id }.distinct().size)
    }

    @Test
    fun aFailedSetReadIsReportedSeparatelyFromAnEmptySession() = runTest(dispatcher) {
        val key = WorkoutHistoryKey.Session("s1")
        val repository = FakeWorkoutHistoryRepository(listOf(session("s1")))
        repository.failSetReads = true
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onEntrySelected(key)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.setsFailed)
        assertFalse(viewModel.state.value.showNoRecordedSets)
    }

    @Test
    fun setupNotesLoadOncePerExerciseAndAFailedReadIsRetryable() = runTest(dispatcher) {
        val key = WorkoutHistoryKey.Session("s1")
        val repository = FakeWorkoutHistoryRepository(listOf(session("s1")))
        repository.sets.value = listOf(
            set(1L, exerciseId = "bench", exerciseName = "Bench Press"),
            set(2L, exerciseId = "squat", exerciseName = "Back Squat")
        )
        val notes = FakeExerciseNoteRepository(mutableMapOf("bench" to "Elbows tucked"))
        notes.failReads = true
        val viewModel = viewModel(repository, notes)
        advanceUntilIdle()

        viewModel.onEntrySelected(key)
        advanceUntilIdle()

        assertEquals(setOf("bench", "squat"), viewModel.state.value.exerciseNotes.keys)
        assertTrue(viewModel.state.value.exerciseNotes.values.all { it.loadFailed })

        val readsBefore = notes.reads.size
        notes.failReads = false
        viewModel.onRetryExerciseNote("bench")
        advanceUntilIdle()

        assertEquals(readsBefore + 1, notes.reads.size)
        assertEquals("Elbows tucked", viewModel.state.value.exerciseNotes["bench"]?.note)
        assertEquals(false, viewModel.state.value.exerciseNotes["bench"]?.loadFailed)
    }

    @Test
    fun aLiveSetEmissionDoesNotReReadAnAlreadyLoadedSetupNote() = runTest(dispatcher) {
        val key = WorkoutHistoryKey.Session("s1")
        val repository = FakeWorkoutHistoryRepository(listOf(session("s1")))
        repository.sets.value = listOf(set(1L))
        val notes = FakeExerciseNoteRepository(mutableMapOf("bench" to "Elbows tucked"))
        val viewModel = viewModel(repository, notes)
        advanceUntilIdle()

        viewModel.onEntrySelected(key)
        advanceUntilIdle()
        repository.sets.value = listOf(set(1L), set(2L))
        advanceUntilIdle()

        assertEquals(1, notes.reads.count { it == "bench" })
    }

    @Test
    fun closingTheDetailKeepsTheAppliedFiltersAndLoadedPages() = runTest(dispatcher) {
        val repository = FakeWorkoutHistoryRepository(thirtyFiveSessions())
        val viewModel = viewModel(repository)
        advanceUntilIdle()
        viewModel.onSearchDraftChanged("session")
        viewModel.onApplySearch()
        advanceUntilIdle()
        viewModel.onLoadMore()
        advanceUntilIdle()
        val entriesBefore = viewModel.state.value.entries
        assertEquals(35, entriesBefore.size)

        viewModel.onEntrySelected(WorkoutHistoryKey.Session("s0"))
        advanceUntilIdle()
        viewModel.onEntryClosed()
        advanceUntilIdle()

        assertNull(viewModel.state.value.selectedKey)
        assertEquals("session", viewModel.state.value.filter.search)
        assertEquals(entriesBefore, viewModel.state.value.entries)
        assertTrue(viewModel.state.value.sets.isEmpty())
    }

    @Test
    fun openingASecondEntryReplacesTheFirstOnesData() = runTest(dispatcher) {
        val repository = FakeWorkoutHistoryRepository(listOf(session("s1"), session("s2")))
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onEntrySelected(WorkoutHistoryKey.Session("s1"))
        repository.sets.value = listOf(set(1L))
        advanceUntilIdle()
        assertEquals(listOf(1L), viewModel.state.value.sets.map { it.id })

        viewModel.onEntrySelected(WorkoutHistoryKey.Session("s2"))
        repository.sets.value = listOf(set(9L))
        advanceUntilIdle()

        assertEquals(WorkoutHistoryKey.Session("s2"), viewModel.state.value.selectedKey)
        assertEquals(listOf(9L), viewModel.state.value.sets.map { it.id })
    }

    @Test
    fun anUnassignedSetReadsItsSingleSetBySetId() = runTest(dispatcher) {
        val key = WorkoutHistoryKey.UnassignedSet(7L)
        val repository = FakeWorkoutHistoryRepository(
            listOf(
                WorkoutHistoryEntry(
                    key = key,
                    timestampMillis = 100L,
                    localEpochDay = 0L,
                    sessionMissing = false,
                    note = null,
                    recordedSetCount = 1,
                    nameSource = WorkoutHistoryNameSource.CURRENT_CATALOG
                )
            )
        )
        repository.sets.value = listOf(set(7L))
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onEntrySelected(key)
        advanceUntilIdle()

        assertEquals(listOf(7L), viewModel.state.value.sets.map { it.id })
        assertFalse(viewModel.state.value.hasMoreSets)
    }

    @Test
    fun aFailedEntryMetadataReadKeepsItsOwnErrorState() = runTest(dispatcher) {
        val key = WorkoutHistoryKey.Session("s1")
        val repository = FakeWorkoutHistoryRepository(listOf(session("s1")))
        repository.failEntryMetadata = true
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.onEntrySelected(key)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.detailFailed)
        assertFalse(viewModel.state.value.showDetailUnavailable)
    }

    private fun WorkoutHistoryViewModel.keys() = state.value.entries.map { it.key }

    private fun viewModel(
        repository: FakeWorkoutHistoryRepository,
        notes: FakeExerciseNoteRepository = FakeExerciseNoteRepository(),
        unit: WeightUnit = WeightUnit.KG
    ) = WorkoutHistoryViewModel(
        historyRepository = repository,
        exerciseNoteRepository = notes,
        weightUnitRepository = FakeWeightUnitRepository(unit),
        distanceUnitRepository = FakeDistanceUnitRepository(),
        timeProvider = TimeProvider { 0L }
    )

    private fun session(id: String, timestampMillis: Long = 1_000L) = WorkoutHistoryEntry(
        key = WorkoutHistoryKey.Session(id),
        timestampMillis = timestampMillis,
        localEpochDay = 5L,
        sessionMissing = false,
        note = "session ${id.removePrefix("s")}",
        recordedSetCount = 1,
        nameSource = WorkoutHistoryNameSource.CURRENT_CATALOG
    )

    private fun sessions(count: Int) = (0 until count).map {
        session("s$it", timestampMillis = 1_000_000L - it)
    }

    private fun thirtyFiveSessions() = sessions(35)

    private fun sixtyFiveSessions() = sessions(65)

    private fun set(id: Long, exerciseId: String = "bench", exerciseName: String? = "Bench Press") =
        WorkoutHistorySet(
            id = id,
            exerciseId = exerciseId,
            exerciseName = exerciseName,
            reps = 5,
            weightKg = 60.0,
            loadKind = LoadKind.EXTERNAL,
            isWarmup = false,
            rir = null,
            performedAtMillis = 1_000_000L - id,
            timingProvenance = WorkoutTimingProvenance.UNKNOWN
        )

    private class FakeWeightUnitRepository(private val unit: WeightUnit) : WeightUnitRepository {
        override suspend fun selectedUnit(): WeightUnit = unit

        override fun unitFlow(): Flow<WeightUnit> = flowOf(unit)

        override suspend fun setUnit(unit: WeightUnit) = Unit
    }

    private class FakeDistanceUnitRepository(private val unit: DistanceUnit = DistanceUnit.METERS) :
        DistanceUnitRepository {
        override suspend fun selectedUnit(): DistanceUnit = unit

        override fun unitFlow(): Flow<DistanceUnit> = flowOf(unit)

        override suspend fun setUnit(unit: DistanceUnit) = Unit
    }

    private class FakeExerciseNoteRepository(
        private val notes: MutableMap<String, String?> = mutableMapOf()
    ) : ExerciseNoteRepository {
        var failReads: Boolean = false
        val reads = mutableListOf<String>()

        override suspend fun note(exerciseId: String): String? {
            reads += exerciseId
            if (failReads) throw IllegalStateException("note read failed")
            return notes[exerciseId]
        }

        override suspend fun save(exerciseId: String, text: String) {
            notes[exerciseId] = text
        }

        override suspend fun clear(exerciseId: String) {
            notes[exerciseId] = ""
        }
    }

    private class FakeWorkoutHistoryRepository(
        initialEntries: List<WorkoutHistoryEntry> = emptyList()
    ) : WorkoutHistoryRepository {
        val entries = MutableStateFlow(initialEntries)
        val sets = MutableStateFlow<List<WorkoutHistorySet>>(emptyList())
        var failEntryReads: Boolean = false
        var failEntryReadsAfterTheFirst: Boolean = false
        var failEntryMetadata: Boolean = false
        var failSetReads: Boolean = false
        val entryRequests = mutableListOf<WorkoutHistoryRequest>()
        val setRequests = mutableListOf<WorkoutHistorySetRequest>()
        val entryKeys = mutableListOf<WorkoutHistoryKey>()

        override fun observeEntries(request: WorkoutHistoryRequest): Flow<WorkoutHistoryPage> {
            entryRequests += request
            if (failEntryReads) return failingFlow()
            if (failEntryReadsAfterTheFirst && entryRequests.size > 1) return failingFlow()
            return entries.map { pageFor(it, request) }
        }

        override fun observeEntry(
            key: WorkoutHistoryKey,
            utcOffsetMillis: Long
        ): Flow<WorkoutHistoryEntry?> {
            entryKeys += key
            if (failEntryMetadata) return failingFlow()
            return entries.map { all -> all.firstOrNull { it.key == key } }
        }

        override fun observeEntrySets(
            key: WorkoutHistoryKey,
            request: WorkoutHistorySetRequest
        ): Flow<WorkoutHistorySetPage> {
            setRequests += request
            if (failSetReads) return failingFlow()
            return sets.map { all ->
                if (key is WorkoutHistoryKey.UnassignedSet) {
                    val single = all.firstOrNull { it.id == key.setId }
                    WorkoutHistorySetPage(
                        sets = single?.let { listOf(it) } ?: emptyList(),
                        hasMore = false
                    )
                } else {
                    val start = request.cursor?.let { cursor ->
                        val index = all.indexOfFirst { it.id == cursor.setId }
                        if (index < 0) all.size else index + 1
                    } ?: 0
                    WorkoutHistorySetPage(
                        sets = all.drop(start).take(request.pageSize),
                        hasMore = all.size - start > request.pageSize
                    )
                }
            }
        }

        private fun <T> failingFlow(): Flow<T> = flow { throw IllegalStateException("read failed") }

        private fun pageFor(
            all: List<WorkoutHistoryEntry>,
            request: WorkoutHistoryRequest
        ): WorkoutHistoryPage {
            val search = request.search.trim()
            val filtered = all.filter { entry ->
                val matchesSearch = search.isEmpty() ||
                    entry.note?.contains(search, ignoreCase = true) == true
                val matchesFrom = request.dayFrom?.let {
                    (entry.localEpochDay ?: Long.MIN_VALUE) >= it
                } ?: true
                val matchesTo = request.dayTo?.let {
                    (entry.localEpochDay ?: Long.MAX_VALUE) <= it
                } ?: true
                matchesSearch && matchesFrom && matchesTo
            }
            val start = request.cursor?.let { cursor ->
                val index = filtered.indexOfFirst { it.key == cursor.key }
                if (index < 0) filtered.size else index + 1
            } ?: 0
            return WorkoutHistoryPage(
                entries = filtered.drop(start).take(request.pageSize),
                hasMore = filtered.size - start > request.pageSize
            )
        }
    }
}
