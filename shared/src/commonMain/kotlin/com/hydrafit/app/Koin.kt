package com.hydrafit.app

import com.hydrafit.app.core.database.DatabaseStartupMaintenance
import com.hydrafit.app.core.database.databaseModule
import com.hydrafit.app.core.network.networkModule
import com.hydrafit.app.feature.cardio.cardioModule
import com.hydrafit.app.feature.equipment.equipmentModule
import com.hydrafit.app.feature.fatigueheatmap.fatigueHeatmapModule
import com.hydrafit.app.feature.logger.loggerModule
import com.hydrafit.app.feature.onboarding.onboardingModule
import com.hydrafit.app.feature.progress.progressModule
import com.hydrafit.app.feature.routines.routinesModule
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
            routinesModule,
            onboardingModule,
            progressModule,
            cardioModule,
            platformModule
        )
        modules(extraModules)
    }
    // Catalog seeding, custom-exercise dedupe and the legacy session backfill run off the main
    // thread; the app shell gates its first screen on StartupReadiness.
    koinApplication.koin.get<DatabaseStartupMaintenance>().start()
}
