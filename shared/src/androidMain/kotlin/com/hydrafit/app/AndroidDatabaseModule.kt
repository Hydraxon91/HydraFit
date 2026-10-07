package com.hydrafit.app

import android.content.Context
import android.net.Uri
import com.hydrafit.app.core.database.AndroidDatabaseDriverFactory
import com.hydrafit.app.core.database.DatabaseDriverFactory
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.llm.AndroidOnDeviceModelManager
import com.hydrafit.app.core.llm.AndroidOnDevicePlannerLogger
import com.hydrafit.app.core.llm.LiteRtLmTextGenerator
import com.hydrafit.app.core.llm.OnDevicePlannerLogger
import com.hydrafit.app.core.llm.OnDeviceTextGenerator
import com.hydrafit.app.core.network.ApiKeyProvider
import com.hydrafit.app.core.userdata.llm.OnDeviceModelManager
import com.hydrafit.app.core.userdata.settings.AndroidAppVersionProvider
import com.hydrafit.app.core.userdata.settings.AndroidKeystoreApiKeyStore
import com.hydrafit.app.core.userdata.settings.ApiKeyStore
import com.hydrafit.app.core.userdata.settings.AppVersionProvider
import org.koin.core.module.Module
import org.koin.dsl.module

fun androidDatabaseModule(context: Context, geminiApiKey: String): Module = module {
    single<DatabaseDriverFactory> { AndroidDatabaseDriverFactory(context.applicationContext) }
    single<TimeProvider> {
        object : TimeProvider {
            override fun nowMillis(): Long = System.currentTimeMillis()
            override fun utcOffsetMillis(): Long =
                java.util.TimeZone.getDefault().getOffset(nowMillis()).toLong()
        }
    }
    single<ApiKeyStore> { AndroidKeystoreApiKeyStore(context.applicationContext) }
    single<ApiKeyProvider> {
        val store = get<ApiKeyStore>()
        ApiKeyProvider { store.load()?.takeIf { it.isNotBlank() } ?: geminiApiKey }
    }
    single { AndroidOnDeviceModelManager(context.applicationContext) }
    single<OnDeviceTextGenerator> {
        LiteRtLmTextGenerator(context.applicationContext, get<AndroidOnDeviceModelManager>())
    }
    single<OnDeviceModelManager> {
        val manager = get<AndroidOnDeviceModelManager>()
        // Resolve the generator (cheap: it loads no model until generate) so removing the model also
        // stops its cached native engine instead of leaving it holding backend resources.
        val generator = get<OnDeviceTextGenerator>()
        DelegatingOnDeviceModelManager(
            installedCheck = manager::isInstalled,
            targetCheck = manager::modelTarget,
            onInstall = { source -> manager.importFromUri(Uri.parse(source)) },
            onRemove = {
                manager.remove()
                generator.release()
            }
        )
    }
    single<OnDevicePlannerLogger> { AndroidOnDevicePlannerLogger() }
}

/** Carries the Android `BuildConfig.VERSION_NAME`, which is not reachable from `:shared`/`:core`. */
fun appVersionModule(versionName: String): Module = module {
    single<AppVersionProvider> { AndroidAppVersionProvider(versionName) }
}

fun initKoin(context: Context, geminiApiKey: String, versionName: String) {
    initKoin(androidDatabaseModule(context, geminiApiKey), listOf(appVersionModule(versionName)))
}
