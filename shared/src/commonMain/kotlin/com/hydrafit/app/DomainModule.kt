package com.hydrafit.app

import com.hydrafit.app.core.domain.engine.DeterministicWorkoutPlannerEngine
import com.hydrafit.app.core.domain.engine.GenerateWeeklySplitUseCase
import com.hydrafit.app.core.domain.engine.WorkoutPlannerEngine
import com.hydrafit.app.core.domain.fatigue.CalculateMuscleFatigueUseCase
import org.koin.core.module.Module
import org.koin.dsl.module

val domainModule: Module = module {
    single { CalculateMuscleFatigueUseCase() }
    single<WorkoutPlannerEngine> { DeterministicWorkoutPlannerEngine(get()) }
    single { GenerateWeeklySplitUseCase(get()) }
}
