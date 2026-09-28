package com.hydrafit.app.feature.splitbuilder

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val splitBuilderModule: Module = module {
    viewModel {
        SplitBuilderViewModel(
            observeWorkoutPlanInputs = get(),
            generateWeeklySplit = get(),
            exerciseCatalog = get(),
            enginePreference = get()
        )
    }
}
