package com.hydrafit.app.feature.logger

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val loggerModule: Module = module {
    single {
        WorkoutLoggerSettings(
            weightUnitRepository = get(),
            guidedWorkoutPreferenceRepository = get(),
            restPreferenceRepository = get(),
            resolveRestDurationUseCase = get(),
            setExerciseRestDurationUseCase = get(),
            clearExerciseRestDurationUseCase = get()
        )
    }
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
