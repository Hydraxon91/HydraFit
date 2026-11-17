package com.hydrafit.app.core.network

import org.koin.core.module.Module
import org.koin.dsl.module

val networkModule: Module = module {
    single { createPlannerHttpClient() }
    single { PlannerPromptBuilder() }
    single { GroqWorkoutPlannerEngine(get(), get(), get(), get(), get()) }
}
