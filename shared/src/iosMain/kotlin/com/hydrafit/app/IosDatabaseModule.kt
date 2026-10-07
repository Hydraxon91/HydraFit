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
import com.hydrafit.app.core.userdata.settings.AppVersionProvider
import com.hydrafit.app.core.userdata.settings.IosAppVersionProvider
import com.hydrafit.app.core.userdata.settings.NoopApiKeyStore
import org.koin.core.module.Module
import org.koin.dsl.module
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSProcessInfo
import platform.Foundation.timeIntervalSince1970

fun iosDatabaseModule(): Module = module {
    single<DatabaseDriverFactory> { NativeDatabaseDriverFactory() }
    single<TimeProvider> {
        object : TimeProvider {
            override fun nowMillis(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()

            override fun utcOffsetMillis(): Long = offsetFromZoneName()
        }
    }
    single<ApiKeyStore> { NoopApiKeyStore() }
    single<AppVersionProvider> { IosAppVersionProvider() }
    single<ApiKeyProvider> {
        val store = get<ApiKeyStore>()
        val environmentKey =
            NSProcessInfo.processInfo.environment["GEMINI_API_KEY"] as? String ?: ""
        ApiKeyProvider { store.load()?.takeIf { it.isNotBlank() } ?: environmentKey }
    }
    single<OnDeviceTextGenerator> { UnsupportedOnDeviceTextGenerator() }
    single<OnDevicePlannerLogger> { NoopOnDevicePlannerLogger }
}

/**
 * The current UTC offset (ms) parsed from the formatter's "Z" pattern (e.g. "+0200"). Uses an
 * instance API because the `NSTimeZone` class properties are not exposed to Kotlin/Native.
 */
private fun offsetFromZoneName(): Long {
    val formatter = NSDateFormatter()
    formatter.dateFormat = "Z"
    val zone = formatter.stringFromDate(NSDate())
    if (zone.length != 5) return 0L
    val sign = if (zone.startsWith("-")) -1L else 1L
    val hours = zone.substring(1, 3).toLong()
    val minutes = zone.substring(3, 5).toLong()
    return sign * (hours * 60L + minutes) * 60_000L
}
