package com.hydrafit.app.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.hydrafit.app.core.domain.engine.PlanHistoryRepository
import com.hydrafit.app.core.domain.engine.WorkoutPlanSourcesRepository
import com.hydrafit.app.core.domain.routine.RoutineTemplateRepository
import com.hydrafit.app.core.domain.schedule.WorkoutScheduleRepository
import com.hydrafit.app.core.domain.startup.StartupReadiness
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.userdata.equipment.ExerciseExclusionRepository
import com.hydrafit.app.core.userdata.equipment.ExercisePreferenceRepository
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
            assertNotNull(koin.get<RoutineTemplateRepository>())
            assertNotNull(koin.get<WorkoutScheduleRepository>())
        } finally {
            koin.close()
            driver.close()
        }
    }

    /**
     * The corrected/changed bindings are lambda `single { }` definitions that `verify()` cannot
     * reflect; resolve the real repositories (including the eight-dependency sources repository)
     * from a container to prove their `get()` chains.
     */
    @Test
    fun newRepositoriesAndSourcesResolveAtRuntime() {
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
            assertNotNull(koin.get<ExercisePreferenceRepository>())
            assertNotNull(koin.get<ExerciseExclusionRepository>())
            assertNotNull(koin.get<WorkoutPlanSourcesRepository>())
            assertNotNull(koin.get<PlanHistoryRepository>())
        } finally {
            koin.close()
            driver.close()
        }
    }
}
