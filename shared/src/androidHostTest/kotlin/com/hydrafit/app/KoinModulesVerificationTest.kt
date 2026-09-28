package com.hydrafit.app

import com.hydrafit.app.core.database.DatabaseDriverFactory
import com.hydrafit.app.core.database.databaseModule
import com.hydrafit.app.core.domain.time.TimeProvider
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
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.verify

@OptIn(KoinExperimentalAPI::class)
class KoinModulesVerificationTest {

    private val testPlatformModule = module {
        single<TimeProvider> { TimeProvider { 0L } }
        single<ApiKeyStore> { FakeApiKeyStore }
        single<ApiKeyProvider> { ApiKeyProvider { "test-key" } }
        single<OnDeviceTextGenerator> { FakeOnDeviceTextGenerator }
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

    private object FakeApiKeyStore : ApiKeyStore {
        override fun load(): String? = "test-key"

        override fun save(apiKey: String) = Unit

        override fun clear() = Unit
    }

    private object FakeOnDeviceTextGenerator : OnDeviceTextGenerator {
        override fun isAvailable(): Boolean = false

        override fun generate(prompt: String): String = error("Not used by graph verification")
    }
}
