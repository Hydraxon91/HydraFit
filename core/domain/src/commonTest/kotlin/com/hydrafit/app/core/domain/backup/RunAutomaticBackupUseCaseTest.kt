package com.hydrafit.app.core.domain.backup

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest

class RunAutomaticBackupUseCaseTest {

    @Test
    fun doesNothingWhenAutomationIsOff() = runTest {
        val harness = harness(state = AutomaticBackupState())

        val outcome = harness.useCase("1.0")

        assertEquals(AutomaticBackupOutcome.Disabled, outcome)
        assertEquals(0, harness.folder.createCalls)
        assertEquals(0, harness.stateStore.saves)
    }

    @Test
    fun doesNothingBeforeTheIntervalElapses() = runTest {
        val harness = harness(state = enabledState(lastSuccessMillis = TEST_NOW - 1_000L))

        val outcome = harness.useCase("1.0")

        assertEquals(AutomaticBackupOutcome.NotDue, outcome)
        assertEquals(0, harness.folder.createCalls)
    }

    @Test
    fun forceRunsEvenWhenNotDue() = runTest {
        val harness = harness(state = enabledState(lastSuccessMillis = TEST_NOW - 1_000L))

        val outcome = harness.useCase("1.0", force = true)

        assertIs<AutomaticBackupOutcome.Created>(outcome)
    }

    @Test
    fun writesVerifiesRecordsAndPrunesBeyondRetention() = runTest {
        val harness = harness(
            state = enabledState(documents = (1..8).map { verified("doc-$it", it.toLong()) })
        )

        val outcome = harness.useCase("1.0")

        assertIs<AutomaticBackupOutcome.Created>(outcome)
        assertTrue(outcome.displayName.startsWith("hydrafit-auto-"))
        assertTrue(outcome.displayName.endsWith(".json"))
        assertEquals(2, outcome.prunedCount)
        assertEquals(0, outcome.prunePendingCount)
        assertEquals(listOf("doc-1", "doc-2"), harness.folder.deletedIds)
        val state = harness.stateStore.state
        assertEquals(TEST_NOW, state.lastSuccessMillis)
        assertNull(state.lastFailure)
        assertEquals(7, state.documents.size)
        assertTrue(state.documents.all { it.verified })
    }

    @Test
    fun keepsTheSnapshotUnverifiedWhenTheReadBackDiffers() = runTest {
        val folder = FakeBackupFolderStore().apply {
            readMutator = { bytes -> bytes.copyOf(bytes.size + 1) }
        }
        val harness = harness(state = enabledState(), folder = folder)

        val outcome = harness.useCase("1.0")

        assertEquals(
            AutomaticBackupOutcome.Failed(AutomaticBackupFailureKind.WRITE_UNVERIFIED),
            outcome
        )
        val state = harness.stateStore.state
        assertNull(state.lastSuccessMillis)
        assertEquals(AutomaticBackupFailureKind.WRITE_UNVERIFIED, state.lastFailure?.kind)
        assertEquals(1, state.documents.size)
        assertFalse(state.documents.single().verified)
        assertEquals(emptyList(), folder.deletedIds)
    }

    @Test
    fun keepsTheSnapshotUnverifiedWhenTheReadBackFails() = runTest {
        val folder = FakeBackupFolderStore().apply { failReads = true }
        val harness = harness(state = enabledState(), folder = folder)

        val outcome = harness.useCase("1.0")

        assertEquals(
            AutomaticBackupOutcome.Failed(AutomaticBackupFailureKind.WRITE_UNVERIFIED),
            outcome
        )
        val state = harness.stateStore.state
        assertNull(state.lastSuccessMillis)
        assertFalse(state.documents.single().verified)
    }

    @Test
    fun recordsAWriteFailureAndNothingUsable() = runTest {
        val folder = FakeBackupFolderStore().apply { failCreates = true }
        val harness = harness(state = enabledState(), folder = folder)

        val outcome = harness.useCase("1.0")

        assertEquals(
            AutomaticBackupOutcome.Failed(AutomaticBackupFailureKind.WRITE_FAILED),
            outcome
        )
        val state = harness.stateStore.state
        assertEquals(emptyList(), state.documents)
        assertEquals(AutomaticBackupFailureKind.WRITE_FAILED, state.lastFailure?.kind)
        assertTrue(AutomaticBackupFailureKind.WRITE_FAILED.retryable)
    }

