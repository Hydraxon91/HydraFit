package com.hydrafit.app

import com.hydrafit.app.core.database.DatabaseDriverFactory
import com.hydrafit.app.core.database.databaseModule
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.feature.equipment.equipmentModule
import com.hydrafit.app.feature.fatigueheatmap.fatigueHeatmapModule
import com.hydrafit.app.feature.logger.loggerModule
import com.hydrafit.app.feature.splitbuilder.splitBuilderModule
import kotlin.test.Test
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.module
import org.koin.test.verify.verify

@OptIn(KoinExperimentalAPI::class)
class KoinModulesVerificationTest {

    private val allModules = module {
        includes(
            domainModule,
            databaseModule,
            equipmentModule,
            fatigueHeatmapModule,
            splitBuilderModule,
            loggerModule
        )
    }

    @Test
    fun allModulesResolve() {
        allModules.verify(extraTypes = listOf(DatabaseDriverFactory::class, TimeProvider::class))
    }
}
