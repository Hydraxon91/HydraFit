package com.hydrafit.app.feature.settings

import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.equipment.CatalogExerciseProfile
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.ExerciseProfile
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.transfer.PreviewWorkoutImport
import com.hydrafit.app.core.domain.transfer.WorkoutCsvFailure
import com.hydrafit.app.core.domain.transfer.WorkoutCsvParser
import com.hydrafit.app.core.domain.transfer.WorkoutCsvSource
import com.hydrafit.app.core.domain.transfer.WorkoutImportBucket
import com.hydrafit.app.core.domain.transfer.WorkoutImportChoices
import com.hydrafit.app.core.domain.transfer.WorkoutImportCommitFailure
import com.hydrafit.app.core.domain.transfer.WorkoutImportException
import com.hydrafit.app.core.domain.transfer.WorkoutImportExerciseChoice
import com.hydrafit.app.core.domain.transfer.WorkoutImportIssue
import com.hydrafit.app.core.domain.transfer.WorkoutImportPreview
import com.hydrafit.app.core.domain.transfer.WorkoutImportProvenance
import com.hydrafit.app.core.domain.transfer.WorkoutImportRepository
import com.hydrafit.app.core.domain.transfer.WorkoutImportResult
import com.hydrafit.app.core.domain.transfer.WorkoutImportSessionChoice
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.WorkoutSession
import com.hydrafit.app.core.domain.workout.WorkoutSessionRepository
import com.hydrafit.app.core.userdata.backup.BackupFileStore
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class WorkoutImportViewModelTest {

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
    fun unsupportedPlatformNeverReadsOrWrites() = runTest(dispatcher) {
        val store = FakeFileStore(isSupported = false, content = hevyCsv("Bench Press"))
        val repository = FakeImportRepository()
        val viewModel = viewModel(store = store, repository = repository, catalog = benchCatalog())

        assertFalse(viewModel.state.value.isSupported)
        viewModel.chooseFile("content://source")
        viewModel.apply()
        advanceUntilIdle()

        assertEquals(0, store.readCount)
        assertEquals(0, repository.commitCount)
        assertIs<WorkoutImportPhase.Idle>(viewModel.state.value.phase)
    }

    @Test
    fun typedCsvFailureSurfacesAsFailed() = runTest(dispatcher) {
        val viewModel = viewModel(store = FakeFileStore(content = "not a header"))

        viewModel.chooseFile("content://source")
        advanceUntilIdle()

        val phase = viewModel.state.value.phase
        assertIs<WorkoutImportPhase.Failed>(phase)
        assertEquals(WorkoutImportFailure.Csv(WorkoutCsvFailure.EMPTY_INPUT), phase.failure)
    }

    @Test
    fun rejectedRowBlocksUntilExcluded() = runTest(dispatcher) {
        val csv = "Date,Exercise,Weight (kg),Reps\n" +
            "2024-01-01,Bench Press,80,8\n" +
            "2024-01-01,Bench Press,80,abc\n"
        val viewModel = viewModel(store = FakeFileStore(content = csv), catalog = benchCatalog())

        viewModel.chooseFile("content://source")
        advanceUntilIdle()

        val first = review(viewModel)
        assertEquals(1, first.rejectedRows.size)
        assertTrue(first.hasIssue(WorkoutImportIssue.UNRESOLVED_EXCLUSION))
        assertFalse(first.canImport)

        viewModel.toggleRowIncluded(first.rejectedRows.single().sourceRowIndex)
        advanceUntilIdle()

        val second = review(viewModel)
        assertFalse(second.hasIssue(WorkoutImportIssue.UNRESOLVED_EXCLUSION))
        assertTrue(second.rejectedRows.single().excluded)
    }

    @Test
    fun unresolvedMappingBlocksImport() = runTest(dispatcher) {
        val viewModel = viewModel(
            store = FakeFileStore(content = hevyCsv("Mystery Lift")),
            catalog = benchCatalog()
        )

        viewModel.chooseHevyFile()
        advanceUntilIdle()

        val mapping = review(viewModel).mappings.single()
        assertIs<MappingState.Unmatched>(mapping.state)
        assertNull(mapping.chosenExerciseId)
        assertFalse(review(viewModel).canImport)
    }

    @Test
    fun uniqueCandidateSuggestsButStillRequiresLoadConfirmation() = runTest(dispatcher) {
        val viewModel = viewModel(
            store = FakeFileStore(content = hevyCsv("Bench Press")),
            catalog = benchCatalog()
        )

        viewModel.chooseHevyFile()
        advanceUntilIdle()

        val mapping = review(viewModel).mappings.single()
        assertEquals(MappingState.Suggested("bench", "Bench Press"), mapping.state)
        assertEquals("bench", mapping.chosenExerciseId)
        assertFalse(mapping.confirmedLoadMeaning)
        assertFalse(review(viewModel).canImport)
    }

    @Test
    fun ambiguousCandidateRequiresExplicitChoice() = runTest(dispatcher) {
        val catalog = FakeCatalog(
            exercises = listOf(exercise("row-a", "Row A"), exercise("row-b", "Row B")),
            candidates = listOf(profile("row-a", "Row"), profile("row-b", "Row"))
        )
        val viewModel = viewModel(
            store = FakeFileStore(content = hevyCsv("Row")),
            catalog = catalog
        )

        viewModel.chooseHevyFile()
        advanceUntilIdle()

        val mapping = review(viewModel).mappings.single()
        assertIs<MappingState.Ambiguous>(mapping.state)
        assertNull(mapping.chosenExerciseId)
        assertFalse(review(viewModel).canImport)
    }

    @Test
    fun dateOnlyDayRequiresAnExplicitTimeAndConfirmations() = runTest(dispatcher) {
        val csv = "Date,Exercise,Weight (kg),Reps\n2024-01-01,Bench Press,80,8\n"
        val viewModel = viewModel(store = FakeFileStore(content = csv), catalog = benchCatalog())

        viewModel.chooseFile("content://source")
        advanceUntilIdle()
        viewModel.confirmLoadMeaning("Bench Press")
        viewModel.setUnknownWarmupIsWorking("Bench Press", true)
        viewModel.acknowledgeHistoryEffects(true)
        advanceUntilIdle()
        assertFalse(review(viewModel).canImport)

        val key = review(viewModel).sessions.single().draftId
        viewModel.setSessionTime(key, 7 * 3600)
        advanceUntilIdle()
        assertFalse(review(viewModel).canImport)

        viewModel.confirmSession(key, true)
        advanceUntilIdle()
        assertTrue(review(viewModel).canImport)
    }

    @Test
    fun unchangedSourceProvenanceProducesSkipNotice() = runTest(dispatcher) {
        val csv = hevyCsv("Bench Press")
        val repository = FakeImportRepository()
        val provenance = provenanceFor(csv, "bench")
        repository.provenanceResult = provenance
        val viewModel = viewModel(
            store = FakeFileStore(content = csv),
            repository = repository,
            catalog = benchCatalog(),
            sessions = FakeSessionRepository(
                provenance.map { importedSession(it.importedSessionId) }
            )
        )

        viewModel.chooseHevyFile()
        advanceUntilIdle()

        val current = review(viewModel)
        assertTrue(current.notices.any { it.issue == WorkoutImportIssue.UNCHANGED_SOURCE_SKIPPED })
        assertFalse(current.canImport)
    }

    @Test
    fun twoSameDayWorkoutsStaySeparateSessions() = runTest(dispatcher) {
        val viewModel = viewModel(
            store = FakeFileStore(content = twoWorkoutHevyCsv()),
            catalog = benchCatalog()
        )

        viewModel.chooseHevyFile()
        advanceUntilIdle()

        val sessions = review(viewModel).sessions
        assertEquals(2, sessions.size)
        assertEquals(listOf("Morning", "Evening"), sessions.map { it.workoutTitle })
        assertEquals(listOf(2), sessions[0].rowIndexes)
        assertEquals(listOf(3), sessions[1].rowIndexes)
        assertEquals(2, sessions.map { it.draftId }.distinct().size)
    }

    @Test
    fun aTitlelessOrTitledBucketCanBeSplitIntoTwoSessionsAndRejoined() = runTest(dispatcher) {
        val viewModel = viewModel(
            store = FakeFileStore(content = hevyCsv("Bench Press")),
            catalog = benchCatalog()
        )

        viewModel.chooseHevyFile()
        advanceUntilIdle()
        val initial = review(viewModel)
        assertEquals(1, initial.sessions.size)
        assertEquals(listOf(2, 3), initial.sessions.single().rowIndexes)
        assertFalse(initial.sessions.single().split)

        viewModel.setRowSession(3, null)
        advanceUntilIdle()

        val split = review(viewModel)
        assertEquals(2, split.sessions.size)
        assertEquals(listOf(2), split.sessions[0].rowIndexes)
        assertEquals(listOf(3), split.sessions[1].rowIndexes)
        assertTrue(split.sessions.all { it.split })
        // The row can be moved to either draft of its bucket.
        assertEquals(2, split.rows.first { it.sourceRowIndex == 3 }.sessionOptions.size)

        // Moving it back merges the now-empty draft away.
        viewModel.setRowSession(3, split.sessions[0].draftId)
        advanceUntilIdle()
        val rejoined = review(viewModel)
        assertEquals(1, rejoined.sessions.size)
        assertEquals(listOf(2, 3), rejoined.sessions.single().rowIndexes)
        assertFalse(rejoined.sessions.single().split)
    }

    @Test
    fun aSplitBucketCommitsTwoSessionsAndOneBucket() = runTest(dispatcher) {
        val repository = FakeImportRepository()
        repository.result = WorkoutImportResult(
            importedSessions = 2,
            importedSets = 2,
            skippedSessions = 0
        )
        val viewModel = viewModel(
            store = FakeFileStore(content = hevyCsv("Bench Press")),
            repository = repository,
            catalog = benchCatalog()
        )

        viewModel.chooseHevyFile()
        advanceUntilIdle()
        viewModel.setRowSession(3, null)
        viewModel.confirmLoadMeaning("Bench Press")
        viewModel.acknowledgeHistoryEffects(true)
        review(viewModel).sessions.forEach { viewModel.confirmSession(it.draftId, true) }
        advanceUntilIdle()
        assertTrue(review(viewModel).canImport)

        viewModel.apply()
        advanceUntilIdle()

        assertEquals(2, repository.lastPreview?.sessions?.size)
        assertEquals(1, repository.lastPreview?.buckets?.size)
        assertIs<WorkoutImportPhase.Imported>(viewModel.state.value.phase)
    }

    @Test
    fun importingTwoSameDayWorkoutsCommitsTwoSessions() = runTest(dispatcher) {
        val repository = FakeImportRepository()
        repository.result = WorkoutImportResult(
            importedSessions = 2,
            importedSets = 2,
            skippedSessions = 0
        )
        val viewModel = viewModel(
            store = FakeFileStore(content = twoWorkoutHevyCsv()),
            repository = repository,
            catalog = benchCatalog()
        )

        viewModel.chooseHevyFile()
        advanceUntilIdle()
        viewModel.confirmLoadMeaning("Bench Press")
        viewModel.acknowledgeHistoryEffects(true)
        review(viewModel).sessions.forEach { viewModel.confirmSession(it.draftId, true) }
        advanceUntilIdle()
        assertTrue(review(viewModel).canImport)

        viewModel.apply()
        advanceUntilIdle()

        assertEquals(2, repository.lastPreview?.sessions?.size)
        val phase = viewModel.state.value.phase
        assertIs<WorkoutImportPhase.Imported>(phase)
        assertEquals(2, phase.result.importedSessions)
    }

    @Test
    fun repeatImportMarksBothSameDayWorkoutsSkipped() = runTest(dispatcher) {
        val csv = twoWorkoutHevyCsv()
        val repository = FakeImportRepository()
        val provenance = provenanceFor(csv, "bench")
        repository.provenanceResult = provenance
        val viewModel = viewModel(
            store = FakeFileStore(content = csv),
            repository = repository,
            catalog = benchCatalog(),
            sessions = FakeSessionRepository(
                provenance.map { importedSession(it.importedSessionId) }
            )
        )

        viewModel.chooseHevyFile()
        advanceUntilIdle()

        val current = review(viewModel)
        assertEquals(
            2,
            current.notices.count { it.issue == WorkoutImportIssue.UNCHANGED_SOURCE_SKIPPED }
        )
        assertFalse(current.canImport)
    }

    @Test
    fun providerWithoutTitlesGroupsOneSessionPerDay() = runTest(dispatcher) {
        val csv = "Date,Exercise,Weight (kg),Reps\n" +
            "2024-01-01,Bench Press,80,8\n" +
            "2024-01-01,Bench Press,70,10\n"
        val viewModel = viewModel(store = FakeFileStore(content = csv), catalog = benchCatalog())

        viewModel.chooseFile("content://source")
        advanceUntilIdle()

        val sessions = review(viewModel).sessions
        assertEquals(1, sessions.size)
        assertNull(sessions.single().workoutTitle)
        assertEquals(listOf(2, 3), sessions.single().rowIndexes)
    }

    @Test
    fun successfulCommitReportsCountsAndClearsTheReview() = runTest(dispatcher) {
        val repository = FakeImportRepository()
        repository.result = WorkoutImportResult(
            importedSessions = 1,
            importedSets = 2,
            skippedSessions = 0
        )
        val viewModel = viewModel(
            store = FakeFileStore(content = hevyCsv("Bench Press")),
            repository = repository,
            catalog = benchCatalog()
        )

        confirmReadyToImport(viewModel)
        assertTrue(review(viewModel).canImport)

        viewModel.apply()
        advanceUntilIdle()

        val phase = viewModel.state.value.phase
        assertIs<WorkoutImportPhase.Imported>(phase)
        assertEquals(1, phase.result.importedSessions)
        assertEquals(2, phase.result.importedSets)
        assertEquals(1, repository.commitCount)
    }

    @Test
    fun importExceptionKeepsTheReviewAndSurfacesTheFailure() = runTest(dispatcher) {
        val repository = FakeImportRepository()
        repository.commitFailure =
            WorkoutImportException(WorkoutImportCommitFailure.SOURCE_CHANGED, "changed")
        val viewModel = viewModel(
            store = FakeFileStore(content = hevyCsv("Bench Press")),
            repository = repository,
            catalog = benchCatalog()
        )

        confirmReadyToImport(viewModel)
        viewModel.apply()
        advanceUntilIdle()

        val phase = viewModel.state.value.phase
        assertIs<WorkoutImportPhase.Reviewing>(phase)
        assertEquals(WorkoutImportCommitFailure.SOURCE_CHANGED, phase.review.commitFailure)
        assertEquals(1, repository.commitCount)
    }

    @Test
    fun secondApplyWhileInProgressPerformsNoSecondCommit() = runTest(dispatcher) {
        val repository = FakeImportRepository()
        val gate = CompletableDeferred<Unit>()
        repository.commitGate = gate
        val viewModel = viewModel(
            store = FakeFileStore(content = hevyCsv("Bench Press")),
            repository = repository,
            catalog = benchCatalog()
        )

        confirmReadyToImport(viewModel)
        viewModel.apply()
        viewModel.apply()
        advanceUntilIdle()
        assertEquals(1, repository.commitCount)

        gate.complete(Unit)
        advanceUntilIdle()
        assertIs<WorkoutImportPhase.Imported>(viewModel.state.value.phase)
    }

    @Test
    fun cancellationPropagatesWithoutAFailedState() = runTest(dispatcher) {
        val store = FakeFileStore(content = hevyCsv("Bench Press"))
        store.readFailure = CancellationException("cancelled")
        val viewModel = viewModel(store = store, catalog = benchCatalog())

        viewModel.chooseFile("content://source")
        advanceUntilIdle()

        assertFalse(viewModel.state.value.phase is WorkoutImportPhase.Failed)
    }

    @Test
    fun lateReadCannotOverwriteNewerState() = runTest(dispatcher) {
        val store = FakeFileStore(content = hevyCsv("Bench Press"))
        val gate = CompletableDeferred<Unit>()
        store.readGate = gate
        val viewModel = viewModel(store = store, catalog = benchCatalog())

        viewModel.chooseFile("content://source")
        advanceUntilIdle()
        viewModel.reset()
        gate.complete(Unit)
        advanceUntilIdle()

        assertIs<WorkoutImportPhase.Idle>(viewModel.state.value.phase)
    }

    private fun WorkoutImportViewModel.chooseHevyFile() {
        selectProvider(WorkoutCsvSource.HEVY)
        chooseFile("content://source")
    }

    private fun TestScope.confirmReadyToImport(viewModel: WorkoutImportViewModel) {
        viewModel.chooseHevyFile()
        advanceUntilIdle()
        viewModel.confirmLoadMeaning("Bench Press")
        viewModel.acknowledgeHistoryEffects(true)
        val key = review(viewModel).sessions.single().draftId
        viewModel.confirmSession(key, true)
        advanceUntilIdle()
    }

    @Test
    fun invalidOffsetTextBlocksAnOtherwiseImportableReview() = runTest(dispatcher) {
        val viewModel = viewModel(
            store = FakeFileStore(content = hevyCsv("Bench Press")),
            catalog = benchCatalog()
        )

        confirmReadyToImport(viewModel)
        assertTrue(review(viewModel).canImport)
        val key = review(viewModel).sessions.single().draftId

        viewModel.setSessionOffset(key, "abc")
        advanceUntilIdle()

        assertEquals("abc", review(viewModel).sessions.single().utcOffsetText)
        assertTrue(review(viewModel).problems.any { it is ReviewProblem.InvalidOffset })
        assertFalse(review(viewModel).canImport)
    }

    @Test
    fun offsetTextAppliesValidMinutesAndKeepsTheLastValidValue() = runTest(dispatcher) {
        val viewModel = viewModel(
            store = FakeFileStore(content = hevyCsv("Bench Press")),
            catalog = benchCatalog()
        )

        viewModel.chooseHevyFile()
        advanceUntilIdle()
        val key = review(viewModel).sessions.single().draftId

        viewModel.setSessionOffset(key, "120")
        advanceUntilIdle()
        assertEquals("120", review(viewModel).sessions.single().utcOffsetText)
        assertEquals(120, review(viewModel).sessions.single().utcOffsetMinutes)
        assertFalse(review(viewModel).problems.any { it is ReviewProblem.InvalidOffset })

        viewModel.setSessionOffset(key, "abc")
        advanceUntilIdle()
        assertEquals("abc", review(viewModel).sessions.single().utcOffsetText)
        assertEquals(120, review(viewModel).sessions.single().utcOffsetMinutes)

        viewModel.setSessionOffset(key, "90")
        advanceUntilIdle()
        assertEquals(90, review(viewModel).sessions.single().utcOffsetMinutes)
        assertFalse(review(viewModel).problems.any { it is ReviewProblem.InvalidOffset })
    }

    @Test
    fun emptyOffsetTextMeansZero() = runTest(dispatcher) {
        val viewModel = viewModel(
            store = FakeFileStore(content = hevyCsv("Bench Press")),
            catalog = benchCatalog()
        )

        viewModel.chooseHevyFile()
        advanceUntilIdle()
        val key = review(viewModel).sessions.single().draftId

        viewModel.setSessionOffset(key, "120")
        advanceUntilIdle()
        viewModel.setSessionOffset(key, "")
        advanceUntilIdle()

        assertEquals("", review(viewModel).sessions.single().utcOffsetText)
        assertEquals(0, review(viewModel).sessions.single().utcOffsetMinutes)
        assertFalse(review(viewModel).problems.any { it is ReviewProblem.InvalidOffset })
    }

    @Test
    fun outOfRangeOffsetTextIsReportedAndKeepsTheLastValidOffset() = runTest(dispatcher) {
        val viewModel = viewModel(
            store = FakeFileStore(content = hevyCsv("Bench Press")),
            catalog = benchCatalog()
        )

        viewModel.chooseHevyFile()
        advanceUntilIdle()
        val key = review(viewModel).sessions.single().draftId

        viewModel.setSessionOffset(key, "120")
        advanceUntilIdle()
        viewModel.setSessionOffset(key, "5000")
        advanceUntilIdle()

        assertEquals("5000", review(viewModel).sessions.single().utcOffsetText)
        assertEquals(120, review(viewModel).sessions.single().utcOffsetMinutes)
        assertTrue(review(viewModel).problems.any { it is ReviewProblem.InvalidOffset })
    }

    @Test
    fun anOverlapRefusalRefreshesHistoryBeforeTheRetry() = runTest(dispatcher) {
        val repository = FakeImportRepository()
        repository.commitFailure =
            WorkoutImportException(WorkoutImportCommitFailure.SESSION_OVERLAP, "overlap")
        val sessions = FakeSessionRepository()
        val viewModel = viewModel(
            store = FakeFileStore(content = hevyCsv("Bench Press")),
            repository = repository,
            catalog = benchCatalog(),
            sessions = sessions
        )

        confirmReadyToImport(viewModel)
        assertTrue(review(viewModel).canImport)

        // A session appears under the review; the commit refuses and the review is refreshed so the
        // overlap is now a visible blocking problem rather than a silent retry.
        sessions.replaceAll(listOf(WorkoutSession("existing", 0L, Long.MAX_VALUE, 0L)))
        viewModel.apply()
        advanceUntilIdle()

        val phase = viewModel.state.value.phase
        assertIs<WorkoutImportPhase.Reviewing>(phase)
        assertEquals(WorkoutImportCommitFailure.SESSION_OVERLAP, phase.review.commitFailure)
        assertTrue(
            phase.review.problems.any {
                it is ReviewProblem.Domain && it.problem.issue == WorkoutImportIssue.SESSION_OVERLAP
            },
            phase.review.problems.toString()
        )
        assertFalse(phase.review.canImport)
    }

    @Test
    fun aBucketSplitBeforeIsRebuiltFromItsStoredPartitionOnASecondRead() = runTest(dispatcher) {
        val repository = FakeImportRepository()
        val viewModel = viewModel(
            store = FakeFileStore(content = hevyCsv("Bench Press")),
            repository = repository,
            catalog = benchCatalog()
        )

        // Split the bucket's two rows into two sessions, then import them.
        viewModel.chooseHevyFile()
        advanceUntilIdle()
        viewModel.confirmLoadMeaning("Bench Press")
        viewModel.acknowledgeHistoryEffects(true)
        val firstDraft = review(viewModel).sessions.single().draftId
        viewModel.confirmSession(firstDraft, true)
        viewModel.setRowSession(3, null)
        advanceUntilIdle()
        val drafts = review(viewModel).sessions
        assertEquals(2, drafts.size)
        viewModel.confirmSession(drafts.first { it.draftId != firstDraft }.draftId, true)
        advanceUntilIdle()
        assertTrue(review(viewModel).canImport, review(viewModel).problems.toString())
        viewModel.apply()
        advanceUntilIdle()
        assertIs<WorkoutImportPhase.Imported>(viewModel.state.value.phase)

        // A second read of the byte-identical file must rebuild the split the user approved, so it is
        // skipped — not propose one merged session, differ from the stored partition, and refuse the
        // whole file as a changed source.
        viewModel.chooseHevyFile()
        advanceUntilIdle()
        viewModel.confirmLoadMeaning("Bench Press")
        viewModel.acknowledgeHistoryEffects(true)
        review(viewModel).sessions.forEach { viewModel.confirmSession(it.draftId, true) }
        advanceUntilIdle()

        val second = review(viewModel)
        assertEquals(2, second.sessions.size)
        assertTrue(second.problems.isEmpty(), second.problems.toString())
        assertEquals(
            2,
            second.notices.count { it.issue == WorkoutImportIssue.REMOVED_HISTORY_SKIPPED },
            second.notices.toString()
        )
        assertFalse(second.canImport)
    }

    @Test
    fun aStoredExclusionIsRestoredOnASecondReadSoTheBucketStillSkips() = runTest(dispatcher) {
        val repository = FakeImportRepository()
        val viewModel = viewModel(
            store = FakeFileStore(content = hevyCsv("Bench Press")),
            repository = repository,
            catalog = benchCatalog()
        )

        // Import with the bucket's second row excluded, so the stored partition carries an exclusion.
        viewModel.chooseHevyFile()
        advanceUntilIdle()
        viewModel.confirmLoadMeaning("Bench Press")
        viewModel.acknowledgeHistoryEffects(true)
        viewModel.toggleRowIncluded(3)
        val draft = review(viewModel).sessions.single().draftId
        viewModel.confirmSession(draft, true)
        advanceUntilIdle()
        assertTrue(review(viewModel).canImport, review(viewModel).problems.toString())
        viewModel.apply()
        advanceUntilIdle()
        assertIs<WorkoutImportPhase.Imported>(viewModel.state.value.phase)

        // A fresh read must restore the exclusion together with the partition; without it the proposed
        // partition would differ from the stored one and block the file as a changed source.
        viewModel.chooseHevyFile()
        advanceUntilIdle()
        viewModel.confirmLoadMeaning("Bench Press")
        viewModel.acknowledgeHistoryEffects(true)
        review(viewModel).sessions.forEach { viewModel.confirmSession(it.draftId, true) }
        advanceUntilIdle()

        val second = review(viewModel)
        assertEquals(setOf(3), second.excludedRows, second.excludedRows.toString())
        assertTrue(second.problems.isEmpty(), second.problems.toString())
        assertEquals(
            1,
            second.notices.count { it.issue == WorkoutImportIssue.REMOVED_HISTORY_SKIPPED },
            second.notices.toString()
        )
        assertFalse(second.canImport)
    }

    private fun review(viewModel: WorkoutImportViewModel): WorkoutImportReview {
        val phase = viewModel.state.value.phase
        assertIs<WorkoutImportPhase.Reviewing>(phase, "expected a review but was $phase")
        return phase.review
    }

    private fun WorkoutImportReview.hasIssue(issue: WorkoutImportIssue): Boolean =
        problems.any { it is ReviewProblem.Domain && it.problem.issue == issue }

    private fun viewModel(
        store: BackupFileStore = FakeFileStore(),
        repository: WorkoutImportRepository = FakeImportRepository(),
        catalog: ExerciseCatalog = FakeCatalog(emptyList(), emptyList()),
        sessions: WorkoutSessionRepository = FakeSessionRepository()
    ): WorkoutImportViewModel = WorkoutImportViewModel(
        fileStore = store,
        importRepository = repository,
        exerciseCatalog = catalog,
        sessionRepository = sessions
    )

    private fun benchCatalog(): FakeCatalog = FakeCatalog(
        exercises = listOf(exercise("bench", "Bench Press")),
        candidates = listOf(profile("bench", "Bench Press"))
    )
}

