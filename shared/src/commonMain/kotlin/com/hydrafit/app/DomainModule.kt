package com.hydrafit.app

import com.hydrafit.app.core.domain.engine.AcceptWeeklyPlanUseCase
import com.hydrafit.app.core.domain.engine.BuildRecentWeightsUseCase
import com.hydrafit.app.core.domain.engine.DefaultOnDevicePlanProgressReporter
import com.hydrafit.app.core.domain.engine.DeterministicWorkoutPlannerEngine
import com.hydrafit.app.core.domain.engine.EngineAvailability
import com.hydrafit.app.core.domain.engine.GenerateWeeklySplitUseCase
import com.hydrafit.app.core.domain.engine.ObserveAcceptedPlanUseCase
import com.hydrafit.app.core.domain.engine.ObserveWorkoutPlanInputsUseCase
import com.hydrafit.app.core.domain.engine.OnDevicePlanProgressReporter
import com.hydrafit.app.core.domain.engine.PeriodizationConfig
import com.hydrafit.app.core.domain.engine.ProgressWeightsUseCase
import com.hydrafit.app.core.domain.engine.SuggestWeightsUseCase
import com.hydrafit.app.core.domain.engine.SuggestedWeightConfig
import com.hydrafit.app.core.domain.engine.WeeklyPlanSanitizer
import com.hydrafit.app.core.domain.engine.WorkoutPlannerEngineProvider
import com.hydrafit.app.core.domain.fatigue.CalculateMuscleFatigueUseCase
import com.hydrafit.app.core.domain.workout.CorrectWorkoutSetTimeUseCase
import com.hydrafit.app.core.domain.workout.DeleteWorkoutSetUseCase
import com.hydrafit.app.core.domain.workout.EndWorkoutSessionUseCase
import com.hydrafit.app.core.domain.workout.GetWorkoutLogUseCase
import com.hydrafit.app.core.domain.workout.LogWorkoutSetUseCase
import com.hydrafit.app.core.domain.workout.ObserveOpenWorkoutSessionUseCase
import com.hydrafit.app.core.domain.workout.SessionConfig
import com.hydrafit.app.core.domain.workout.StartWorkoutSessionUseCase
import com.hydrafit.app.core.domain.workout.WorkoutLogMutations
import com.hydrafit.app.core.llm.LocalLlmWorkoutPlannerEngine
import com.hydrafit.app.core.llm.OnDeviceEngineLifecycle
import com.hydrafit.app.core.network.ApiKeyProvider
import com.hydrafit.app.core.network.GeminiWorkoutPlannerEngine
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val domainModule: Module = module {
    single { CalculateMuscleFatigueUseCase() }
    single { DeterministicWorkoutPlannerEngine(get()) }
    single { WeeklyPlanSanitizer(get()) }
    single<OnDevicePlanProgressReporter> { DefaultOnDevicePlanProgressReporter() }
    single {
        LocalLlmWorkoutPlannerEngine(
            generator = get(),
            fallback = get<DeterministicWorkoutPlannerEngine>(),
            catalog = get(),
            sanitizer = get(),
            logger = get(),
            progressReporter = get()
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
    single { OnDeviceEngineLifecycle(get(), get()) }
    singleOf(::GenerateWeeklySplitUseCase)
    single { SuggestedWeightConfig() }
    single { PeriodizationConfig() }
    singleOf(::SuggestWeightsUseCase)
    single { BuildRecentWeightsUseCase() }
    single { ProgressWeightsUseCase() }
    singleOf(::ObserveWorkoutPlanInputsUseCase)
    singleOf(::AcceptWeeklyPlanUseCase)
    singleOf(::ObserveAcceptedPlanUseCase)
    singleOf(::LogWorkoutSetUseCase)
    single { SessionConfig() }
    singleOf(::GetWorkoutLogUseCase)
    singleOf(::DeleteWorkoutSetUseCase)
    singleOf(::CorrectWorkoutSetTimeUseCase)
    singleOf(::WorkoutLogMutations)
    singleOf(::StartWorkoutSessionUseCase)
    singleOf(::EndWorkoutSessionUseCase)
    singleOf(::ObserveOpenWorkoutSessionUseCase)
}
