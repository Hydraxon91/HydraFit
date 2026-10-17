package com.hydrafit.app.feature.settings

import android.app.job.JobParameters
import android.app.job.JobService
import com.hydrafit.app.core.domain.backup.AutomaticBackupOutcome
import com.hydrafit.app.core.domain.backup.RunAutomaticBackupUseCase
import com.hydrafit.app.core.domain.startup.StartupReadiness
import com.hydrafit.app.core.userdata.settings.AppVersionProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get

/**
 * Runs one automatic-backup attempt on the scheduler's thread. The attempt waits for startup
 * maintenance, runs the shared single-flight use case off the main thread, and reports a retry only
 * for the failure kinds the policy calls retryable; every other outcome surfaces in Settings. A
 * system stop cancels the attempt (the runner rethrows cancellation and records no failure), and
 * the periodic job waits for its next opportunity.
 */
class AutomaticBackupJobService :
    JobService(),
    KoinComponent {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onStartJob(params: JobParameters): Boolean {
        scope.launch {
            val retry = try {
                get<StartupReadiness>().isReady.first { it }
                val outcome = get<RunAutomaticBackupUseCase>()(
                    appVersion = get<AppVersionProvider>().versionName
                )
                (outcome as? AutomaticBackupOutcome.Failed)?.kind?.retryable ?: false
            } catch (_: CancellationException) {
                false
            } catch (_: Throwable) {
                true
            }
            jobFinished(params, retry)
        }
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        scope.coroutineContext.cancelChildren()
        return true
    }
}
