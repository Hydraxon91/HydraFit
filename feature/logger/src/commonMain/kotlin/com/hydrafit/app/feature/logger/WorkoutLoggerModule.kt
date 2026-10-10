package com.hydrafit.app.feature.logger

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val loggerModule: Module = module {
    single { WorkoutLoggerSettings(get(), get(), get()) }
    factory {
        WorkoutLoggerRuntime(
            timeProvider = get(),
            bootIdentityProvider = get(),
            restCountdownRepository = get()
        )
    }
    viewModel {
        WorkoutLoggerViewModel(
            logMutations = get(),
            getWorkoutLog = get(),
            loggingActions = get(),
            exerciseCatalog = get(),
            runtime = get(),
            settings = get()
        )
    }
}
