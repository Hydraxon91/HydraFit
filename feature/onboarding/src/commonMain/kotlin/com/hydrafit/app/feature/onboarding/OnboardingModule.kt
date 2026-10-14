package com.hydrafit.app.feature.onboarding

import com.hydrafit.app.core.domain.setup.SaveSetupStepUseCase
import com.hydrafit.app.core.domain.setup.SkipSetupUseCase
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val onboardingModule: Module = module {
    factory { SaveSetupStepUseCase(get()) }
    factory { SkipSetupUseCase(get()) }
    viewModel {
        OnboardingViewModel(
            setup = get(),
            saveStep = get(),
            skipSetup = get(),
            units = get(),
            equipment = get(),
            selection = get(),
            goals = get(),
            weekdays = get(),
            records = get(),
            catalog = get(),
            readiness = get()
        )
    }
}
