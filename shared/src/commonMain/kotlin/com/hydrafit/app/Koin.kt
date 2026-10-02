package com.hydrafit.app

import com.hydrafit.app.core.database.SeedEquipmentCatalog
import com.hydrafit.app.core.database.SeedExerciseCatalog
import com.hydrafit.app.core.database.WorkoutSessionBackfill
import com.hydrafit.app.core.database.databaseModule
import com.hydrafit.app.core.network.networkModule
import com.hydrafit.app.feature.equipment.equipmentModule
import com.hydrafit.app.feature.fatigueheatmap.fatigueHeatmapModule
import com.hydrafit.app.feature.logger.loggerModule
import com.hydrafit.app.feature.settings.settingsModule
import com.hydrafit.app.feature.splitbuilder.splitBuilderModule
import org.koin.core.context.startKoin
import org.koin.core.module.Module

fun initKoin(platformModule: Module, extraModules: List<Module> = emptyList()) {
    val koinApplication = startKoin {
        modules(
            domainModule,
            databaseModule,
            networkModule,
            equipmentModule,
            fatigueHeatmapModule,
            splitBuilderModule,
            loggerModule,
            settingsModule,
            platformModule
        )
        modules(extraModules)
    }
    koinApplication.koin.get<SeedExerciseCatalog>().seed()
    koinApplication.koin.get<SeedEquipmentCatalog>().seed()
    koinApplication.koin.get<WorkoutSessionBackfill>().backfill()
}
