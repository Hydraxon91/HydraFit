package com.hydrafit.app

import com.hydrafit.app.core.database.DatabaseDriverFactory
import com.hydrafit.app.core.database.databaseModule
import com.hydrafit.app.core.domain.engine.AcceptedPlan
import com.hydrafit.app.core.domain.engine.ObserveWorkoutPlanInputsUseCase
import com.hydrafit.app.core.domain.engine.PlanHistoryRepository
import com.hydrafit.app.core.domain.engine.WorkoutPlanSources
import com.hydrafit.app.core.domain.engine.WorkoutPlanSourcesRepository
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.llm.NoopOnDevicePlannerLogger
import com.hydrafit.app.core.llm.OnDevicePlannerLogger
import com.hydrafit.app.core.llm.OnDeviceTextGenerator
import com.hydrafit.app.core.network.ApiKeyProvider
import com.hydrafit.app.core.network.GeminiWorkoutPlannerEngine
import com.hydrafit.app.core.userdata.settings.ApiKeyStore
import com.hydrafit.app.feature.equipment.equipmentModule
import com.hydrafit.app.feature.fatigueheatmap.fatigueHeatmapModule
import com.hydrafit.app.feature.logger.loggerModule
import com.hydrafit.app.feature.settings.settingsModule
import com.hydrafit.app.feature.splitbuilder.splitBuilderModule
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.verify.verify

@OptIn(KoinExperimentalAPI::class)
class KoinModulesVerificationTest {

    private val testPlatformModule = module {
        single<TimeProvider> { TimeProvider { 0L } }
        single<ApiKeyStore> { FakeApiKeyStore }
        single<ApiKeyProvider> { ApiKeyProvider { "test-key" } }
        single<OnDeviceTextGenerator> { FakeOnDeviceTextGenerator }
        single<OnDevicePlannerLogger> { NoopOnDevicePlannerLogger }
    }

    private val allModules = module {
        includes(
            domainModule,
            databaseModule,
            equipmentModule,
            fatigueHeatmapModule,
            splitBuilderModule,
            loggerModule,
            settingsModule,
            testPlatformModule
        )
    }

    @Test
    fun allModulesResolve() {
        allModules.verify(
            extraTypes = listOf(
                DatabaseDriverFactory::class,
                GeminiWorkoutPlannerEngine::class
            )
        )
    }

    /**
     * `verify()` does not reflect the constructor of lambda/`singleOf` definitions, so a missing
     * collaborator (e.g. `ProgressWeightsUseCase`) can slip through and only crash on device. Boot a
     * real container over the domain graph (with fakes for the database-backed repositories) and
     * resolve the plan-inputs use case, which is where the app previously crashed.
     */
    @Test
    fun thePlanInputsGraphResolvesAtRuntime() {
        val koin = koinApplication {
            modules(
                module {
                    single<WorkoutPlanSourcesRepository> { FakeWorkoutPlanSourcesRepository }
                    single<PlanHistoryRepository> { FakePlanHistoryRepository }
                },
                domainModule,
                testPlatformModule
            )
        }.koin

        try {
            assertNotNull(koin.get<ObserveWorkoutPlanInputsUseCase>())
        } finally {
            koin.close()
        }
    }

    private object FakeApiKeyStore : ApiKeyStore {
        override fun load(): String? = "test-key"

        override fun save(apiKey: String) = Unit

        override fun clear() = Unit
    }

    private object FakeOnDeviceTextGenerator : OnDeviceTextGenerator {
        override fun isAvailable(): Boolean = false

        override fun generate(prompt: String, jsonSchema: String?): String =
            error("Not used by graph verification")
    }

    private object FakeWorkoutPlanSourcesRepository : WorkoutPlanSourcesRepository {
        override fun observe(): Flow<WorkoutPlanSources> = emptyFlow()
    }

    private object FakePlanHistoryRepository : PlanHistoryRepository {
        override fun observeLatest(): Flow<AcceptedPlan?> = flowOf(null)

        override fun observeHistory(): Flow<List<AcceptedPlan>> = flowOf(emptyList())

        override suspend fun latest(): AcceptedPlan? = null

        override suspend fun accept(plan: AcceptedPlan) = Unit

        override suspend fun clear() = Unit
    }
}
