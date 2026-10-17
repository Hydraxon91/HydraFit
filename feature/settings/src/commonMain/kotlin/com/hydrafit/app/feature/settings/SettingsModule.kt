package com.hydrafit.app.feature.settings

import com.hydrafit.app.core.userdata.llm.OnDeviceModelManager
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val settingsModule: Module = module {
    viewModel { SettingsViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { RestDurationSettingsViewModel(get(), get()) }
    viewModel { TrainingGuidanceSettingsViewModel(get(), get()) }
    viewModel { WorkoutReminderSettingsViewModel(get(), get()) }
    viewModel { AcknowledgmentsViewModel(get()) }
    viewModel {
        BackupViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get())
    }
    viewModel {
        AutomaticBackupSettingsViewModel(
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get()
        )
    }
    viewModel { WorkoutImportViewModel(get(), get(), get(), get()) }
    // The model manager is absent on platforms without an on-device model; the handle reports that
    // absence as "not installed" instead of failing the whole graph.
    single<StorageModelHandle> { OnDeviceModelHandle(getOrNull<OnDeviceModelManager>()) }
    viewModel { StorageDataViewModel(get(), get(), get(), get(), get(), get()) }
}
