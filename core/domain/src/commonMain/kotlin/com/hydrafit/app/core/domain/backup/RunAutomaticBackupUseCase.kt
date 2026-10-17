package com.hydrafit.app.core.domain.backup

import com.hydrafit.app.core.domain.time.TimeProvider
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Runs one automatic snapshot attempt end to end: enabled/due gate, single-flight against a
 * concurrent manual run, folder capability probe, export, write, same-run read-back verification,
 * ledger update, reconciliation of earlier unverified writes and retention pruning.
 *
 * Ordering guarantees: a new snapshot is recorded before it is read back, so an interrupted run
 * leaves an identity that reconciliation can settle and pruning can never select blind; a snapshot
 * is read back byte-identical before it counts as a success; pruning runs only after a new verified
 * success and deletes only recorded identities. Cancellation is rethrown, never recorded as a
 * failure.
 */
class RunAutomaticBackupUseCase(
    private val exportBackup: ExportBackupUseCase,
    private val verifySnapshot: VerifyBackupSnapshotUseCase,
    private val folderStore: BackupFolderStore,
    private val stateStore: AutomaticBackupStateStore,
    private val timeProvider: TimeProvider,
    private val random: Random = Random.Default,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val mutex = Mutex()

    /**
     * @param force bypasses the due check for an explicit user request ("Back up now"); it never
     * bypasses the enabled check or the single-flight guard.
     */
    suspend operator fun invoke(
        appVersion: String,
        force: Boolean = false
    ): AutomaticBackupOutcome = mutex.withLock {
        withContext(dispatcher) {
            run(appVersion, force)
        }
    }

    private suspend fun run(appVersion: String, force: Boolean): AutomaticBackupOutcome {
        val state = stateStore.load()
        if (!state.enabled) return AutomaticBackupOutcome.Disabled
        val now = timeProvider.nowMillis()
        if (!force && !AutomaticBackupPolicy.isDue(state, now)) return AutomaticBackupOutcome.NotDue

        val reconciled = reconcileUnverified(state)
        val handle = reconciled.folderHandle
            ?: return fail(reconciled, AutomaticBackupFailureKind.FOLDER_NOT_CONFIGURED, now)

        when (val capabilities = probeCapabilities(handle)) {
            null -> return fail(reconciled, AutomaticBackupFailureKind.FOLDER_UNAVAILABLE, now)
            is FolderCapabilities.Unavailable ->
                return fail(reconciled, capabilities.reason.toFailureKind(), now)
            FolderCapabilities.Ready -> Unit
        }

        val bytes = try {
            exportBackup(appVersion, now).encodeToByteArray()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (tooLarge: BackupException) {
            val kind = if (tooLarge.failure == BackupFailure.TOO_LARGE) {
                AutomaticBackupFailureKind.TOO_LARGE
            } else {
                AutomaticBackupFailureKind.EXPORT_FAILED
            }
            return fail(reconciled, kind, now)
        } catch (_: Throwable) {
            return fail(reconciled, AutomaticBackupFailureKind.EXPORT_FAILED, now)
        }

        val document = try {
            folderStore.create(handle, displayName(now), bytes)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return fail(reconciled, AutomaticBackupFailureKind.WRITE_FAILED, now)
        }

        // Record the identity before verification so an interrupted run leaves a settled trail: the
        // document is known, unverified, and beyond pruning until reconciliation proves it usable.
        var updated = reconciled.copy(
            documents = reconciled.documents + RecordedBackupDocument(
                documentId = document.documentId,
                displayName = document.displayName,
                createdAtMillis = now,
                verified = false
            )
        )
        stateStore.save(updated)

        val readBack = try {
            folderStore.read(handle, document.documentId)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            null
        }
        if (readBack == null || !readBack.contentEquals(bytes)) {
            return fail(updated, AutomaticBackupFailureKind.WRITE_UNVERIFIED, now)
        }

        updated = updated.copy(
            lastSuccessMillis = now,
            lastFailure = null,
            documents = updated.documents.map {
                if (it.documentId == document.documentId) it.copy(verified = true) else it
            }
        )
        stateStore.save(updated)

        val pruneResult = prune(handle, updated)
        return AutomaticBackupOutcome.Created(
            displayName = document.displayName,
            prunedCount = pruneResult.prunedCount,
            prunePendingCount = pruneResult.pendingCount
        )
    }

    /**
     * Re-checks the recorded documents whose same-run read-back never completed, promoting the ones
     * that now read back as complete restorable snapshots. A read or validation failure leaves the
     * record unverified: it is retried on a later run and is never pruned meanwhile.
     */
    private suspend fun reconcileUnverified(state: AutomaticBackupState): AutomaticBackupState {
        val handle = state.folderHandle ?: return state
        val unverified = state.documents.filterNot { it.verified }
        if (unverified.isEmpty()) return state
        var updated = state
        for (document in unverified.sortedByDescending { it.createdAtMillis }) {
            val text = try {
                folderStore.read(handle, document.documentId).decodeToString()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                continue
            }
            if (verifySnapshot(text)) {
                updated = updated.copy(
                    documents = updated.documents.map {
                        if (it.documentId == document.documentId) {
                            it.copy(verified = true)
                        } else {
                            it
                        }
                    }
                )
            }
        }
        if (updated != state) stateStore.save(updated)
        return updated
    }

    /** Deletes recorded documents beyond the retention window; deletion is idempotent per document. */
    private suspend fun prune(handle: String, state: AutomaticBackupState): PruneResult {
        var updated = state
        var prunedCount = 0
        var pendingCount = 0
        for (documentId in AutomaticBackupPolicy.documentsToPrune(state)) {
            val deleted = try {
                folderStore.delete(handle, documentId)
                true
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                false
            }
            if (deleted) {
                prunedCount++
                updated = updated.copy(
                    documents = updated.documents.filterNot { it.documentId == documentId }
                )
            } else {
                pendingCount++
            }
        }
        if (prunedCount > 0) stateStore.save(updated)
        return PruneResult(prunedCount, pendingCount)
    }

    private suspend fun probeCapabilities(handle: String): FolderCapabilities? = try {
        folderStore.capabilities(handle)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Throwable) {
        null
    }

    private suspend fun fail(
        state: AutomaticBackupState,
        kind: AutomaticBackupFailureKind,
        nowMillis: Long
    ): AutomaticBackupOutcome.Failed {
        stateStore.save(state.copy(lastFailure = AutomaticBackupFailureRecord(kind, nowMillis)))
        return AutomaticBackupOutcome.Failed(kind)
    }

    private fun displayName(nowMillis: Long): String {
        val suffix = random.nextInt(0x10000).toString(16).padStart(4, '0')
        return "hydrafit-auto-$nowMillis-$suffix.json"
    }

    private data class PruneResult(val prunedCount: Int, val pendingCount: Int)

    private fun FolderUnavailableReason.toFailureKind(): AutomaticBackupFailureKind = when (this) {
        FolderUnavailableReason.PERMISSION_LOST,
        FolderUnavailableReason.FOLDER_MISSING -> AutomaticBackupFailureKind.FOLDER_UNAVAILABLE
        FolderUnavailableReason.UNSUPPORTED_PROVIDER ->
            AutomaticBackupFailureKind.FOLDER_UNSUPPORTED
    }
}
