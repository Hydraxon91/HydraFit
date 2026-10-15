package com.hydrafit.app.feature.logger

import org.koin.core.module.Module
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val loggerModule: Module = module {
    single { RestCompletionPresentation() }
    single(createdAtStart = true) {
        RestAlertCoordinator(
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            completion = get()
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
    factory { WorkoutLoggerEquipment(get(), get()) }
    viewModel {
        WorkoutLoggerViewModel(
            logMutations = get(),
            getWorkoutLog = get(),
            loggingActions = get(),
            exerciseCatalog = get(),
            runtime = get(),
            settings = get(),
            exerciseNoteRepository = get(),
            sessionNoteRepository = get(),
            equipment = get()
        )
    }
    viewModel { OccurrenceReplacementViewModel(get(), get()) }
    viewModel {
        WorkoutSummaryRecordsViewModel(
            observeOccurrenceRecords = get(),
            exerciseCatalog = get(),
            weightUnitRepository = get(),
            timeProvider = get()
        )
    }
    viewModel {
        WorkoutHistoryViewModel(
            historyRepository = get(),
            exerciseNoteRepository = get(),
            weightUnitRepository = get(),
            timeProvider = get()
        )
    }
}
