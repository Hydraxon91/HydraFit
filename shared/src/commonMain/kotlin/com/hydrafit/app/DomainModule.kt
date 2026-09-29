package com.hydrafit.app

import com.hydrafit.app.core.domain.engine.AcceptWeeklyPlanUseCase
import com.hydrafit.app.core.domain.engine.BuildRecentWeightsUseCase
import com.hydrafit.app.core.domain.engine.DeterministicWorkoutPlannerEngine
import com.hydrafit.app.core.domain.engine.EngineAvailability
import com.hydrafit.app.core.domain.engine.GenerateWeeklySplitUseCase
import com.hydrafit.app.core.domain.engine.ObserveAcceptedPlanUseCase
import com.hydrafit.app.core.domain.engine.ObserveWorkoutPlanInputsUseCase
import com.hydrafit.app.core.domain.engine.PeriodizationConfig
import com.hydrafit.app.core.domain.engine.SuggestWeightsUseCase
import com.hydrafit.app.core.domain.engine.SuggestedWeightConfig
import com.hydrafit.app.core.domain.engine.WeeklyPlanSanitizer
import com.hydrafit.app.core.domain.engine.WorkoutPlannerEngineProvider
import com.hydrafit.app.core.domain.fatigue.CalculateMuscleFatigueUseCase
import com.hydrafit.app.core.domain.workout.GetWorkoutLogUseCase
import com.hydrafit.app.core.domain.workout.LogWorkoutSetUseCase
import com.hydrafit.app.core.llm.LocalLlmWorkoutPlannerEngine
import com.hydrafit.app.core.network.ApiKeyProvider
import com.hydrafit.app.core.network.GeminiWorkoutPlannerEngine
import org.koin.core.module.Module
import org.koin.dsl.module

val domainModule: Module = module {
    single { CalculateMuscleFatigueUseCase() }
    single { DeterministicWorkoutPlannerEngine(get()) }
    single { WeeklyPlanSanitizer(get()) }
    single {
        LocalLlmWorkoutPlannerEngine(
            generator = get(),
            fallback = get<DeterministicWorkoutPlannerEngine>(),
            catalog = get(),
            sanitizer = get(),
            logger = get()
        )
    }
    single<EngineAvailability> { DefaultEngineAvailability(get<ApiKeyProvider>(), get()) }
    single<WorkoutPlannerEngineProvider> {
        DefaultWorkoutPlannerEngineProvider(
            preference = get(),
            deterministic = get<DeterministicWorkoutPlannerEngine>(),
            gemini = get<GeminiWorkoutPlannerEngine>(),
            localLlm = get<LocalLlmWorkoutPlannerEngine>(),
            apiKeyProvider = get<ApiKeyProvider>()
        )
    }
    single { GenerateWeeklySplitUseCase(get()) }
    single { SuggestedWeightConfig() }
    single { PeriodizationConfig() }
    single { SuggestWeightsUseCase(get()) }
    single { BuildRecentWeightsUseCase() }
    single { ObserveWorkoutPlanInputsUseCase(get(), get(), get(), get(), get(), get(), get()) }
    single { AcceptWeeklyPlanUseCase(get(), get(), get()) }
    single { ObserveAcceptedPlanUseCase(get()) }
    single { LogWorkoutSetUseCase(get()) }
    single { GetWorkoutLogUseCase(get()) }
}
