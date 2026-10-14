package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.backup.BackupCatalog
import com.hydrafit.app.core.domain.backup.BackupRepository
import com.hydrafit.app.core.domain.backup.BackupStagingRepository
import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.PlanHistoryRepository
import com.hydrafit.app.core.domain.engine.WorkoutPlanSourcesRepository
import com.hydrafit.app.core.domain.routine.RoutineTemplateRepository
import com.hydrafit.app.core.domain.schedule.OccurrenceReplacementRepository
import com.hydrafit.app.core.domain.schedule.ReminderDeliveryRepository
import com.hydrafit.app.core.domain.schedule.WorkoutScheduleRepository
import com.hydrafit.app.core.domain.settings.RestPreferenceRepository
import com.hydrafit.app.core.domain.settings.TrainingGuidancePreferenceRepository
import com.hydrafit.app.core.domain.settings.WorkoutReminderPreferenceRepository
import com.hydrafit.app.core.domain.setup.SetupWriteRepository
import com.hydrafit.app.core.domain.startup.StartupReadiness
import com.hydrafit.app.core.domain.workout.ExerciseNoteRepository
import com.hydrafit.app.core.domain.workout.RestCountdownRepository
import com.hydrafit.app.core.domain.workout.SessionNoteRepository
import com.hydrafit.app.core.domain.workout.SessionResegmenter
import com.hydrafit.app.core.domain.workout.WorkoutHistoryRepository
import com.hydrafit.app.core.domain.workout.WorkoutLogRepository
import com.hydrafit.app.core.domain.workout.WorkoutSessionRepository
import com.hydrafit.app.core.userdata.equipment.CustomExerciseRepository
import com.hydrafit.app.core.userdata.equipment.EquipmentRepository
import com.hydrafit.app.core.userdata.equipment.EquipmentSelectionRepository
import com.hydrafit.app.core.userdata.equipment.ExerciseExclusionRepository
import com.hydrafit.app.core.userdata.equipment.ExerciseOverrideRepository
import com.hydrafit.app.core.userdata.equipment.ExercisePreferenceRepository
import com.hydrafit.app.core.userdata.equipment.PersonalRecordRepository
import com.hydrafit.app.core.userdata.settings.EnginePreferenceRepository
import com.hydrafit.app.core.userdata.settings.GuidedWorkoutPreferenceRepository
import com.hydrafit.app.core.userdata.settings.PreferredWeekdaysRepository
import com.hydrafit.app.core.userdata.settings.SetupRepository
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
    single { SearchProjectionBackfill(get()) }
    single { DatabaseStartupMaintenance(get(), get(), get(), get(), get(), get()) }
    single<StartupReadiness> { get<DatabaseStartupMaintenance>() }
    single<ExerciseCatalog> { SqlDelightExerciseCatalog(get()) }
    single<EquipmentRepository> { SqlDelightEquipmentRepository(get()) }
    single<CustomExerciseRepository> { SqlDelightCustomExerciseRepository(get()) }
    single<ExerciseOverrideRepository> { SqlDelightExerciseOverrideRepository(get()) }
    single<ExercisePreferenceRepository> { SqlDelightExercisePreferenceRepository(get()) }
    single<ExerciseExclusionRepository> { SqlDelightExerciseExclusionRepository(get()) }
    single<EquipmentSelectionRepository> { SqlDelightEquipmentSelectionRepository(get()) }
    single<WorkoutLogRepository> { SqlDelightWorkoutLogRepository(get()) }
    single<WorkoutHistoryRepository> { SqlDelightWorkoutHistoryRepository(get()) }
    single<ExerciseNoteRepository> { SqlDelightExerciseNoteRepository(get()) }
    single<SessionNoteRepository> { SqlDelightSessionNoteRepository(get()) }
    single<WorkoutSessionRepository> { SqlDelightWorkoutSessionRepository(get()) }
    single<SessionResegmenter> { SqlDelightSessionResegmenter(get()) }
    single<EnginePreferenceRepository> { SqlDelightEnginePreferenceRepository(get()) }
    single<GuidedWorkoutPreferenceRepository> { SqlDelightGuidedWorkoutPreferenceRepository(get()) }
    single<TrainingGoalRepository> { SqlDelightTrainingGoalRepository(get()) }
    single<WeightUnitRepository> { SqlDelightWeightUnitRepository(get()) }
    single<SetupRepository> { SqlDelightSetupRepository(get()) }
    single<SetupWriteRepository> { SqlDelightSetupWriteRepository(get(), get()) }
    single<PreferredWeekdaysRepository> { SqlDelightPreferredWeekdaysRepository(get()) }
    single<RestPreferenceRepository> { SqlDelightRestPreferenceRepository(get()) }
    single<TrainingGuidancePreferenceRepository> {
        SqlDelightTrainingGuidancePreferenceRepository(get())
    }
    single<WorkoutReminderPreferenceRepository> {
        SqlDelightWorkoutReminderPreferenceRepository(get())
    }
    single<RestCountdownRepository> { SqlDelightRestCountdownRepository(get()) }
    single<ReminderDeliveryRepository> { SqlDelightReminderDeliveryRepository(get()) }
    single<PlanHistoryRepository> { SqlDelightPlanHistoryRepository(get()) }
    single<RoutineTemplateRepository> { SqlDelightRoutineTemplateRepository(get()) }
    single { SqlDelightWorkoutScheduleRepository(get()) }
    single<WorkoutScheduleRepository> { get<SqlDelightWorkoutScheduleRepository>() }
    single<OccurrenceReplacementRepository> { get<SqlDelightWorkoutScheduleRepository>() }
    single<PersonalRecordRepository> { SqlDelightPersonalRecordRepository(get(), get()) }
    single<BackupRepository> { SqlDelightBackupRepository(get(), get()) }
    single<BackupStagingRepository> { SqlDelightBackupStagingRepository(get()) }
    single<BackupCatalog> { SqlDelightBackupCatalog() }
    single<WorkoutPlanSourcesRepository> {
        SqlDelightWorkoutPlanSourcesRepository(
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get(),
            get()
        )
    }
}