private fun hevyCsv(exerciseName: String): String = "title,start_time,exercise_title,set_type," +
    "weight_kg,reps\n" +
    "Push,\"01 Jan 2024, 07:30\",$exerciseName,normal,80,8\n" +
    "Push,\"01 Jan 2024, 07:35\",$exerciseName,normal,80,8\n"

private fun twoWorkoutHevyCsv(): String = "title,start_time,exercise_title,set_type," +
    "weight_kg,reps\n" +
    "Morning,\"01 Jan 2024, 07:30\",Bench Press,normal,80,8\n" +
    "Evening,\"01 Jan 2024, 18:30\",Bench Press,normal,60,10\n"

private fun provenanceFor(text: String, exerciseId: String): List<WorkoutImportProvenance> {
    val parsed = WorkoutCsvParser.parse(WorkoutCsvSource.HEVY, text)
    val name = parsed.rows.first().exerciseName
    val sessions = parsed.rows.groupBy { it.workoutTitle to it.day }.values.map { rows ->
        WorkoutImportSessionChoice(
            sourceRows = rows.map { it.sourceRowIndex },
            utcOffsetMinutes = 0,
            confirmedSession = true,
            confirmedInferredSetTimes = true
        )
    }
    val choices = WorkoutImportChoices(
        exercises = mapOf(
            name to WorkoutImportExerciseChoice(
                exerciseId = exerciseId,
                loadKind = LoadKind.EXTERNAL,
                useSourceWeight = true,
                confirmedLoadMeaning = true,
                unknownWarmupIsWorking = true
            )
        ),
        sessions = sessions,
        acknowledgedUnsupportedFields = true,
        acknowledgedHistoryEffects = true
    )
    val preview = PreviewWorkoutImport.build(
        parsed = parsed,
        choices = choices,
        catalog = listOf(exercise(exerciseId, name))
    )
    return preview.sessions.mapIndexed { index, session ->
        WorkoutImportProvenance(
            source = WorkoutCsvSource.HEVY,
            candidateKey = session.candidateKey,
            content = session.content,
            importedSessionId = "session-${index + 1}"
        )
    }
}

