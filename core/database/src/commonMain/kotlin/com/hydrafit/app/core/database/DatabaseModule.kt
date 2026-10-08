package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.PlanHistoryRepository
import com.hydrafit.app.core.domain.engine.WorkoutPlanSourcesRepository
import com.hydrafit.app.core.domain.routine.RoutineTemplateRepository
import com.hydrafit.app.core.domain.schedule.WorkoutScheduleRepository
import com.hydrafit.app.core.domain.startup.StartupReadiness
import com.hydrafit.app.core.domain.workout.SessionResegmenter
import com.hydrafit.app.core.domain.workout.WorkoutLogRepository
import com.hydrafit.app.core.domain.workout.WorkoutSessionRepository
import com.hydrafit.app.core.userdata.equipment.CustomExerciseRepository
import com.hydrafit.app.core.userdata.equipment.EquipmentRepository
import com.hydrafit.app.core.userdata.equipment.EquipmentSelectionRepository
import com.hydrafit.app.core.userdata.equipment.ExerciseOverrideRepository
import com.hydrafit.app.core.userdata.equipment.ExercisePreferenceRepository
import com.hydrafit.app.core.userdata.equipment.PersonalRecordRepository
import com.hydrafit.app.core.userdata.settings.EnginePreferenceRepository
import com.hydrafit.app.core.userdata.settings.TrainingGoalRepository
import com.hydrafit.app.core.userdata.settings.WeightUnitRepository
import org.koin.core.module.Module
import org.koin.dsl.module

val databaseModule: Module = module {
    single { HydraFitDatabase(get<DatabaseDriverFactory>().createDriver()) }
    single { SeedExerciseCatalog(get()) }
    single { SeedEquipmentCatalog(get()) }
    single { CustomExerciseDedupe(get()) }
    single { WorkoutSessionBackfill(get(), get()) }
    single { DatabaseStartupMaintenance(get(), get(), get(), get()) }
    single<StartupReadiness> { get<DatabaseStartupMaintenance>() }
    single<ExerciseCatalog> { SqlDelightExerciseCatalog(get()) }
    single<EquipmentRepository> { SqlDelightEquipmentRepository(get()) }
    single<CustomExerciseRepository> { SqlDelightCustomExerciseRepository(get()) }
    single<ExerciseOverrideRepository> { SqlDelightExerciseOverrideRepository(get()) }
    single<ExercisePreferenceRepository> { SqlDelightExercisePreferenceRepository(get()) }
    single<EquipmentSelectionRepository> { SqlDelightEquipmentSelectionRepository(get()) }
    single<WorkoutLogRepository> { SqlDelightWorkoutLogRepository(get()) }
    single<WorkoutSessionRepository> { SqlDelightWorkoutSessionRepository(get()) }
    single<SessionResegmenter> { SqlDelightSessionResegmenter(get()) }
    single<EnginePreferenceRepository> { SqlDelightEnginePreferenceRepository(get()) }
    single<TrainingGoalRepository> { SqlDelightTrainingGoalRepository(get()) }
    single<WeightUnitRepository> { SqlDelightWeightUnitRepository(get()) }
    single<PlanHistoryRepository> { SqlDelightPlanHistoryRepository(get()) }
    single<RoutineTemplateRepository> { SqlDelightRoutineTemplateRepository(get()) }
    single<WorkoutScheduleRepository> { SqlDelightWorkoutScheduleRepository(get()) }
    single<PersonalRecordRepository> { SqlDelightPersonalRecordRepository(get(), get()) }
    single<WorkoutPlanSourcesRepository> {
        SqlDelightWorkoutPlanSourcesRepository(get(), get(), get(), get(), get(), get(), get())
    }
}
