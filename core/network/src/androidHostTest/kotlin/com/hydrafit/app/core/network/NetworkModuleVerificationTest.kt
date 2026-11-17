package com.hydrafit.app.core.network

import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.WeeklyPlanSanitizer
import io.ktor.client.engine.HttpClientEngine
import kotlin.test.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.test.verify.verify

@OptIn(KoinExperimentalAPI::class)
class NetworkModuleVerificationTest {

    @Test
    fun networkModuleDependenciesAreResolvable() {
        networkModule.verify(
            extraTypes = listOf(
                GroqApiKeyProvider::class,
                ExerciseCatalog::class,
                HttpClientEngine::class,
                WeeklyPlanSanitizer::class
            )
        )
    }
}
