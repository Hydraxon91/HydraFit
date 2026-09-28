package com.hydrafit.app

import android.content.Context
import com.hydrafit.app.core.database.AndroidDatabaseDriverFactory
import com.hydrafit.app.core.database.DatabaseDriverFactory
import com.hydrafit.app.core.domain.time.TimeProvider
import org.koin.core.module.Module
import org.koin.dsl.module

fun androidDatabaseModule(context: Context): Module = module {
    single<DatabaseDriverFactory> { AndroidDatabaseDriverFactory(context.applicationContext) }
    single<TimeProvider> { TimeProvider { System.currentTimeMillis() } }
}

fun initKoin(context: Context) {
    initKoin(androidDatabaseModule(context))
}
