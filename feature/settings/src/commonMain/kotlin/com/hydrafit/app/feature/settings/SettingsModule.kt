package com.hydrafit.app.feature.settings

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val settingsModule: Module = module {
    viewModel { SettingsViewModel(get(), get(), get(), get(), get(), get()) }
    viewModel { RestDurationSettingsViewModel(get(), get()) }
    viewModel { TrainingGuidanceSettingsViewModel(get(), get()) }
    viewModel { WorkoutReminderSettingsViewModel(get(), get()) }
    viewModel { AcknowledgmentsViewModel(get()) }
    viewModel { BackupViewModel(get(), get(), get(), get(), get(), get(), get()) }
    viewModel {
        AutomaticBackupSettingsViewModel(get(), get(), get(), get(), get(), get())
    }
    viewModel { WorkoutImportViewModel(get(), get(), get(), get()) }
}
