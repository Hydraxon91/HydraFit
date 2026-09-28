package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.userdata.equipment.EquipmentSelectionRepository
import org.koin.core.module.Module
import org.koin.dsl.module

val databaseModule: Module = module {
    single { HydraFitDatabase(get<DatabaseDriverFactory>().createDriver()) }
    single<ExerciseCatalog> { SqlDelightExerciseCatalog(get()) }
    single<EquipmentSelectionRepository> { SqlDelightEquipmentSelectionRepository(get()) }
}
