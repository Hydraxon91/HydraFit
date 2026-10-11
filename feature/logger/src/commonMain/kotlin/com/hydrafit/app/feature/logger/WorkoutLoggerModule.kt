package com.hydrafit.app.feature.logger

import org.koin.core.module.Module
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val loggerModule: Module = module {
    single(createdAtStart = true) {
        RestAlertCoordinator(
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get()
        ).also { it.start() }
    }.factory.beanDefinition.onClose { it?.close() }
    single {
        WorkoutLoggerSettings(
            weightUnitRepository = get(),
            guidedWorkoutPreferenceRepository = get(),
            restPreferenceRepository = get(),
            trainingGuidancePreferenceRepository = get(),
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
    viewModel { OccurrenceReplacementViewModel(get(), get()) }
}
