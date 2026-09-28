package com.hydrafit.app.feature.fatigueheatmap

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val fatigueHeatmapModule: Module = module {
    viewModel { FatigueHeatmapViewModel(get(), get(), get()) }
}