private fun importedSession(id: String): WorkoutSession = WorkoutSession(
    id = id,
    startedAtMillis = 0L,
    endedAtMillis = null,
    localEpochDay = 0L
)

private fun exercise(id: String, name: String): Exercise = Exercise(
    id = id,
    name = name,
    requiredEquipment = emptySet(),
    primaryMuscles = emptySet()
)

private fun profile(id: String, name: String): CatalogExerciseProfile = CatalogExerciseProfile(
    catalogId = id,
    canonicalName = name,
    displayName = name,
    profile = ExerciseProfile(
        equipment = emptySet(),
        movementPattern = MovementPattern.CORE,
        involvements = emptyMap(),
        loadCapability = ExerciseLoadCapability.EXTERNAL,
        isUnilateral = false
    )
)

private class FakeCatalog(
    private val exercises: List<Exercise>,
    private val candidates: List<CatalogExerciseProfile>
) : ExerciseCatalog {
    override suspend fun all(): List<Exercise> = exercises

    override suspend fun profileCandidates(): List<CatalogExerciseProfile> = candidates
}

private class FakeFileStore(
    override val isSupported: Boolean = true,
    private var content: String = ""
) : BackupFileStore {
    var readCount = 0
    var readGate: CompletableDeferred<Unit>? = null
    var readFailure: Throwable? = null

    override suspend fun read(handle: String): String {
        readCount++
        readGate?.await()
        readFailure?.let { throw it }
        return content
    }

    override suspend fun write(handle: String, text: String) = Unit
}

