package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.backup.ApplyStagedBackupUseCase
import com.hydrafit.app.core.domain.startup.StartupReadiness
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Runs the idempotent startup maintenance -- catalog seeding, custom-exercise dedupe and the legacy
 * session backfill -- off the main thread, in the same order the former synchronous `initKoin` used,
 * then reports when it is safe for the UI to read the database. A staged backup is applied first, in
 * the same window, before any writer exists.
 *
 * A storage failure is reported through [hasFailed] instead of escaping the coroutine: the process
 * keeps running, the shell keeps its first screen closed and offers [retry]. Nothing is cleared,
 * replaced or repaired here, so a transient failure can be retried without touching user data. A
 * cancellation is not a failure and still propagates, so a shutdown or test scope ending never shows
 * a retryable error.
 */
class DatabaseStartupMaintenance(
    private val applyStagedBackup: ApplyStagedBackupUseCase,
    private val seedExerciseCatalog: SeedExerciseCatalog,
    private val seedEquipmentCatalog: SeedEquipmentCatalog,
    private val customExerciseDedupe: CustomExerciseDedupe,
    private val workoutSessionBackfill: WorkoutSessionBackfill,
    private val searchProjectionBackfill: SearchProjectionBackfill,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : StartupReadiness {
    private val _isReady = MutableStateFlow(false)
    override val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _hasFailed = MutableStateFlow(false)
    override val hasFailed: StateFlow<Boolean> = _hasFailed.asStateFlow()

    private val started = MutableStateFlow(false)

    fun start() {
        // One launch per process: a second call cannot race a second attempt against the first.
        if (!started.compareAndSet(expect = false, update = true)) return
        launchAttempt()
    }

    override fun retry() {
        // Only a failed attempt may start another one, and clearing the flag before launching keeps a
        // second tap from queueing a duplicate attempt behind this one.
        if (!_hasFailed.compareAndSet(expect = true, update = false)) return
        launchAttempt()
    }

    private fun launchAttempt() {
        scope.launch {
            try {
                applyStagedBackup()
                seedExerciseCatalog.seed()
                seedEquipmentCatalog.seed()
                customExerciseDedupe.run()
                workoutSessionBackfill.backfill()
                searchProjectionBackfill.run()
                _isReady.value = true
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _hasFailed.value = true
            }
        }
    }
}