    @Test
    fun refusesWithoutAConfiguredFolder() = runTest {
        val harness = harness(state = enabledState(handle = null))

        val outcome = harness.useCase("1.0")

        assertEquals(
            AutomaticBackupOutcome.Failed(AutomaticBackupFailureKind.FOLDER_NOT_CONFIGURED),
            outcome
        )
        assertEquals(0, harness.folder.createCalls)
    }

    @Test
    fun refusesWhenTheFolderGrantIsGone() = runTest {
        val folder = FakeBackupFolderStore().apply {
            capabilities = FolderCapabilities.Unavailable(FolderUnavailableReason.PERMISSION_LOST)
        }
        val harness = harness(state = enabledState(), folder = folder)

        val outcome = harness.useCase("1.0")

        assertEquals(
            AutomaticBackupOutcome.Failed(AutomaticBackupFailureKind.FOLDER_UNAVAILABLE),
            outcome
        )
        assertEquals(0, folder.createCalls)
    }

    @Test
    fun mapsAnUnsupportedProviderToItsOwnFailure() = runTest {
        val folder = FakeBackupFolderStore().apply {
            capabilities =
                FolderCapabilities.Unavailable(FolderUnavailableReason.UNSUPPORTED_PROVIDER)
        }
        val harness = harness(state = enabledState(), folder = folder)

        val outcome = harness.useCase("1.0")

        assertEquals(
            AutomaticBackupOutcome.Failed(AutomaticBackupFailureKind.FOLDER_UNSUPPORTED),
            outcome
        )
    }

    @Test
    fun reconcilesAPreviouslyUnverifiedSnapshotBeforeWriting() = runTest {
        val folder = FakeBackupFolderStore()
        folder.documents["doc-old"] = validAutomaticBackupText().encodeToByteArray()
        val harness = harness(
            state = enabledState(
                lastSuccessMillis = TEST_NOW - 2 * AutomaticBackupPolicy.INTERVAL_MILLIS,
                documents = listOf(unverified("doc-old", 0L))
            ),
            folder = folder
        )

        val outcome = harness.useCase("1.0")

        assertIs<AutomaticBackupOutcome.Created>(outcome)
        val documents = harness.stateStore.state.documents
        assertEquals(2, documents.size)
        assertTrue(documents.all { it.verified })
    }

    @Test
    fun neverPrunesAnUnverifiedSnapshotEvenBeyondRetention() = runTest {
        val folder = FakeBackupFolderStore()
        folder.documents["doc-bad"] = "{not-json".encodeToByteArray()
        val harness = harness(
            state = enabledState(
                lastSuccessMillis = TEST_NOW - AutomaticBackupPolicy.INTERVAL_MILLIS,
                documents = (1..9).map { verified("doc-$it", it.toLong()) } +
                    listOf(unverified("doc-bad", 0L))
            ),
            folder = folder
        )

        val outcome = harness.useCase("1.0")

        assertIs<AutomaticBackupOutcome.Created>(outcome)
        assertEquals(3, outcome.prunedCount)
        assertEquals(listOf("doc-1", "doc-2", "doc-3"), folder.deletedIds)
        val bad = harness.stateStore.state.documents.first { it.documentId == "doc-bad" }
        assertFalse(bad.verified)
    }

    @Test
    fun reportsPruneFailuresWithoutLosingTheLedger() = runTest {
        val folder = FakeBackupFolderStore().apply { failDeletes = true }
        val harness = harness(
            state = enabledState(documents = (1..8).map { verified("doc-$it", it.toLong()) }),
            folder = folder
        )

        val outcome = harness.useCase("1.0")

        assertIs<AutomaticBackupOutcome.Created>(outcome)
        assertEquals(0, outcome.prunedCount)
        assertEquals(2, outcome.prunePendingCount)
        assertEquals(9, harness.stateStore.state.documents.size)
        assertEquals(TEST_NOW, harness.stateStore.state.lastSuccessMillis)
    }

