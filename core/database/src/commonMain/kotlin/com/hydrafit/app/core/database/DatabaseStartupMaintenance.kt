package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.backup.ApplyStagedBackupUseCase
import com.hydrafit.app.core.domain.startup.StartupReadiness
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
 * Failures are left uncaught: an exception surfaces as an unhandled coroutine exception and crashes
 * the process, matching the previous synchronous behaviour rather than masking a half-maintained
 * database or leaving the gate stuck.
 */
class DatabaseStartupMaintenance(
    private val applyStagedBackup: ApplyStagedBackupUseCase,
    private val seedExerciseCatalog: SeedExerciseCatalog,
    private val seedEquipmentCatalog: SeedEquipmentCatalog,
    private val customExerciseDedupe: CustomExerciseDedupe,
    private val workoutSessionBackfill: WorkoutSessionBackfill,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : StartupReadiness {
    private val _isReady = MutableStateFlow(false)
    override val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    fun start() {
        scope.launch {
            applyStagedBackup()
            seedExerciseCatalog.seed()
            seedEquipmentCatalog.seed()
            customExerciseDedupe.run()
            workoutSessionBackfill.backfill()
            _isReady.value = true
        }
    }
}
