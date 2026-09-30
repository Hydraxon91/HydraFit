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
import com.hydrafit.app.core.userdata.settings.AndroidKeystoreApiKeyStore
import com.hydrafit.app.core.userdata.settings.ApiKeyStore
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
    single<OnDeviceModelManager> {
        val manager = get<AndroidOnDeviceModelManager>()
        DelegatingOnDeviceModelManager(
            installedCheck = manager::isInstalled,
            targetCheck = manager::modelTarget,
            onInstall = { source -> manager.importFromUri(Uri.parse(source)) },
            onRemove = manager::remove
        )
    }
    single<OnDeviceTextGenerator> {
        LiteRtLmTextGenerator(context.applicationContext, get<AndroidOnDeviceModelManager>())
    }
    single<OnDevicePlannerLogger> { AndroidOnDevicePlannerLogger() }
}

fun initKoin(context: Context, geminiApiKey: String) {
    initKoin(androidDatabaseModule(context, geminiApiKey))
}