    @Test
    fun serializesConcurrentRuns() = runTest {
        val gate = CompletableDeferred<Unit>()
        val folder = FakeBackupFolderStore().apply { readGate = gate }
        val harness = harness(state = enabledState(), folder = folder)

        val first = async { harness.useCase("1.0") }
        runCurrent()
        val second = async { harness.useCase("1.0") }
        runCurrent()
        gate.complete(Unit)
        advanceUntilIdle()

        assertIs<AutomaticBackupOutcome.Created>(first.await())
        assertEquals(AutomaticBackupOutcome.NotDue, second.await())
        assertEquals(1, folder.documents.size)
    }

    @Test
    fun disableDuringARunIsNotClobberedByTheRun() = runTest {
        val gate = CompletableDeferred<Unit>()
        val folder = FakeBackupFolderStore().apply { readGate = gate }
        val harness = harness(state = enabledState(), folder = folder)
        val scheduler = FakeAutomaticBackupScheduler()

        val run = async { harness.useCase("1.0") }
        runCurrent()
        val enable = SetAutomaticBackupsEnabledUseCase(folder, harness.stateStore, scheduler)
        assertEquals(AutomaticBackupEnableResult.Disabled, enable(false))
        gate.complete(Unit)
        advanceUntilIdle()

        assertIs<AutomaticBackupOutcome.Created>(run.await())
        val state = harness.stateStore.state
        assertFalse(state.enabled)
        assertEquals(1, scheduler.cancelCalls)
        assertEquals(TEST_NOW, state.lastSuccessMillis)
        assertTrue(state.documents.single().verified)
    }

    @Test
    fun aFolderChangeDuringARunKeepsTheTwoLedgersApart() = runTest {
        val gate = CompletableDeferred<Unit>()
        val folder = FakeBackupFolderStore().apply { readGate = gate }
        val harness = harness(state = enabledState(handle = "tree://old"), folder = folder)

        val run = async { harness.useCase("1.0") }
        runCurrent()
        val configure = ConfigureAutomaticBackupFolderUseCase(folder, harness.stateStore)
        assertEquals(FolderCapabilities.Ready, configure("tree://new"))
        gate.complete(Unit)
        advanceUntilIdle()

        assertIs<AutomaticBackupOutcome.Created>(run.await())
        val state = harness.stateStore.state
        assertEquals("tree://new", state.folderHandle)
        // The old folder's write is neither recorded nor promoted into the new folder's ledger.
        assertEquals(emptyList(), state.documents)
        assertNull(state.lastSuccessMillis)
    }

    private fun TestScope.harness(
        state: AutomaticBackupState = AutomaticBackupState(),
        folder: FakeBackupFolderStore = FakeBackupFolderStore(),
        file: BackupFile = emptyBackupFile(),
        now: Long = TEST_NOW
    ): Harness {
        val stateStore = FakeAutomaticBackupStateStore(state)
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val useCase = RunAutomaticBackupUseCase(
            exportBackup = ExportBackupUseCase(StaticBackupRepository(file), dispatcher),
            verifySnapshot = VerifyBackupSnapshotUseCase(
                BackupValidator(automaticBackupTestCatalog())
            ),
            folderStore = folder,
            stateStore = stateStore,
            timeProvider = { now },
            random = Random(7),
            dispatcher = dispatcher
        )
        return Harness(folder, stateStore, useCase)
    }

    private class Harness(
        val folder: FakeBackupFolderStore,
        val stateStore: FakeAutomaticBackupStateStore,
        val useCase: RunAutomaticBackupUseCase
    )

    private fun enabledState(
        handle: String? = "tree://folder",
        lastSuccessMillis: Long? = null,
        documents: List<RecordedBackupDocument> = emptyList()
    ) = AutomaticBackupState(
        enabled = true,
        folderHandle = handle,
        lastSuccessMillis = lastSuccessMillis,
        documents = documents
    )

    private fun verified(id: String, createdAtMillis: Long) =
        RecordedBackupDocument(id, "$id.json", createdAtMillis, verified = true)

    private fun unverified(id: String, createdAtMillis: Long) =
        RecordedBackupDocument(id, "$id.json", createdAtMillis, verified = false)

    private companion object {
        const val TEST_NOW = 1_700_000_000_000L
    }
}
