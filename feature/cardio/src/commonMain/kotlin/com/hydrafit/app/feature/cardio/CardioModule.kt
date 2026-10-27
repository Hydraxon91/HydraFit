package com.hydrafit.app.feature.cardio

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val cardioModule: Module = module {
    viewModel {
        CardioViewModel(
            repository = get(),
            distanceUnitRepository = get(),
            timeProvider = get()
        )
    }
}
