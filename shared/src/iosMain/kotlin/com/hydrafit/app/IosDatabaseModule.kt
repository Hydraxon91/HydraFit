package com.hydrafit.app

import com.hydrafit.app.core.database.DatabaseDriverFactory
import com.hydrafit.app.core.database.NativeDatabaseDriverFactory
import org.koin.core.module.Module
import org.koin.dsl.module

fun iosDatabaseModule(): Module = module {
    single<DatabaseDriverFactory> { NativeDatabaseDriverFactory() }
}
