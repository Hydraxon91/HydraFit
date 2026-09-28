package com.hydrafit.app

import com.hydrafit.app.core.database.DatabaseDriverFactory
import com.hydrafit.app.core.database.NativeDatabaseDriverFactory
import com.hydrafit.app.core.domain.time.TimeProvider
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSDate

fun iosDatabaseModule(): Module = module {
    single<DatabaseDriverFactory> { NativeDatabaseDriverFactory() }
    single<TimeProvider> { TimeProvider { (NSDate().timeIntervalSince1970 * 1000.0).toLong() } }
}
