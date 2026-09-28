package com.hydrafit.app.core.network

import com.hydrafit.app.core.domain.engine.DeterministicWorkoutPlannerEngine
import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.WeeklyPlanSanitizer
import com.hydrafit.app.core.domain.engine.WorkoutPlannerEngine
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
                ApiKeyProvider::class,
                ExerciseCatalog::class,
                GeminiConfig::class,
                HttpClientEngine::class,
                WeeklyPlanSanitizer::class,
                DeterministicWorkoutPlannerEngine::class,
                WorkoutPlannerEngine::class
            )
        )
    }
}
