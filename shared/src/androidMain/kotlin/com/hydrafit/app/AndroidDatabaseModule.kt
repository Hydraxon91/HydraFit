package com.hydrafit.app

import android.content.Context
import android.provider.Settings
import com.hydrafit.app.core.database.AndroidDatabaseDriverFactory
import com.hydrafit.app.core.database.DatabaseDriverFactory
import com.hydrafit.app.core.domain.backup.AutomaticBackupScheduler
import com.hydrafit.app.core.domain.backup.AutomaticBackupStateStore
import com.hydrafit.app.core.domain.backup.BackupCrypto
import com.hydrafit.app.core.domain.backup.BackupEncryptionKeyStore
import com.hydrafit.app.core.domain.backup.BackupFolderStore
import com.hydrafit.app.core.domain.schedule.WorkoutReminderScheduler
import com.hydrafit.app.core.domain.storage.StorageUsagePort
import com.hydrafit.app.core.domain.time.BootIdentityProvider
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.workout.RestAlertScheduler
import com.hydrafit.app.core.network.ApiKeyProvider
import com.hydrafit.app.core.network.GroqApiKeyProvider
import com.hydrafit.app.core.userdata.backup.AndroidAutomaticBackupStateStore
import com.hydrafit.app.core.userdata.backup.AndroidBackupCrypto
import com.hydrafit.app.core.userdata.backup.AndroidBackupEncryptionKeyStore
import com.hydrafit.app.core.userdata.backup.AndroidBackupFileStore
import com.hydrafit.app.core.userdata.backup.AndroidBackupFolderStore
import com.hydrafit.app.core.userdata.backup.BackupFileStore
import com.hydrafit.app.core.userdata.settings.AndroidAppVersionProvider
import com.hydrafit.app.core.userdata.settings.AndroidGroqApiKeyStore
import com.hydrafit.app.core.userdata.settings.ApiKeyStore
import com.hydrafit.app.core.userdata.settings.AppVersionProvider
import com.hydrafit.app.core.userdata.storage.AndroidStorageUsagePort
import com.hydrafit.app.core.userdata.transfer.AndroidPrescriptionDocumentStore
import com.hydrafit.app.core.userdata.transfer.AndroidPrescriptionPdfRenderer
import com.hydrafit.app.core.userdata.transfer.PrescriptionDocumentStore
import com.hydrafit.app.core.userdata.transfer.PrescriptionPdfRenderer
import com.hydrafit.app.feature.logger.AndroidRestAlertScheduler
import com.hydrafit.app.feature.routines.AndroidWorkoutReminderScheduler
import com.hydrafit.app.feature.routines.localMinuteEpochMillis
import com.hydrafit.app.feature.settings.AndroidAutomaticBackupScheduler
import com.hydrafit.app.feature.settings.AutomaticBackupCoordinator
import org.koin.core.module.Module
import org.koin.core.module.dsl.onClose
import org.koin.dsl.module

fun androidDatabaseModule(context: Context, geminiApiKey: String): Module = module {
    single<DatabaseDriverFactory> { AndroidDatabaseDriverFactory(context.applicationContext) }
    single<TimeProvider> {
        object : TimeProvider {
            override fun nowMillis(): Long = System.currentTimeMillis()
            override fun utcOffsetMillis(): Long =
                java.util.TimeZone.getDefault().getOffset(nowMillis()).toLong()

            override fun epochMillisForLocalMinute(localEpochDay: Long, minuteOfDay: Int): Long =
                localMinuteEpochMillis(localEpochDay, minuteOfDay)
        }
    }
    single<BootIdentityProvider> {
        BootIdentityProvider {
            Settings.Global.getInt(
                context.applicationContext.contentResolver,
                Settings.Global.BOOT_COUNT,
                -1
            ).takeIf { it >= 0 }?.toString()
        }
    }
    single { AndroidRestAlertScheduler(context.applicationContext) }
    single<RestAlertScheduler> { get<AndroidRestAlertScheduler>() }
    single { AndroidWorkoutReminderScheduler(context.applicationContext) }
    single<WorkoutReminderScheduler> { get<AndroidWorkoutReminderScheduler>() }
    single<ApiKeyStore> { AndroidGroqApiKeyStore(context.applicationContext) }
    single<ApiKeyProvider> {
        ApiKeyProvider { "" }
    }
    single<GroqApiKeyProvider> {
        val store = get<ApiKeyStore>()
        GroqApiKeyProvider { store.load().orEmpty() }
    }
    single<BackupFileStore> { AndroidBackupFileStore(context.applicationContext) }
    single<BackupFolderStore> { AndroidBackupFolderStore(context.applicationContext) }
    single<StorageUsagePort> {
        AndroidStorageUsagePort(context.applicationContext, get(), get())
    }
    single<BackupCrypto> { AndroidBackupCrypto() }
    single<BackupEncryptionKeyStore> { AndroidBackupEncryptionKeyStore(context.applicationContext) }
    single<AutomaticBackupStateStore> {
        AndroidAutomaticBackupStateStore(context.applicationContext)
    }
    single { AndroidAutomaticBackupScheduler(context.applicationContext) }
    single<AutomaticBackupScheduler> { get<AndroidAutomaticBackupScheduler>() }
    single(createdAtStart = true) {
        AutomaticBackupCoordinator(
            stateStore = get(),
            scheduler = get(),
            readiness = get()
        ).also { it.start() }
    }.factory.beanDefinition.onClose { it?.close() }
    single<PrescriptionDocumentStore> {
        AndroidPrescriptionDocumentStore(context.applicationContext)
    }
    single<PrescriptionPdfRenderer> { AndroidPrescriptionPdfRenderer() }
}

/** Carries the Android `BuildConfig.VERSION_NAME`, which is not reachable from `:shared`/`:core`. */
fun appVersionModule(versionName: String): Module = module {
    single<AppVersionProvider> { AndroidAppVersionProvider(versionName) }
}

fun initKoin(context: Context, geminiApiKey: String, versionName: String) {
    initKoin(androidDatabaseModule(context, geminiApiKey), listOf(appVersionModule(versionName)))
}
