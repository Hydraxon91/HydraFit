package com.hydrafit.app.feature.settings

import com.hydrafit.app.core.domain.backup.AutomaticBackupScheduler
import com.hydrafit.app.core.domain.backup.AutomaticBackupStateStore
import com.hydrafit.app.core.domain.startup.StartupReadiness
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * App-lifetime reconciliation between the stored automatic-backup state and the platform
 * scheduler, independent of whether the Settings screen is open: an app update, a restored
 * process or a job the system dropped all converge to "scheduled exactly while enabled". The
 * enable/disable flow keeps both in line at the moment of a change; this startup pass converges
 * whatever state the process finds.
 */
class AutomaticBackupCoordinator(
    private val stateStore: AutomaticBackupStateStore,
    private val scheduler: AutomaticBackupScheduler,
    private val readiness: StartupReadiness,
    dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    fun start() {
        scope.launch {
            readiness.isReady.first { it }
            val state = stateStore.load()
            if (state.enabled) scheduler.schedule() else scheduler.cancel()
        }
    }

    fun close() = scope.cancel()
}
