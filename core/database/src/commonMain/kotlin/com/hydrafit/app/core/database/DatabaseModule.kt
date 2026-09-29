package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.PlanHistoryRepository
import com.hydrafit.app.core.domain.engine.WorkoutPlanSourcesRepository
import com.hydrafit.app.core.domain.workout.WorkoutLogRepository
import com.hydrafit.app.core.userdata.equipment.CustomExerciseRepository
import com.hydrafit.app.core.userdata.equipment.EquipmentRepository
import com.hydrafit.app.core.userdata.equipment.EquipmentSelectionRepository
import com.hydrafit.app.core.userdata.equipment.ExerciseEquipmentRepository
import com.hydrafit.app.core.userdata.equipment.ExerciseMuscleRepository
import com.hydrafit.app.core.userdata.settings.EnginePreferenceRepository
import com.hydrafit.app.core.userdata.settings.TrainingGoalRepository
import org.koin.core.module.Module
import org.koin.dsl.module

val databaseModule: Module = module {
    single { HydraFitDatabase(get<DatabaseDriverFactory>().createDriver()) }
    single { SeedExerciseCatalog(get()) }
    single { SeedEquipmentCatalog(get()) }
    single<ExerciseCatalog> { SqlDelightExerciseCatalog(get()) }
    single<EquipmentRepository> { SqlDelightEquipmentRepository(get()) }
    single<CustomExerciseRepository> { SqlDelightCustomExerciseRepository(get()) }
    single<ExerciseEquipmentRepository> { SqlDelightExerciseEquipmentRepository(get()) }
    single<ExerciseMuscleRepository> { SqlDelightExerciseMuscleRepository(get()) }
    single<EquipmentSelectionRepository> { SqlDelightEquipmentSelectionRepository(get()) }
    single<WorkoutLogRepository> { SqlDelightWorkoutLogRepository(get()) }
    single<EnginePreferenceRepository> { SqlDelightEnginePreferenceRepository(get()) }
    single<TrainingGoalRepository> { SqlDelightTrainingGoalRepository(get()) }
    single<PlanHistoryRepository> { SqlDelightPlanHistoryRepository(get()) }
    single<WorkoutPlanSourcesRepository> {
        SqlDelightWorkoutPlanSourcesRepository(get(), get(), get(), get())
    }
}
