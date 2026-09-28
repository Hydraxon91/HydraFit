package com.hydrafit.app

import android.content.Context
import com.hydrafit.app.core.database.AndroidDatabaseDriverFactory
import com.hydrafit.app.core.database.DatabaseDriverFactory
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.llm.AndroidOnDeviceModelManager
import com.hydrafit.app.core.llm.LiteRtLmTextGenerator
import com.hydrafit.app.core.llm.OnDeviceModelManager
import com.hydrafit.app.core.llm.OnDeviceTextGenerator
import com.hydrafit.app.core.network.ApiKeyProvider
import com.hydrafit.app.core.userdata.settings.AndroidKeystoreApiKeyStore
import com.hydrafit.app.core.userdata.settings.ApiKeyStore
import org.koin.core.module.Module
import org.koin.dsl.module

fun androidDatabaseModule(context: Context, geminiApiKey: String): Module = module {
    single<DatabaseDriverFactory> { AndroidDatabaseDriverFactory(context.applicationContext) }
    single<TimeProvider> { TimeProvider { System.currentTimeMillis() } }
    single<ApiKeyStore> { AndroidKeystoreApiKeyStore(context.applicationContext) }
    single<ApiKeyProvider> {
        val store = get<ApiKeyStore>()
        ApiKeyProvider { store.load()?.takeIf { it.isNotBlank() } ?: geminiApiKey }
    }
    single { AndroidOnDeviceModelManager(context.applicationContext) }
    single<OnDeviceModelManager> { get<AndroidOnDeviceModelManager>() }
    single<OnDeviceTextGenerator> { LiteRtLmTextGenerator(context.applicationContext, get()) }
}

fun initKoin(context: Context, geminiApiKey: String) {
    initKoin(androidDatabaseModule(context, geminiApiKey))
}
