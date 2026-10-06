package com.hydrafit.app.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.startup.StartupReadiness
import com.hydrafit.app.core.domain.time.TimeProvider
import kotlin.test.Test
import kotlin.test.assertNotNull
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.verify.verify

@OptIn(KoinExperimentalAPI::class)
class DatabaseModuleVerificationTest {

    @Test
    fun databaseModuleDependenciesAreResolvable() {
        databaseModule.verify(
            extraTypes = listOf(DatabaseDriverFactory::class, TimeProvider::class)
        )
    }

    /**
     * `verify()` does not reflect lambda `single { }` definitions, so the startup-maintenance
     * bindings are resolved from a real container instead.
     */
    @Test
    fun startupMaintenanceResolvesAtRuntime() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        HydraFitDatabase.Schema.create(driver)
        val koin = koinApplication {
            modules(
                module {
                    single<DatabaseDriverFactory> {
                        object : DatabaseDriverFactory {
                            override fun createDriver() = driver
                        }
                    }
                    single<TimeProvider> { TimeProvider { 0L } }
                },
                databaseModule
            )
        }.koin
        try {
            assertNotNull(koin.get<DatabaseStartupMaintenance>())
            assertNotNull(koin.get<StartupReadiness>())
        } finally {
            koin.close()
            driver.close()
        }
    }
}
