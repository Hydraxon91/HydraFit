package com.hydrafit.app.feature.logger

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val loggerModule: Module = module {
    single { WorkoutLoggerSettings(get(), get()) }
    viewModel {
        WorkoutLoggerViewModel(
            logMutations = get(),
            getWorkoutLog = get(),
            loggingActions = get(),
            exerciseCatalog = get(),
            timeProvider = get(),
            settings = get()
        )
    }
}
