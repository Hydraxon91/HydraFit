package com.hydrafit.app

import com.hydrafit.app.core.database.DatabaseDriverFactory
import com.hydrafit.app.core.database.NativeDatabaseDriverFactory
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.network.ApiKeyProvider
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSDate
import platform.Foundation.NSProcessInfo
import platform.Foundation.timeIntervalSince1970

fun iosDatabaseModule(): Module = module {
    single<DatabaseDriverFactory> { NativeDatabaseDriverFactory() }
    single<TimeProvider> { TimeProvider { (NSDate().timeIntervalSince1970 * 1000.0).toLong() } }
    single<ApiKeyProvider> {
        ApiKeyProvider {
            NSProcessInfo.processInfo.environment["GEMINI_API_KEY"] as? String ?: ""
        }
    }
}
