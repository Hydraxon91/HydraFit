package com.hydrafit.app.feature.progress

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val progressModule: Module = module {
    viewModel {
        ProgressViewModel(
            progressRepository = get(),
            exerciseCatalog = get(),
            weightUnitRepository = get(),
            distanceUnitRepository = get(),
            timeProvider = get()
        )
    }
    viewModel {
        BodyWeightViewModel(
            bodyWeightRepository = get(),
            weightUnitRepository = get(),
            timeProvider = get()
        )
    }
}
