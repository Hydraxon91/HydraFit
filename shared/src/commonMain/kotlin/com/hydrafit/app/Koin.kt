package com.hydrafit.app

import com.hydrafit.app.core.database.databaseModule
import org.koin.core.context.startKoin
import org.koin.core.module.Module

fun initKoin(platformModule: Module, extraModules: List<Module> = emptyList()) {
    startKoin {
        modules(databaseModule, platformModule)
        modules(extraModules)
    }
}
