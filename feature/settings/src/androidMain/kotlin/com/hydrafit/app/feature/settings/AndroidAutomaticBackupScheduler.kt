package com.hydrafit.app.feature.settings

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import com.hydrafit.app.core.domain.backup.AutomaticBackupScheduler

/**
 * The persisted daily job behind automatic backups. `JobScheduler` owns delivery: the period is a
 * target, not an exact time, and a persisted job is re-armed by the system after a reboot, so no
 * boot receiver is involved. The runner re-checks due-ness, so an early or late run is harmless.
 */
class AndroidAutomaticBackupScheduler(context: Context) : AutomaticBackupScheduler {
    private val appContext = context.applicationContext
    private val scheduler = appContext.getSystemService(JobScheduler::class.java)

    override fun schedule() {
        val job = JobInfo.Builder(
            JOB_ID,
            ComponentName(appContext, AutomaticBackupJobService::class.java)
        )
            .setPersisted(true)
            .setPeriodic(PERIOD_MILLIS)
            .build()
        scheduler.schedule(job)
    }

    override fun cancel() {
        scheduler.cancel(JOB_ID)
    }

    private companion object {
        const val JOB_ID = 4001
        const val PERIOD_MILLIS = 24L * 60 * 60 * 1000
    }
}
