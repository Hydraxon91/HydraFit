package com.hydrafit.app

import android.content.Context
import com.hydrafit.app.core.database.AndroidDatabaseDriverFactory
import com.hydrafit.app.core.database.DatabaseDriverFactory
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.network.ApiKeyProvider
import org.koin.core.module.Module
import org.koin.dsl.module

fun androidDatabaseModule(context: Context, geminiApiKey: String): Module = module {
    single<DatabaseDriverFactory> { AndroidDatabaseDriverFactory(context.applicationContext) }
    single<TimeProvider> { TimeProvider { System.currentTimeMillis() } }
    single<ApiKeyProvider> { ApiKeyProvider { geminiApiKey } }
}

fun initKoin(context: Context, geminiApiKey: String) {
    initKoin(androidDatabaseModule(context, geminiApiKey))
}