private class FakeImportRepository : WorkoutImportRepository {
    var provenanceResult: List<WorkoutImportProvenance> = emptyList()
    var bucketResult: List<WorkoutImportBucket> = emptyList()
    var result: WorkoutImportResult = WorkoutImportResult(0, 0, 0)
    var commitCount = 0
    var commitGate: CompletableDeferred<Unit>? = null
    var commitFailure: Throwable? = null
    var lastPreview: WorkoutImportPreview? = null

    override suspend fun buckets(
        source: WorkoutCsvSource,
        candidateKeys: Set<String>
    ): List<WorkoutImportBucket> = bucketResult

    override suspend fun provenance(
        source: WorkoutCsvSource,
        candidateKeys: Set<String>
    ): List<WorkoutImportProvenance> = provenanceResult

    override suspend fun commit(preview: WorkoutImportPreview): WorkoutImportResult {
        commitCount++
        lastPreview = preview
        commitGate?.await()
        commitFailure?.let { throw it }
        // Mirror the repository: a committed bucket is recorded with one link per session it holds.
        bucketResult = preview.buckets.map { bucket ->
            bucket.copy(
                importedSessionIds = preview.sessions
                    .filter { it.candidateKey == bucket.candidateKey }
                    .mapIndexed { index, _ -> "session-$index" }
            )
        }
        return result
    }
}

private class FakeSessionRepository(private var sessions: List<WorkoutSession> = emptyList()) :
    WorkoutSessionRepository {
    override suspend fun create(session: WorkoutSession) = Unit

    override suspend fun end(id: String, endedAtMillis: Long) = Unit

    override suspend fun open(): WorkoutSession? = null

    override fun openFlow(): Flow<WorkoutSession?> = emptyFlow()

    override suspend fun all(): List<WorkoutSession> = sessions

    fun replaceAll(replacement: List<WorkoutSession>) {
        sessions = replacement
    }
}
