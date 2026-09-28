package com.hydrafit.app.core.network

import com.hydrafit.app.core.domain.engine.DeterministicWorkoutPlannerEngine
import org.koin.core.module.Module
import org.koin.dsl.module

val networkModule: Module = module {
    single { createGeminiHttpClient() }
    single {
        GeminiWorkoutPlannerEngine(
            httpClient = get(),
            config = GeminiConfig(),
            catalog = get(),
            apiKeyProvider = get(),
            sanitizer = get(),
            fallback = get<DeterministicWorkoutPlannerEngine>()
        )
    }
}
