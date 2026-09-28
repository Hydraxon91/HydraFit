package com.hydrafit.app

import com.hydrafit.app.core.database.DatabaseDriverFactory
import com.hydrafit.app.core.database.databaseModule
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.feature.equipment.equipmentModule
import com.hydrafit.app.feature.fatigueheatmap.fatigueHeatmapModule
import com.hydrafit.app.feature.splitbuilder.splitBuilderModule
import org.koin.dsl.module
import org.koin.test.verify.verify
import kotlin.test.Test

class KoinModulesVerificationTest {

    private val allModules = module {
        includes(
            domainModule,
            databaseModule,
            equipmentModule,
            fatigueHeatmapModule,
            splitBuilderModule
        )
    }

    @Test
    fun allModulesResolve() {
        allModules.verify(extraTypes = listOf(DatabaseDriverFactory::class, TimeProvider::class))
    }
}
