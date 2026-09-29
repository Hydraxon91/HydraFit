package com.hydrafit.app.feature.equipment

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val equipmentModule: Module = module {
    viewModel { EquipmentProfilerViewModel(get(), get()) }
}
