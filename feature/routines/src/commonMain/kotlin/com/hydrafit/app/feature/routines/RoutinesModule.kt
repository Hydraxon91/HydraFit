package com.hydrafit.app.feature.routines

import org.koin.core.module.Module
import org.koin.core.module.dsl.onClose
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val routinesModule: Module = module {
    single(createdAtStart = true) {
        WorkoutReminderCoordinator(
            preferences = get(),
            schedule = get(),
            deliveries = get(),
            reminders = get(),
            timeProvider = get(),
            readiness = get(),
            gate = get()
        ).also { it.start() }
    }.factory.beanDefinition.onClose { it?.close() }
    viewModel {
        RoutinesViewModel(
            routineActions = get(),
            scheduleActions = get(),
            exerciseCatalog = get(),
            timeProvider = get(),
            weightUnitRepository = get(),
            exclusionRepository = get(),
            preferredWeekdaysRepository = get()
        )
    }
}
