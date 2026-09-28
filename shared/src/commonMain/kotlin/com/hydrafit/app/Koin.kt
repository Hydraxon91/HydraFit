package com.hydrafit.app

import com.hydrafit.app.core.database.SeedExerciseCatalog
import com.hydrafit.app.core.database.databaseModule
import com.hydrafit.app.feature.equipment.equipmentModule
import com.hydrafit.app.feature.fatigueheatmap.fatigueHeatmapModule
import com.hydrafit.app.feature.splitbuilder.splitBuilderModule
import org.koin.core.context.startKoin
import org.koin.core.module.Module

fun initKoin(platformModule: Module, extraModules: List<Module> = emptyList()) {
    val koinApplication = startKoin {
        modules(
            domainModule,
            databaseModule,
            equipmentModule,
            fatigueHeatmapModule,
            splitBuilderModule,
            platformModule
        )
        modules(extraModules)
    }
    koinApplication.koin.get<SeedExerciseCatalog>().seed()
}
