package com.hydrafit.app.core.network

import org.koin.core.module.Module
import org.koin.dsl.module

val networkModule: Module = module {
    single { createGeminiHttpClient() }
    single {
        GeminiWorkoutPlannerEngine(get(), GeminiConfig(get<ApiKeyProvider>().geminiApiKey()), get())
    }
}
