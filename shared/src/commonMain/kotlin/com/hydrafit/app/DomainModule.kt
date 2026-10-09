package com.hydrafit.app

import com.hydrafit.app.core.domain.backup.ApplyStagedBackupUseCase
import com.hydrafit.app.core.domain.backup.BackupValidator
import com.hydrafit.app.core.domain.backup.ExportBackupUseCase
import com.hydrafit.app.core.domain.backup.PreviewBackupUseCase
import com.hydrafit.app.core.domain.backup.RestoreBackupUseCase
import com.hydrafit.app.core.domain.engine.AcceptWeeklyPlanUseCase
import com.hydrafit.app.core.domain.engine.BuildPlannerLoadInputsUseCase
import com.hydrafit.app.core.domain.engine.BuildRecentWeightsUseCase
import com.hydrafit.app.core.domain.engine.DefaultOnDevicePlanProgressReporter
import com.hydrafit.app.core.domain.engine.DeterministicWorkoutPlannerEngine
import com.hydrafit.app.core.domain.engine.EngineAvailability
import com.hydrafit.app.core.domain.engine.GenerateWeeklySplitUseCase
import com.hydrafit.app.core.domain.engine.ObserveAcceptedPlanUseCase
import com.hydrafit.app.core.domain.engine.ObserveWorkoutPlanInputsUseCase
import com.hydrafit.app.core.domain.engine.OnDevicePlanProgressReporter
import com.hydrafit.app.core.domain.engine.PeriodizationConfig
import com.hydrafit.app.core.domain.engine.PlanBuilderActions
import com.hydrafit.app.core.domain.engine.ProgressWeightsUseCase
import com.hydrafit.app.core.domain.engine.SubstituteExerciseUseCase
import com.hydrafit.app.core.domain.engine.SuggestWeightsUseCase
import com.hydrafit.app.core.domain.engine.SuggestedWeightConfig
import com.hydrafit.app.core.domain.engine.WeeklyPlanSanitizer
import com.hydrafit.app.core.domain.engine.WorkoutPlannerEngineProvider
import com.hydrafit.app.core.domain.fatigue.CalculateMuscleFatigueUseCase
import com.hydrafit.app.core.domain.routine.ArchiveRoutineTemplateUseCase
import com.hydrafit.app.core.domain.routine.ConvertPlanToTemplateUseCase
import com.hydrafit.app.core.domain.routine.DeleteRoutineTemplateUseCase
import com.hydrafit.app.core.domain.routine.DuplicateRoutineTemplateUseCase
import com.hydrafit.app.core.domain.routine.ObserveRoutineTemplatesUseCase
import com.hydrafit.app.core.domain.routine.RoutineTemplateActions
import com.hydrafit.app.core.domain.routine.SaveRoutineTemplateUseCase
import com.hydrafit.app.core.domain.schedule.ActivateRoutineUseCase
import com.hydrafit.app.core.domain.schedule.CancelTrainingActivationUseCase
import com.hydrafit.app.core.domain.schedule.CreateTrainingActivationUseCase
import com.hydrafit.app.core.domain.schedule.EditUnstartedOccurrenceUseCase
import com.hydrafit.app.core.domain.schedule.FinishTrainingBlockUseCase
import com.hydrafit.app.core.domain.schedule.FinishWorkoutOccurrenceUseCase
import com.hydrafit.app.core.domain.schedule.MoveWorkoutOccurrenceUseCase
import com.hydrafit.app.core.domain.schedule.PreviewWorkoutScheduleUseCase
import com.hydrafit.app.core.domain.schedule.RepeatTrainingBlockUseCase
import com.hydrafit.app.core.domain.schedule.SelectWorkoutOccurrenceUseCase
import com.hydrafit.app.core.domain.schedule.SkipWorkoutOccurrenceUseCase
import com.hydrafit.app.core.domain.schedule.StartWorkoutOccurrenceUseCase
import com.hydrafit.app.core.domain.schedule.SwitchScheduleModeUseCase
import com.hydrafit.app.core.domain.schedule.WorkoutLoggingActions
import com.hydrafit.app.core.domain.schedule.WorkoutScheduleActions
import com.hydrafit.app.core.domain.workout.CorrectWorkoutSetTimeUseCase
import com.hydrafit.app.core.domain.workout.DeleteWorkoutSetUseCase
import com.hydrafit.app.core.domain.workout.EndWorkoutSessionUseCase
import com.hydrafit.app.core.domain.workout.GetWorkoutLogUseCase
import com.hydrafit.app.core.domain.workout.LogWorkoutSetUseCase
import com.hydrafit.app.core.domain.workout.ObserveOpenWorkoutSessionUseCase
import com.hydrafit.app.core.domain.workout.SessionConfig
import com.hydrafit.app.core.domain.workout.StartWorkoutSessionUseCase
import com.hydrafit.app.core.domain.workout.WorkoutLogMutations
import com.hydrafit.app.core.llm.LocalLlmWorkoutPlannerEngine
import com.hydrafit.app.core.llm.OnDeviceEngineLifecycle
import com.hydrafit.app.core.network.ApiKeyProvider
import com.hydrafit.app.core.network.GeminiWorkoutPlannerEngine
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val domainModule: Module = module {
    single { CalculateMuscleFatigueUseCase() }
    singleOf(::ExportBackupUseCase)
    singleOf(::BackupValidator)
    singleOf(::PreviewBackupUseCase)
    singleOf(::RestoreBackupUseCase)
    singleOf(::ApplyStagedBackupUseCase)
    single { DeterministicWorkoutPlannerEngine(get(), weightConfig = get(), periodization = get()) }
    single { WeeklyPlanSanitizer(get(), periodization = get(), weightConfig = get()) }
    single<OnDevicePlanProgressReporter> { DefaultOnDevicePlanProgressReporter() }
    single {
        LocalLlmWorkoutPlannerEngine(
            generator = get(),
            fallback = get<DeterministicWorkoutPlannerEngine>(),
            catalog = get(),
            sanitizer = get(),
            logger = get(),
            progressReporter = get()
        )
    }
    single<EngineAvailability> { DefaultEngineAvailability(get<ApiKeyProvider>(), get()) }
    single<WorkoutPlannerEngineProvider> {
        DefaultWorkoutPlannerEngineProvider(
            preference = get(),
            deterministic = get<DeterministicWorkoutPlannerEngine>(),
            gemini = get<GeminiWorkoutPlannerEngine>(),
            localLlm = get<LocalLlmWorkoutPlannerEngine>(),
            apiKeyProvider = get<ApiKeyProvider>()
        )
    }
    single { OnDeviceEngineLifecycle(get(), get()) }
    singleOf(::GenerateWeeklySplitUseCase)
    single { SuggestedWeightConfig() }
    single { PeriodizationConfig() }
    singleOf(::SuggestWeightsUseCase)
    single { BuildRecentWeightsUseCase() }
    single { ProgressWeightsUseCase() }
    singleOf(::BuildPlannerLoadInputsUseCase)
    singleOf(::ObserveWorkoutPlanInputsUseCase)
    singleOf(::AcceptWeeklyPlanUseCase)
    singleOf(::SubstituteExerciseUseCase)
    singleOf(::PlanBuilderActions)
    singleOf(::ObserveAcceptedPlanUseCase)
    singleOf(::LogWorkoutSetUseCase)
    single { SessionConfig() }
    singleOf(::GetWorkoutLogUseCase)
    singleOf(::DeleteWorkoutSetUseCase)
    singleOf(::CorrectWorkoutSetTimeUseCase)
    singleOf(::WorkoutLogMutations)
    singleOf(::StartWorkoutSessionUseCase)
    singleOf(::EndWorkoutSessionUseCase)
    singleOf(::ObserveOpenWorkoutSessionUseCase)
    single { ConvertPlanToTemplateUseCase() }
    singleOf(::SaveRoutineTemplateUseCase)
    singleOf(::DuplicateRoutineTemplateUseCase)
    singleOf(::ArchiveRoutineTemplateUseCase)
    singleOf(::DeleteRoutineTemplateUseCase)
    singleOf(::ObserveRoutineTemplatesUseCase)
    single { PreviewWorkoutScheduleUseCase() }
    singleOf(::CreateTrainingActivationUseCase)
    singleOf(::ActivateRoutineUseCase)
    singleOf(::RepeatTrainingBlockUseCase)
    singleOf(::SelectWorkoutOccurrenceUseCase)
    singleOf(::StartWorkoutOccurrenceUseCase)
    singleOf(::FinishWorkoutOccurrenceUseCase)
    singleOf(::SkipWorkoutOccurrenceUseCase)
    singleOf(::FinishTrainingBlockUseCase)
    singleOf(::CancelTrainingActivationUseCase)
    singleOf(::MoveWorkoutOccurrenceUseCase)
    singleOf(::SwitchScheduleModeUseCase)
    singleOf(::EditUnstartedOccurrenceUseCase)
    singleOf(::RoutineTemplateActions)
    singleOf(::WorkoutScheduleActions)
    singleOf(::WorkoutLoggingActions)
}
