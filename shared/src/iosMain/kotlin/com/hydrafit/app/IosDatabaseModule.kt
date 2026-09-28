package com.hydrafit.app

import com.hydrafit.app.core.database.DatabaseDriverFactory
import com.hydrafit.app.core.database.NativeDatabaseDriverFactory
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.llm.NoopOnDevicePlannerLogger
import com.hydrafit.app.core.llm.OnDevicePlannerLogger
import com.hydrafit.app.core.llm.OnDeviceTextGenerator
import com.hydrafit.app.core.llm.UnsupportedOnDeviceTextGenerator
import com.hydrafit.app.core.network.ApiKeyProvider
import com.hydrafit.app.core.userdata.settings.ApiKeyStore
import com.hydrafit.app.core.userdata.settings.NoopApiKeyStore
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSDate
import platform.Foundation.NSProcessInfo
import platform.Foundation.timeIntervalSince1970

fun iosDatabaseModule(): Module = module {
    single<DatabaseDriverFactory> { NativeDatabaseDriverFactory() }
    single<TimeProvider> { TimeProvider { (NSDate().timeIntervalSince1970 * 1000.0).toLong() } }
    single<ApiKeyStore> { NoopApiKeyStore() }
    single<ApiKeyProvider> {
        val store = get<ApiKeyStore>()
        val environmentKey =
            NSProcessInfo.processInfo.environment["GEMINI_API_KEY"] as? String ?: ""
        ApiKeyProvider { store.load()?.takeIf { it.isNotBlank() } ?: environmentKey }
    }
    single<OnDeviceTextGenerator> { UnsupportedOnDeviceTextGenerator() }
    single<OnDevicePlannerLogger> { NoopOnDevicePlannerLogger }
}
