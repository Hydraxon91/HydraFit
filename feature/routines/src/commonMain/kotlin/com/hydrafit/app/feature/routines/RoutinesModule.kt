package com.hydrafit.app.feature.routines

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val routinesModule: Module = module {
    viewModel {
        RoutinesViewModel(
            routineActions = get(),
            scheduleActions = get(),
            exerciseCatalog = get(),
            timeProvider = get(),
            weightUnitRepository = get(),
            exclusionRepository = get()
        )
    }
}
