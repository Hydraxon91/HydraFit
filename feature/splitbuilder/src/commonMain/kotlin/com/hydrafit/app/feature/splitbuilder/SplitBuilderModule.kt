package com.hydrafit.app.feature.splitbuilder

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val splitBuilderModule: Module = module {
    viewModel { SplitBuilderViewModel(get(), get(), get(), get(), get(), get()) }
}
