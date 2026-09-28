package com.hydrafit.app.feature.logger

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val loggerModule: Module = module {
    viewModel {
        WorkoutLoggerViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get())
    }
}
