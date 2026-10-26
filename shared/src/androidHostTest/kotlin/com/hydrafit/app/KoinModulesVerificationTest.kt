package com.hydrafit.app

import com.hydrafit.app.core.database.DatabaseDriverFactory
import com.hydrafit.app.core.database.databaseModule
import com.hydrafit.app.core.domain.backup.AutomaticBackupScheduler
import com.hydrafit.app.core.domain.backup.AutomaticBackupStateStore
import com.hydrafit.app.core.domain.backup.BACKUP_FORMAT
import com.hydrafit.app.core.domain.backup.BACKUP_FORMAT_VERSION
import com.hydrafit.app.core.domain.backup.BackupApplyError
import com.hydrafit.app.core.domain.backup.BackupCatalog
import com.hydrafit.app.core.domain.backup.BackupCatalogManifest
import com.hydrafit.app.core.domain.backup.BackupCrypto
import com.hydrafit.app.core.domain.backup.BackupEncryption
import com.hydrafit.app.core.domain.backup.BackupEncryptionKeyStore
import com.hydrafit.app.core.domain.backup.BackupFile
import com.hydrafit.app.core.domain.backup.BackupFolderStore
import com.hydrafit.app.core.domain.backup.BackupMergeKeys
import com.hydrafit.app.core.domain.backup.BackupMergeSelection
import com.hydrafit.app.core.domain.backup.BackupRepository
import com.hydrafit.app.core.domain.backup.BackupRestPreferenceRecord
import com.hydrafit.app.core.domain.backup.BackupStagingRepository
import com.hydrafit.app.core.domain.backup.ConfigureAutomaticBackupFolderUseCase
import com.hydrafit.app.core.domain.backup.DisableAutomaticBackupEncryptionUseCase
import com.hydrafit.app.core.domain.backup.EnableAutomaticBackupEncryptionUseCase
import com.hydrafit.app.core.domain.backup.PendingBackup
import com.hydrafit.app.core.domain.backup.RecoverAutomaticBackupEncryptionUseCase
import com.hydrafit.app.core.domain.backup.RunAutomaticBackupUseCase
import com.hydrafit.app.core.domain.backup.SetAutomaticBackupsEnabledUseCase
import com.hydrafit.app.core.domain.backup.UnsupportedAutomaticBackupScheduler
import com.hydrafit.app.core.domain.backup.UnsupportedBackupCrypto
import com.hydrafit.app.core.domain.backup.UnsupportedBackupEncryptionKeyStore
import com.hydrafit.app.core.domain.backup.VerifyBackupSnapshotUseCase
import com.hydrafit.app.core.domain.engine.AcceptWeeklyPlanUseCase
import com.hydrafit.app.core.domain.engine.AcceptedPlan
import com.hydrafit.app.core.domain.engine.BuildPlannerLoadInputsUseCase
import com.hydrafit.app.core.domain.engine.DeterministicWorkoutPlannerEngine
import com.hydrafit.app.core.domain.engine.EngineAvailability
import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.engine.ObserveAcceptedPlanUseCase
import com.hydrafit.app.core.domain.engine.ObserveWorkoutPlanInputsUseCase
import com.hydrafit.app.core.domain.engine.OnDevicePlanProgress
import com.hydrafit.app.core.domain.engine.PersonalRecord
import com.hydrafit.app.core.domain.engine.PlanBuilderActions
import com.hydrafit.app.core.domain.engine.PlanHistoryRepository
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.SetAcceptedExerciseMeasureUseCase
import com.hydrafit.app.core.domain.engine.SubstituteExerciseUseCase
import com.hydrafit.app.core.domain.engine.SuggestWeightsUseCase
import com.hydrafit.app.core.domain.engine.TrainingGoal
import com.hydrafit.app.core.domain.engine.WeeklyPlanSanitizer
import com.hydrafit.app.core.domain.engine.WorkoutPlanSources
import com.hydrafit.app.core.domain.engine.WorkoutPlanSourcesRepository
import com.hydrafit.app.core.domain.equipment.Equipment
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.ExerciseExclusion
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.ExercisePreference
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.LoggedSet
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.routine.ArchiveRoutineTemplateUseCase
import com.hydrafit.app.core.domain.routine.ConvertPlanToTemplateUseCase
import com.hydrafit.app.core.domain.routine.DeleteRoutineTemplateUseCase
import com.hydrafit.app.core.domain.routine.DuplicateRoutineTemplateUseCase
import com.hydrafit.app.core.domain.routine.ObserveRoutineTemplatesUseCase
import com.hydrafit.app.core.domain.routine.RoutineTemplate
import com.hydrafit.app.core.domain.routine.RoutineTemplateActions
import com.hydrafit.app.core.domain.routine.RoutineTemplateRepository
import com.hydrafit.app.core.domain.routine.SaveRoutineTemplateUseCase
import com.hydrafit.app.core.domain.schedule.ActivateRoutineUseCase
import com.hydrafit.app.core.domain.schedule.CancelTrainingActivationUseCase
import com.hydrafit.app.core.domain.schedule.CreateTrainingActivationUseCase
import com.hydrafit.app.core.domain.schedule.EditUnstartedOccurrenceUseCase
import com.hydrafit.app.core.domain.schedule.FinishTrainingBlockUseCase
import com.hydrafit.app.core.domain.schedule.FinishWorkoutOccurrenceUseCase
import com.hydrafit.app.core.domain.schedule.MoveWorkoutOccurrenceUseCase
import com.hydrafit.app.core.domain.schedule.OccurrenceEntry
import com.hydrafit.app.core.domain.schedule.OccurrenceReplacementRepository
import com.hydrafit.app.core.domain.schedule.PreviewWorkoutScheduleUseCase
import com.hydrafit.app.core.domain.schedule.ReminderDeliveryRepository
import com.hydrafit.app.core.domain.schedule.RepeatTrainingBlockUseCase
import com.hydrafit.app.core.domain.schedule.ReplaceOccurrenceExerciseUseCase
import com.hydrafit.app.core.domain.schedule.SelectWorkoutOccurrenceUseCase
import com.hydrafit.app.core.domain.schedule.SkipWorkoutOccurrenceUseCase
import com.hydrafit.app.core.domain.schedule.StartWorkoutOccurrenceUseCase
import com.hydrafit.app.core.domain.schedule.SwitchScheduleModeUseCase
import com.hydrafit.app.core.domain.schedule.TrainingActivation
import com.hydrafit.app.core.domain.schedule.UnsupportedWorkoutReminderScheduler
import com.hydrafit.app.core.domain.schedule.WorkoutLoggingActions
import com.hydrafit.app.core.domain.schedule.WorkoutOccurrence
import com.hydrafit.app.core.domain.schedule.WorkoutReminderDeliveryGate
import com.hydrafit.app.core.domain.schedule.WorkoutReminderScheduler
import com.hydrafit.app.core.domain.schedule.WorkoutScheduleActions
import com.hydrafit.app.core.domain.schedule.WorkoutScheduleRepository
import com.hydrafit.app.core.domain.schedule.WorkoutScheduleState
import com.hydrafit.app.core.domain.settings.RestPreferenceRepository
import com.hydrafit.app.core.domain.settings.TrainingGuidancePreferenceRepository
import com.hydrafit.app.core.domain.settings.TrainingGuidancePreferences
import com.hydrafit.app.core.domain.settings.WorkoutReminderPreferenceRepository
import com.hydrafit.app.core.domain.settings.WorkoutReminderPreferences
import com.hydrafit.app.core.domain.setup.SetupCommit
import com.hydrafit.app.core.domain.setup.SetupState
import com.hydrafit.app.core.domain.setup.SetupWriteRepository
import com.hydrafit.app.core.domain.startup.StartupReadiness
import com.hydrafit.app.core.domain.storage.StorageUsagePort
import com.hydrafit.app.core.domain.time.BootIdentityProvider
import com.hydrafit.app.core.domain.time.DayOfWeek
import com.hydrafit.app.core.domain.time.TimeProvider
import com.hydrafit.app.core.domain.transfer.PrescriptionImportPreview
import com.hydrafit.app.core.domain.transfer.PrescriptionImportRepository
import com.hydrafit.app.core.domain.transfer.PrescriptionImportResult
import com.hydrafit.app.core.domain.transfer.WorkoutCsvSource
import com.hydrafit.app.core.domain.transfer.WorkoutImportPreview
import com.hydrafit.app.core.domain.transfer.WorkoutImportProvenance
import com.hydrafit.app.core.domain.transfer.WorkoutImportRepository
import com.hydrafit.app.core.domain.transfer.WorkoutImportResult
import com.hydrafit.app.core.domain.unit.DistanceUnit
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.workout.CorrectWorkoutSetTimeUseCase
import com.hydrafit.app.core.domain.workout.CorrectWorkoutSetUseCase
import com.hydrafit.app.core.domain.workout.DeleteWorkoutSetUseCase
import com.hydrafit.app.core.domain.workout.EndWorkoutSessionUseCase
import com.hydrafit.app.core.domain.workout.ExerciseHistoryPage
import com.hydrafit.app.core.domain.workout.ExerciseHistoryRequest
import com.hydrafit.app.core.domain.workout.ExerciseNoteRepository
import com.hydrafit.app.core.domain.workout.ExerciseProgress
import com.hydrafit.app.core.domain.workout.ExerciseProgressRepository
import com.hydrafit.app.core.domain.workout.ExerciseProgressRequest
import com.hydrafit.app.core.domain.workout.GetWorkoutLogUseCase
import com.hydrafit.app.core.domain.workout.GetWorkoutSummaryUseCase
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.LogWorkoutSetUseCase
import com.hydrafit.app.core.domain.workout.ObserveOccurrenceRecordsUseCase
import com.hydrafit.app.core.domain.workout.ObserveOpenWorkoutSessionUseCase
import com.hydrafit.app.core.domain.workout.PerformedClusterSegment
import com.hydrafit.app.core.domain.workout.PersistedRestCountdown
import com.hydrafit.app.core.domain.workout.RestAlertScheduler
import com.hydrafit.app.core.domain.workout.RestCountdownRepository
import com.hydrafit.app.core.domain.workout.SessionNoteRepository
import com.hydrafit.app.core.domain.workout.SessionResegmenter
import com.hydrafit.app.core.domain.workout.StartWorkoutSessionUseCase
import com.hydrafit.app.core.domain.workout.UnsupportedRestAlertScheduler
import com.hydrafit.app.core.domain.workout.WarmupAction
import com.hydrafit.app.core.domain.workout.WarmupPreparationItem
import com.hydrafit.app.core.domain.workout.WarmupPreparationOverrideRepository
import com.hydrafit.app.core.domain.workout.WorkoutCluster
import com.hydrafit.app.core.domain.workout.WorkoutHistoryEntry
import com.hydrafit.app.core.domain.workout.WorkoutHistoryKey
import com.hydrafit.app.core.domain.workout.WorkoutHistoryPage
import com.hydrafit.app.core.domain.workout.WorkoutHistoryRepository
import com.hydrafit.app.core.domain.workout.WorkoutHistoryRequest
import com.hydrafit.app.core.domain.workout.WorkoutHistorySet
import com.hydrafit.app.core.domain.workout.WorkoutHistorySetPage
import com.hydrafit.app.core.domain.workout.WorkoutHistorySetRequest
import com.hydrafit.app.core.domain.workout.WorkoutLogRepository
import com.hydrafit.app.core.domain.workout.WorkoutSession
import com.hydrafit.app.core.domain.workout.WorkoutSessionRepository
import com.hydrafit.app.core.domain.workout.WorkoutSet
import com.hydrafit.app.core.domain.workout.WorkoutSetCorrection
import com.hydrafit.app.core.llm.NoopOnDevicePlannerLogger
import com.hydrafit.app.core.llm.OnDevicePlannerLogger
import com.hydrafit.app.core.llm.OnDeviceTextGenerator
import com.hydrafit.app.core.network.ApiKeyProvider
import com.hydrafit.app.core.network.GeminiWorkoutPlannerEngine
import com.hydrafit.app.core.userdata.backup.BackupFileStore
import com.hydrafit.app.core.userdata.backup.UnsupportedAutomaticBackupStateStore
import com.hydrafit.app.core.userdata.backup.UnsupportedBackupFileStore
import com.hydrafit.app.core.userdata.backup.UnsupportedBackupFolderStore
import com.hydrafit.app.core.userdata.equipment.CustomExerciseRepository
import com.hydrafit.app.core.userdata.equipment.EquipmentProfileRepository
import com.hydrafit.app.core.userdata.equipment.EquipmentRepository
import com.hydrafit.app.core.userdata.equipment.EquipmentSelectionRepository
import com.hydrafit.app.core.userdata.equipment.ExerciseExclusionRepository
import com.hydrafit.app.core.userdata.equipment.ExerciseOverrideRepository
import com.hydrafit.app.core.userdata.equipment.ExercisePreferenceRepository
import com.hydrafit.app.core.userdata.equipment.PersonalRecordRepository
import com.hydrafit.app.core.userdata.settings.ApiKeyStore
import com.hydrafit.app.core.userdata.settings.AppVersionProvider
import com.hydrafit.app.core.userdata.settings.DistanceUnitRepository
import com.hydrafit.app.core.userdata.settings.EnginePreferenceRepository
import com.hydrafit.app.core.userdata.settings.GuidedWorkoutPreferenceRepository
import com.hydrafit.app.core.userdata.settings.PreferredWeekdaysRepository
import com.hydrafit.app.core.userdata.settings.SetupRepository
import com.hydrafit.app.core.userdata.settings.TrainingGoalRepository
import com.hydrafit.app.core.userdata.settings.WeightUnitRepository
import com.hydrafit.app.core.userdata.storage.UnsupportedStorageUsagePort
import com.hydrafit.app.core.userdata.transfer.PrescriptionDocumentStore
import com.hydrafit.app.core.userdata.transfer.PrescriptionPdfRenderer
import com.hydrafit.app.core.userdata.transfer.UnsupportedPrescriptionDocumentStore
import com.hydrafit.app.core.userdata.transfer.UnsupportedPrescriptionPdfRenderer
import com.hydrafit.app.feature.equipment.EquipmentProfilerViewModel
import com.hydrafit.app.feature.equipment.ExercisePlanningSettingsViewModel
import com.hydrafit.app.feature.equipment.equipmentModule
import com.hydrafit.app.feature.fatigueheatmap.fatigueHeatmapModule
import com.hydrafit.app.feature.logger.LoggerLoadingCalculatorViewModel
import com.hydrafit.app.feature.logger.OccurrenceReplacementViewModel
import com.hydrafit.app.feature.logger.WorkoutHistoryViewModel
import com.hydrafit.app.feature.logger.WorkoutLoggerViewModel
import com.hydrafit.app.feature.logger.WorkoutSummaryRecordsViewModel
import com.hydrafit.app.feature.logger.loggerModule
import com.hydrafit.app.feature.onboarding.OnboardingEntryViewModel
import com.hydrafit.app.feature.onboarding.OnboardingViewModel
import com.hydrafit.app.feature.onboarding.onboardingModule
import com.hydrafit.app.feature.progress.ProgressViewModel
import com.hydrafit.app.feature.progress.progressModule
import com.hydrafit.app.feature.routines.PrescriptionShareViewModel
import com.hydrafit.app.feature.routines.RoutinesViewModel
import com.hydrafit.app.feature.routines.WorkoutReminderCoordinator
import com.hydrafit.app.feature.routines.routinesModule
import com.hydrafit.app.feature.settings.AcknowledgmentsViewModel
import com.hydrafit.app.feature.settings.AutomaticBackupSettingsViewModel
import com.hydrafit.app.feature.settings.BackupViewModel
import com.hydrafit.app.feature.settings.SettingsViewModel
import com.hydrafit.app.feature.settings.TrainingGuidanceSettingsViewModel
import com.hydrafit.app.feature.settings.WorkoutImportViewModel
import com.hydrafit.app.feature.settings.WorkoutReminderSettingsViewModel
import com.hydrafit.app.feature.settings.settingsModule
import com.hydrafit.app.feature.splitbuilder.PlanExportViewModel
import com.hydrafit.app.feature.splitbuilder.splitBuilderModule
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.verify.verify

@OptIn(KoinExperimentalAPI::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class KoinModulesVerificationTest {

    /**
     * Every test runs with a test Main dispatcher installed.
     *
     * Coroutines cannot initialise a Main dispatcher on a host JVM, and it reports that as an
     * `IllegalStateException` the moment anything is dispatched to `Dispatchers.Main` while no test
     * dispatcher is installed — including after a `resetMain()`. Installing one before every test
     * body (and resetting after, below) means no test can observe that state, and no ViewModel that
     * reads `viewModelScope` on init can trigger it. The unconfined dispatcher is deliberate: it
     * runs work eagerly, so nothing stays queued on Main past the end of a test.
     */
    @BeforeTest
    fun installMainDispatcher() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    /**
     * Resets after every test, so one test's installation can never become the next one's problem.
     *
     * The reset is only safe because [installMainDispatcher] runs first for the next test and no test
     * body touches `Dispatchers.Main` after this point: coroutines reports an unset Main the moment
     * anything is dispatched to it, including after a reset. A test body that installs its own
     * dispatcher must therefore drain it after closing its Koin graph and before resetting Main, so no
     * scope-cancellation handler is still dispatching when the reset runs.
     */
    @AfterTest
    fun resetMainDispatcher() {
        Dispatchers.resetMain()
    }

    private val testPlatformModule = module {
        single<TimeProvider> { TimeProvider { 0L } }
        single<BootIdentityProvider> { BootIdentityProvider { null } }
        single<ApiKeyStore> { FakeApiKeyStore }
        single<AppVersionProvider> { FakeAppVersionProvider }
        single<ApiKeyProvider> { ApiKeyProvider { "test-key" } }
        single<OnDeviceTextGenerator> { FakeOnDeviceTextGenerator }
        single<OnDevicePlannerLogger> { NoopOnDevicePlannerLogger }
        single<BackupFileStore> { UnsupportedBackupFileStore() }
        single<BackupFolderStore> { UnsupportedBackupFolderStore() }
        single<StorageUsagePort> { UnsupportedStorageUsagePort() }
        single<AutomaticBackupStateStore> { UnsupportedAutomaticBackupStateStore() }
        single<AutomaticBackupScheduler> { UnsupportedAutomaticBackupScheduler() }
        single<BackupCrypto> { UnsupportedBackupCrypto() }
        single<BackupEncryptionKeyStore> { UnsupportedBackupEncryptionKeyStore() }
        single<PrescriptionDocumentStore> { UnsupportedPrescriptionDocumentStore() }
        single<PrescriptionPdfRenderer> { UnsupportedPrescriptionPdfRenderer() }
        single<RestAlertScheduler> { UnsupportedRestAlertScheduler() }
        single<WorkoutReminderScheduler> { UnsupportedWorkoutReminderScheduler() }
        single<OccurrenceReplacementRepository> {
            object : OccurrenceReplacementRepository {
                override suspend fun replaceUntouchedEntry(
                    occurrenceId: Long,
                    expectedRevision: Int,
                    replacement: OccurrenceEntry,
                    nowMillis: Long
                ) = error("No replacement writes are expected in graph verification")
            }
        }
    }

    private val allModules = module {
        includes(
            domainModule,
            databaseModule,
            equipmentModule,
            fatigueHeatmapModule,
            splitBuilderModule,
            loggerModule,
            settingsModule,
            routinesModule,
            onboardingModule,
            progressModule,
            testPlatformModule
        )
    }

    @Test
    fun allModulesResolve() {
        allModules.verify(
            extraTypes = listOf(
                DatabaseDriverFactory::class,
                GeminiWorkoutPlannerEngine::class,
                // Automatic-backup ports are bound by the platform modules.
                AutomaticBackupScheduler::class,
                AutomaticBackupStateStore::class,
                BackupCrypto::class,
                BackupEncryptionKeyStore::class,
                BackupFolderStore::class,
                // The onboarding ViewModels default their dispatcher; verify still checks the type.
                CoroutineDispatcher::class
            )
        )
    }

    /**
     * `verify()` does not reflect the constructor of lambda/`singleOf` definitions, so a missing
     * collaborator (e.g. `ProgressWeightsUseCase`) can slip through and only crash on device. Boot a
     * real container over the domain graph (with fakes for the database-backed repositories) and
     * resolve the `singleOf` use cases, so a missing binding fails here instead.
     *
     * `GenerateWeeklySplitUseCase` is omitted because its provider resolves the Gemini engine, which
     * lives in `networkModule` and is not on `:shared`'s test classpath.
     */
    @Test
    fun theDomainUseCaseGraphResolvesAtRuntime() {
        val koin = koinApplication {
            modules(
                module {
                    single<WorkoutPlanSourcesRepository> { FakeWorkoutPlanSourcesRepository }
                    single<PlanHistoryRepository> { FakePlanHistoryRepository }
                    single<ExerciseCatalog> { FakeExerciseCatalog }
                    single<WorkoutLogRepository> { FakeWorkoutLogRepository }
                    single<WorkoutSessionRepository> { FakeWorkoutSessionRepository }
                    single<SessionResegmenter> { FakeSessionResegmenter }
                    single<RoutineTemplateRepository> { FakeRoutineTemplateRepository }
                    single<WorkoutScheduleRepository> { FakeWorkoutScheduleRepository }
                    single<SessionNoteRepository> { FakeSessionNoteRepository }
                    single<BackupCatalog> { FakeBackupCatalog }
                    single<BackupRepository> { FakeBackupRepository }
                },
                domainModule,
                testPlatformModule
            )
        }.koin

        try {
            assertNotNull(koin.get<BuildPlannerLoadInputsUseCase>())
            assertNotNull(koin.get<DeterministicWorkoutPlannerEngine>())
            assertNotNull(koin.get<VerifyBackupSnapshotUseCase>())
            assertNotNull(koin.get<RunAutomaticBackupUseCase>())
            assertNotNull(koin.get<ConfigureAutomaticBackupFolderUseCase>())
            assertNotNull(koin.get<SetAutomaticBackupsEnabledUseCase>())
            assertNotNull(koin.get<BackupEncryption>())
            assertNotNull(koin.get<EnableAutomaticBackupEncryptionUseCase>())
            assertNotNull(koin.get<DisableAutomaticBackupEncryptionUseCase>())
            assertNotNull(koin.get<RecoverAutomaticBackupEncryptionUseCase>())
            assertNotNull(koin.get<WeeklyPlanSanitizer>())
            assertNotNull(koin.get<ObserveWorkoutPlanInputsUseCase>())
            assertNotNull(koin.get<AcceptWeeklyPlanUseCase>())
            assertNotNull(koin.get<SubstituteExerciseUseCase>())
            assertNotNull(koin.get<SetAcceptedExerciseMeasureUseCase>())
            assertNotNull(koin.get<ReplaceOccurrenceExerciseUseCase>())
            assertNotNull(koin.get<PlanBuilderActions>())
            assertNotNull(koin.get<ObserveAcceptedPlanUseCase>())
            assertNotNull(koin.get<LogWorkoutSetUseCase>())
            assertNotNull(koin.get<GetWorkoutLogUseCase>())
            assertNotNull(koin.get<GetWorkoutSummaryUseCase>())
            assertNotNull(koin.get<DeleteWorkoutSetUseCase>())
            assertNotNull(koin.get<CorrectWorkoutSetTimeUseCase>())
            assertNotNull(koin.get<CorrectWorkoutSetUseCase>())
            assertNotNull(koin.get<StartWorkoutSessionUseCase>())
            assertNotNull(koin.get<EndWorkoutSessionUseCase>())
            assertNotNull(koin.get<ObserveOpenWorkoutSessionUseCase>())
            assertNotNull(koin.get<SuggestWeightsUseCase>())
            assertNotNull(koin.get<ConvertPlanToTemplateUseCase>())
            assertNotNull(koin.get<SaveRoutineTemplateUseCase>())
            assertNotNull(koin.get<DuplicateRoutineTemplateUseCase>())
            assertNotNull(koin.get<ArchiveRoutineTemplateUseCase>())
            assertNotNull(koin.get<DeleteRoutineTemplateUseCase>())
            assertNotNull(koin.get<ObserveRoutineTemplatesUseCase>())
            assertNotNull(koin.get<RoutineTemplateActions>())
            assertNotNull(koin.get<WorkoutScheduleActions>())
            assertNotNull(koin.get<WorkoutLoggingActions>())
            assertNotNull(koin.get<PreviewWorkoutScheduleUseCase>())
            assertNotNull(koin.get<CreateTrainingActivationUseCase>())
            assertNotNull(koin.get<ActivateRoutineUseCase>())
            assertNotNull(koin.get<RepeatTrainingBlockUseCase>())
            assertNotNull(koin.get<SelectWorkoutOccurrenceUseCase>())
            assertNotNull(koin.get<StartWorkoutOccurrenceUseCase>())
            assertNotNull(koin.get<FinishWorkoutOccurrenceUseCase>())
            assertNotNull(koin.get<SkipWorkoutOccurrenceUseCase>())
            assertNotNull(koin.get<FinishTrainingBlockUseCase>())
            assertNotNull(koin.get<CancelTrainingActivationUseCase>())
            assertNotNull(koin.get<MoveWorkoutOccurrenceUseCase>())
            assertNotNull(koin.get<SwitchScheduleModeUseCase>())
            assertNotNull(koin.get<EditUnstartedOccurrenceUseCase>())
        } finally {
            koin.close()
        }
    }

    /**
     * `verify()` cannot reflect a lambda definition's `get()` chain, so boot the real
     * `settingsModule` plus the production Android version module and resolve the new VM, proving
     * the Acknowledgments binding and `AppVersionProvider` are wired at runtime.
     */
    @Test
    fun theAcknowledgmentsViewModelResolvesFromTheProductionVersionModule() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val koin = koinApplication {
            modules(settingsModule, appVersionModule("1.2.3"))
        }.koin

        try {
            assertEquals("1.2.3", koin.get<AppVersionProvider>().versionName)
            assertNotNull(koin.get<AcknowledgmentsViewModel>())
        } finally {
            koin.close()
        }
    }

    @Test
    fun guidedWorkoutSettingsViewModelResolvesFromKoin() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val koin = koinApplication {
            modules(
                module {
                    single<EngineAvailability> {
                        object : EngineAvailability {
                            override fun availableEngines() = listOf(PlannerEngineId.DETERMINISTIC)
                        }
                    }
                    single<EnginePreferenceRepository> { FakeEnginePreferenceRepository() }
                    single<TrainingGoalRepository> {
                        object : TrainingGoalRepository {
                            override suspend fun selectedGoal() = TrainingGoal.BALANCED
                            override fun goalFlow() = flowOf(TrainingGoal.BALANCED)
                            override suspend fun setGoal(goal: TrainingGoal) = Unit
                        }
                    }
                    single<WeightUnitRepository> {
                        object : WeightUnitRepository {
                            override suspend fun selectedUnit() = WeightUnit.KG
                            override fun unitFlow() = flowOf(WeightUnit.KG)
                            override suspend fun setUnit(unit: WeightUnit) = Unit
                        }
                    }
                    single<DistanceUnitRepository> {
                        object : DistanceUnitRepository {
                            override suspend fun selectedUnit() = DistanceUnit.METERS
                            override fun unitFlow() = flowOf(DistanceUnit.METERS)
                            override suspend fun setUnit(unit: DistanceUnit) = Unit
                        }
                    }
                    single<GuidedWorkoutPreferenceRepository> {
                        object : GuidedWorkoutPreferenceRepository {
                            override suspend fun isGuidedWorkoutEnabled() = false
                            override fun guidedWorkoutFlow() = flowOf(false)
                            override suspend fun setGuidedWorkoutEnabled(enabled: Boolean) = Unit
                        }
                    }
                },
                testPlatformModule,
                module {
                    single<TrainingGuidancePreferenceRepository> { FakeTrainingGuidancePreferences }
                },
                settingsModule
            )
        }.koin
        try {
            koin.get<SettingsViewModel>()
            assertNotNull(koin.get<TrainingGuidanceSettingsViewModel>())
            advanceUntilIdle()
        } finally {
            koin.close()
            // Closing the resolved scopes cancels their Main-dispatched work; drain it before the
            // reset, or the reset races a live Main dispatch and the next test inherits the leak.
            advanceUntilIdle()
            Dispatchers.resetMain()
        }
    }

    /**
     * The reminder ViewModel is a lambda `viewModel { }`, so resolve it over `settingsModule` with a
     * fake preference repository and the test platform module's scheduler binding.
     */
    @Test
    fun theWorkoutReminderSettingsViewModelResolvesAtRuntime() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val koin = koinApplication {
            modules(
                module {
                    single<WorkoutReminderPreferenceRepository> { FakeWorkoutReminderPreferences }
                },
                settingsModule,
                testPlatformModule
            )
        }.koin

        try {
            assertNotNull(koin.get<WorkoutReminderSettingsViewModel>())
            advanceUntilIdle()
        } finally {
            koin.close()
            // Closing the resolved scopes cancels their Main-dispatched work; drain it before the
            // reset, or the reset races a live Main dispatch and the next test inherits the leak.
            advanceUntilIdle()
            Dispatchers.resetMain()
        }
    }

    private object FakeWorkoutReminderPreferences : WorkoutReminderPreferenceRepository {
        override fun observe() = flowOf(WorkoutReminderPreferences())

        override suspend fun get() = WorkoutReminderPreferences()

        override suspend fun setEnabled(enabled: Boolean) = Unit

        override suspend fun setReminderMinuteOfDay(minuteOfDay: Int?) = Unit

        override suspend fun setQuietHoursEnabled(enabled: Boolean) = Unit

        override suspend fun setQuietHours(startMinuteOfDay: Int, endMinuteOfDay: Int) = Unit
    }

    /**
     * The preference ViewModel is registered with a lambda `viewModel { }`, which `verify()` cannot
     * reflect, so resolve it to prove its constructor `get()` chain. The repositories it depends on
     * are proved against the real databaseModule in `DatabaseModuleVerificationTest`.
     */
    @Test
    fun theExercisePlanningSettingsViewModelResolvesAtRuntime() {
        // This ViewModel reads viewModelScope on init, so it must never be resolved before a test
        // Main dispatcher is installed.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val koin = koinApplication {
            modules(
                module {
                    single<ExercisePreferenceRepository> { FakeExercisePreferenceRepository }
                    single<ExerciseExclusionRepository> { FakeExerciseExclusionRepository }
                    single<TimeProvider> { TimeProvider { 0L } }
                },
                equipmentModule
            )
        }.koin

        try {
            assertNotNull(koin.get<ExercisePlanningSettingsViewModel>())
        } finally {
            koin.close()
        }
    }

    /**
     * `EquipmentProfilerViewModel` is also a lambda `viewModel { }` (and now consumes the profile
     * repository), so `verify()` cannot reflect it. Resolve it over `equipmentModule` with fake ports
     * so a missing constructor dependency fails here instead of on device. It reads profile/equipment
     * state on init, so it runs on the test Main dispatcher and performs no writes.
     */
    @Test
    fun theEquipmentProfilerViewModelResolvesAtRuntime() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val koin = koinApplication {
            modules(
                module {
                    single<EquipmentRepository> { FakeEquipmentRepository }
                    single<EquipmentProfileRepository> { FakeEquipmentProfiles }
                    single<ExerciseCatalog> { FakeExerciseCatalog }
                    single<ExerciseOverrideRepository> { FakeExerciseOverrideRepository }
                    single<CustomExerciseRepository> { FakeCustomExerciseRepository }
                    single<PersonalRecordRepository> { FakePersonalRecordRepository }
                },
                equipmentModule
            )
        }.koin

        try {
            assertNotNull(koin.get<EquipmentProfilerViewModel>())
            advanceUntilIdle()
        } finally {
            koin.close()
            // Closing the resolved scopes cancels their Main-dispatched work; drain it before the
            // reset, or the reset races a live Main dispatch and the next test inherits the leak.
            advanceUntilIdle()
            Dispatchers.resetMain()
        }
    }

    /**
     * The backup ViewModel is a lambda `viewModel { }`, so resolve it over `settingsModule` +
     * `domainModule` with a fake repository/catalog and the test platform module.
     */
    @Test
    fun theBackupViewModelResolvesAtRuntime() {
        // Reads viewModelScope on init: install the test Main dispatcher before resolving it.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val koin = koinApplication {
            modules(
                module {
                    single<BackupRepository> { FakeBackupRepository }
                    single<BackupStagingRepository> { FakeBackupStagingRepository }
                    single<BackupCatalog> { FakeBackupCatalog }
                    // BackupViewModel also injects ExportWorkoutHistoryCsvUseCase (history-CSV export).
                    single<WorkoutLogRepository> { FakeWorkoutLogRepository }
                    single<WorkoutSessionRepository> { FakeWorkoutSessionRepository }
                    single<ExerciseCatalog> { FakeExerciseCatalog }
                },
                domainModule,
                settingsModule,
                testPlatformModule
            )
        }.koin

        try {
            assertNotNull(koin.get<BackupViewModel>())
        } finally {
            koin.close()
        }
    }

    /**
     * The automatic-backup ViewModel is a lambda `viewModel { }` over the automation use cases and
     * the platform ports (folder/state/scheduler fakes in [testPlatformModule]); resolving it proves
     * the constructor `get()` chain, including the fail-soft unsupported state store.
     */
    @Test
    fun theAutomaticBackupSettingsViewModelResolvesAtRuntime() {
        // Reads viewModelScope on init: install the test Main dispatcher before resolving it.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val koin = koinApplication {
            modules(
                module {
                    single<BackupRepository> { FakeBackupRepository }
                    single<BackupCatalog> { FakeBackupCatalog }
                },
                domainModule,
                settingsModule,
                testPlatformModule
            )
        }.koin

        try {
            assertNotNull(koin.get<AutomaticBackupSettingsViewModel>())
        } finally {
            koin.close()
        }
    }

    /**
     * The import ViewModel is a lambda `viewModel { }`, so resolve it over `settingsModule` with fake
     * ports. Construction reads and writes nothing, so this only proves the constructor `get()` chain.
     */
    @Test
    fun theWorkoutImportViewModelResolvesAtRuntime() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val koin = koinApplication {
            modules(
                module {
                    single<WorkoutImportRepository> { FakeWorkoutImportRepository }
                    single<WorkoutSessionRepository> { FakeWorkoutSessionRepository }
                    single<ExerciseCatalog> { FakeExerciseCatalog }
                },
                settingsModule,
                testPlatformModule
            )
        }.koin

        try {
            assertNotNull(koin.get<WorkoutImportViewModel>())
        } finally {
            koin.close()
        }
    }

    private object FakeWorkoutImportRepository : WorkoutImportRepository {
        override suspend fun provenance(
            source: WorkoutCsvSource,
            candidateKeys: Set<String>
        ): List<WorkoutImportProvenance> = emptyList()

        override suspend fun commit(preview: WorkoutImportPreview): WorkoutImportResult =
            WorkoutImportResult(importedSessions = 0, importedSets = 0, skippedSessions = 0)
    }

    private object FakeBackupRepository : BackupRepository {
        override suspend fun export(appVersion: String, exportedAtMillis: Long): BackupFile =
            emptyBackupFile(appVersion, exportedAtMillis)

        override suspend fun restore(file: BackupFile) = Unit

        override suspend fun mergeKeys(): BackupMergeKeys = throw UnsupportedOperationException()

        override suspend fun merge(selection: BackupMergeSelection) =
            throw UnsupportedOperationException()
    }

    private object FakeBackupCatalog : BackupCatalog {
        override fun seedExerciseIds(): Set<String> = emptySet()

        override fun builtInEquipmentIds(): Set<String> = emptySet()
    }

    private object FakeBackupStagingRepository : BackupStagingRepository {
        override suspend fun stage(payload: String, appVersion: String, stagedAtMillis: Long) = Unit

        override suspend fun staged(): PendingBackup? = null

        override suspend fun clearStaged() = Unit

        override suspend fun recordApplyError(
            failure: com.hydrafit.app.core.domain.backup.BackupFailure,
            message: String?,
            occurredAtMillis: Long
        ) = Unit

        override suspend fun applyError(): BackupApplyError? = null

        override suspend fun clearApplyError() = Unit
    }

    /**
     * The routines ViewModel and its eager reminder coordinator are lambda definitions with a real
     * constructor `get()` chain, so resolve them over `routinesModule` + `domainModule` with fake
     * repositories. Startup readiness stays false so the coordinator only proves construction.
     */
    @Test
    fun theRoutinesViewModelResolvesAtRuntime() {
        // Reads viewModelScope on init: install the test Main dispatcher before resolving it.
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val koin = koinApplication {
            modules(
                module {
                    single<WorkoutPlanSourcesRepository> { FakeWorkoutPlanSourcesRepository }
                    single<PlanHistoryRepository> { FakePlanHistoryRepository }
                    single<ExerciseCatalog> { FakeExerciseCatalog }
                    single<WorkoutLogRepository> { FakeWorkoutLogRepository }
                    single<WorkoutSessionRepository> { FakeWorkoutSessionRepository }
                    single<SessionResegmenter> { FakeSessionResegmenter }
                    single<RoutineTemplateRepository> { FakeRoutineTemplateRepository }
                    single<WorkoutScheduleRepository> { FakeWorkoutScheduleRepository }
                    single<WeightUnitRepository> { FakeWeightUnitRepository }
                    single<DistanceUnitRepository> { FakeDistanceUnitRepository }
                    single<ExerciseExclusionRepository> { FakeExerciseExclusionRepository }
                    single<PreferredWeekdaysRepository> { FakePreferredWeekdaysRepository }
                    single { WorkoutReminderDeliveryGate() }
                    single<WorkoutReminderPreferenceRepository> {
                        object : WorkoutReminderPreferenceRepository {
                            override fun observe() = flowOf(WorkoutReminderPreferences())

                            override suspend fun get() = WorkoutReminderPreferences()

                            override suspend fun setEnabled(enabled: Boolean) = Unit

                            override suspend fun setReminderMinuteOfDay(minuteOfDay: Int?) = Unit

                            override suspend fun setQuietHoursEnabled(enabled: Boolean) = Unit

                            override suspend fun setQuietHours(
                                startMinuteOfDay: Int,
                                endMinuteOfDay: Int
                            ) = Unit
                        }
                    }
                    single<ReminderDeliveryRepository> {
                        object : ReminderDeliveryRepository {
                            override suspend fun isDelivered(
                                activationId: Long,
                                reminderEpochDay: Long
                            ) = false

                            override suspend fun claim(
                                activationId: Long,
                                reminderEpochDay: Long,
                                deliveredAtMillis: Long
                            ) = false

                            override suspend fun release(
                                activationId: Long,
                                reminderEpochDay: Long
                            ) = Unit
                        }
                    }
                    single<StartupReadiness> {
                        object : StartupReadiness {
                            override val isReady = MutableStateFlow(false)
                        }
                    }
                },
                domainModule,
                routinesModule,
                testPlatformModule
            )
        }.koin

        try {
            assertNotNull(koin.get<RoutinesViewModel>())
            assertNotNull(koin.get<WorkoutReminderCoordinator>())
        } finally {
            koin.close()
        }
    }

    /**
     * The prescription transfer ViewModel is a lambda `viewModel { }`, so `verify()` cannot reflect
     * its constructor. Resolve it over `routinesModule` + `domainModule` with fake ports so a missing
     * collaborator fails here instead of on device. Construction reads and writes nothing.
     */
    @Test
    fun thePrescriptionShareViewModelResolvesAtRuntime() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val koin = koinApplication {
            modules(
                module {
                    single<WorkoutPlanSourcesRepository> { FakeWorkoutPlanSourcesRepository }
                    single<PlanHistoryRepository> { FakePlanHistoryRepository }
                    single<ExerciseCatalog> { FakeExerciseCatalog }
                    single<WorkoutLogRepository> { FakeWorkoutLogRepository }
                    single<WorkoutSessionRepository> { FakeWorkoutSessionRepository }
                    single<SessionResegmenter> { FakeSessionResegmenter }
                    single<RoutineTemplateRepository> { FakeRoutineTemplateRepository }
                    single<WorkoutScheduleRepository> { FakeWorkoutScheduleRepository }
                    single<WeightUnitRepository> { FakeWeightUnitRepository }
                    single<ExerciseExclusionRepository> { FakeExerciseExclusionRepository }
                    single<PreferredWeekdaysRepository> { FakePreferredWeekdaysRepository }
                    single<PrescriptionImportRepository> { FakePrescriptionImportRepository }
                    single<EquipmentSelectionRepository> { FakeEquipmentSelectionRepository }
                    single { WorkoutReminderDeliveryGate() }
                    single<WorkoutReminderPreferenceRepository> { FakeWorkoutReminderPreferences }
                    single<ReminderDeliveryRepository> {
                        object : ReminderDeliveryRepository {
                            override suspend fun isDelivered(
                                activationId: Long,
                                reminderEpochDay: Long
                            ) = false

                            override suspend fun claim(
                                activationId: Long,
                                reminderEpochDay: Long,
                                deliveredAtMillis: Long
                            ) = false

                            override suspend fun release(
                                activationId: Long,
                                reminderEpochDay: Long
                            ) = Unit
                        }
                    }
                    single<StartupReadiness> {
                        object : StartupReadiness {
                            override val isReady = MutableStateFlow(false)
                        }
                    }
                },
                domainModule,
                routinesModule,
                testPlatformModule
            )
        }.koin

        try {
            assertNotNull(koin.get<PrescriptionShareViewModel>())
        } finally {
            koin.close()
        }
    }

    /**
     * The plan-export ViewModel is a lambda `viewModel { }`, so resolve it over `splitBuilderModule`
     * + `domainModule` with fake ports and the test platform module's renderer/document bindings.
     */
    @Test
    fun thePlanExportViewModelResolvesAtRuntime() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val koin = koinApplication {
            modules(
                module {
                    single<WorkoutPlanSourcesRepository> { FakeWorkoutPlanSourcesRepository }
                    single<PlanHistoryRepository> { FakePlanHistoryRepository }
                    single<ExerciseCatalog> { FakeExerciseCatalog }
                    single<WorkoutLogRepository> { FakeWorkoutLogRepository }
                    single<WorkoutSessionRepository> { FakeWorkoutSessionRepository }
                    single<SessionResegmenter> { FakeSessionResegmenter }
                    single<RoutineTemplateRepository> { FakeRoutineTemplateRepository }
                    single<WorkoutScheduleRepository> { FakeWorkoutScheduleRepository }
                    single<EnginePreferenceRepository> { FakeEnginePreferenceRepository() }
                },
                domainModule,
                splitBuilderModule,
                testPlatformModule
            )
        }.koin

        try {
            assertNotNull(koin.get<PlanExportViewModel>())
        } finally {
            koin.close()
        }
    }

    private object FakePrescriptionImportRepository : PrescriptionImportRepository {
        override suspend fun importPrescriptions(
            preview: PrescriptionImportPreview
        ): PrescriptionImportResult =
            PrescriptionImportResult(importedRoutines = 0, createdExercises = 0)
    }

    /**
     * Both onboarding ViewModels are lambda `viewModel { }` definitions, so resolve them over
     * `onboardingModule` with fake ports. The entry ViewModel reads on init, which also proves the
     * Main-dispatcher harness; constructing either must not write setup state.
     */
    @Test
    fun theOnboardingViewModelsResolveAtRuntime() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val koin = koinApplication {
            modules(
                module {
                    single<SetupRepository> { FakeSetupRepository }
                    single<SetupWriteRepository> { FakeSetupWriteRepository }
                    single<WeightUnitRepository> { FakeWeightUnitRepository }
                    single<EquipmentRepository> { FakeEquipmentRepository }
                    single<EquipmentSelectionRepository> { FakeEquipmentSelectionRepository }
                    single<EquipmentProfileRepository> { FakeEquipmentProfiles }
                    single<TrainingGoalRepository> { FakeTrainingGoalRepository }
                    single<PreferredWeekdaysRepository> { FakePreferredWeekdaysRepository }
                    single<PersonalRecordRepository> { FakePersonalRecordRepository }
                    single<ExerciseCatalog> { FakeExerciseCatalog }
                    single<StartupReadiness> {
                        object : StartupReadiness {
                            override val isReady = MutableStateFlow(true)
                        }
                    }
                },
                onboardingModule
            )
        }.koin
        try {
            assertNotNull(koin.get<OnboardingViewModel>())
            assertNotNull(koin.get<OnboardingEntryViewModel>())
            advanceUntilIdle()
        } finally {
            koin.close()
            // Closing the resolved scopes cancels their Main-dispatched work; drain it before the
            // reset, or the reset races a live Main dispatch and the next test inherits the leak.
            advanceUntilIdle()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun theWorkoutLoggerViewModelResolvesAtRuntime() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val koin = koinApplication {
            modules(
                module {
                    single<WorkoutPlanSourcesRepository> { FakeWorkoutPlanSourcesRepository }
                    single<PlanHistoryRepository> { FakePlanHistoryRepository }
                    single<ExerciseCatalog> { FakeExerciseCatalog }
                    single<WorkoutLogRepository> { FakeWorkoutLogRepository }
                    single<EquipmentProfileRepository> { FakeEquipmentProfiles }
                    single<ExerciseNoteRepository> { FakeExerciseNoteRepository }
                    single<SessionNoteRepository> { FakeSessionNoteRepository }
                    single<WarmupPreparationOverrideRepository> {
                        FakeWarmupPreparationOverrideRepository
                    }
                    single<WorkoutHistoryRepository> { FakeWorkoutHistoryRepository }
                    single<ExerciseProgressRepository> { FakeExerciseProgressRepository }
                    single<WorkoutSessionRepository> { FakeWorkoutSessionRepository }
                    single<SessionResegmenter> { FakeSessionResegmenter }
                    single<RoutineTemplateRepository> { FakeRoutineTemplateRepository }
                    single<WorkoutScheduleRepository> { FakeWorkoutScheduleRepository }
                    single<WeightUnitRepository> { FakeWeightUnitRepository }
                    single<DistanceUnitRepository> { FakeDistanceUnitRepository }
                    single<RestPreferenceRepository> { FakeRestPreferenceRepository }
                    single<RestCountdownRepository> { FakeRestCountdownRepository }
                    single<TrainingGuidancePreferenceRepository> { FakeTrainingGuidancePreferences }
                    single<StartupReadiness> {
                        object : StartupReadiness {
                            override val isReady = MutableStateFlow(false)
                        }
                    }
                    single<GuidedWorkoutPreferenceRepository> {
                        object : GuidedWorkoutPreferenceRepository {
                            override suspend fun isGuidedWorkoutEnabled() = false

                            override fun guidedWorkoutFlow() = flowOf(false)

                            override suspend fun setGuidedWorkoutEnabled(enabled: Boolean) = Unit
                        }
                    }
                    single<EquipmentRepository> {
                        object : EquipmentRepository {
                            override fun observeAll(): Flow<List<Equipment>> = flowOf(emptyList())

                            override suspend fun all(): List<Equipment> = emptyList()

                            override suspend fun add(name: String): Equipment = error("unused")

                            override suspend fun remove(id: EquipmentTag) = error("unused")

                            override suspend fun rename(
                                id: EquipmentTag,
                                name: String,
                                maxWeightKg: Double?
                            ): Equipment = error("unused")

                            override suspend fun setMaxWeight(
                                id: EquipmentTag,
                                maxWeightKg: Double?
                            ) = error("unused")
                        }
                    }
                    single<EquipmentSelectionRepository> {
                        object : EquipmentSelectionRepository {
                            override suspend fun selected(): Set<EquipmentTag> = emptySet()

                            override fun selectedFlow(): Flow<Set<EquipmentTag>> =
                                flowOf(emptySet())

                            override suspend fun setSelected(tags: Set<EquipmentTag>) =
                                error("unused")
                        }
                    }
                },
                domainModule,
                loggerModule,
                testPlatformModule
            )
        }.koin

        try {
            assertNotNull(koin.get<WorkoutLoggerViewModel>())
            // The loading helper resolves over loggerModule with the shared profile fake.
            assertNotNull(koin.get<LoggerLoadingCalculatorViewModel>())
            assertNotNull(koin.get<ObserveOccurrenceRecordsUseCase>())
            assertNotNull(koin.get<WorkoutSummaryRecordsViewModel>())
            val history = koin.get<WorkoutHistoryViewModel>()
            advanceUntilIdle()
            assertEquals(true, history.state.value.showEmptyHistory)
            val replacement = koin.get<OccurrenceReplacementViewModel>()
            replacement.open(999L, 999L)
            assertNotNull(koin.get<com.hydrafit.app.feature.logger.RestAlertCoordinator>())
            assertNotNull(koin.get<com.hydrafit.app.feature.logger.RestCompletionPresentation>())
            advanceUntilIdle()
            assertNotNull(replacement.state.value.failure)
            replacement.dismiss()
            assertEquals(false, replacement.state.value.open)
        } finally {
            koin.close()
            // Closing the resolved scopes cancels their Main-dispatched work; drain it before the
            // reset, or the reset races a live Main dispatch and the next test inherits the leak.
            advanceUntilIdle()
            Dispatchers.resetMain()
        }
    }

    /**
     * The Progress ViewModel is a lambda `viewModel { }`, so resolve it over `progressModule` with
     * fake ports and the test platform module's Main dispatcher, proving its `get()` chain and the
     * new `ExerciseProgressRepository` seam. Constructing it must not write.
     */
    @Test
    fun theProgressViewModelResolvesAtRuntime() {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        val koin = koinApplication {
            modules(
                module {
                    single<ExerciseProgressRepository> { FakeExerciseProgressRepository }
                    single<ExerciseCatalog> { FakeExerciseCatalog }
                    single<WeightUnitRepository> { FakeWeightUnitRepository }
                    single<DistanceUnitRepository> { FakeDistanceUnitRepository }
                },
                progressModule,
                testPlatformModule
            )
        }.koin
        try {
            assertNotNull(koin.get<ProgressViewModel>())
        } finally {
            koin.close()
            // Closing the resolved scopes cancels their Main-dispatched work; drain it before the
            // reset, or the reset races a live Main dispatch.
            dispatcher.scheduler.advanceUntilIdle()
            Dispatchers.resetMain()
        }
    }

    private object FakeExerciseProgressRepository : ExerciseProgressRepository {
        override fun observeHistory(request: ExerciseHistoryRequest) =
            flowOf(ExerciseHistoryPage(emptyList(), hasMore = false))

        override fun observeProgress(request: ExerciseProgressRequest) =
            flowOf(ExerciseProgress(emptyList(), emptyList(), emptyList()))

        override fun observeStationOptions(exerciseId: String) =
            flowOf(emptyList<com.hydrafit.app.core.domain.equipment.StationAttribution>())

        override fun observeOccurrenceExerciseIds(occurrenceId: Long) = flowOf(emptyList<String>())

        override fun observeSet(exerciseId: String, setId: Long) = flowOf<WorkoutHistorySet?>(null)
    }

    private object FakeApiKeyStore : ApiKeyStore {
        override fun load(): String? = "test-key"

        override fun save(apiKey: String) = Unit

        override fun clear() = Unit
    }

    private class FakeEnginePreferenceRepository : EnginePreferenceRepository {
        private var shareWorkoutData = false

        override suspend fun selectedEngine() = PlannerEngineId.DETERMINISTIC

        override fun engineFlow(): Flow<PlannerEngineId> = flowOf(PlannerEngineId.DETERMINISTIC)

        override suspend fun setEngine(engine: PlannerEngineId) = Unit

        override suspend fun selectedDaysPerWeek() = 4

        override fun daysPerWeekFlow(): Flow<Int> = flowOf(4)

        override suspend fun setDaysPerWeek(daysPerWeek: Int) = Unit

        override suspend fun isWorkoutDataSharingEnabled() = shareWorkoutData

        override fun workoutDataSharingFlow(): Flow<Boolean> = flowOf(shareWorkoutData)

        override suspend fun setWorkoutDataSharingEnabled(enabled: Boolean) {
            shareWorkoutData = enabled
        }
    }

    private object FakeAppVersionProvider : AppVersionProvider {
        override val versionName: String = "test"
    }

    private object FakeExercisePreferenceRepository : ExercisePreferenceRepository {
        override fun observe(): Flow<Map<String, ExercisePreference>> = flowOf(emptyMap())

        override suspend fun preference(exerciseId: String): ExercisePreference =
            ExercisePreference.NEUTRAL

        override suspend fun set(exerciseId: String, preference: ExercisePreference) = Unit
    }

    private object FakeExerciseExclusionRepository : ExerciseExclusionRepository {
        override fun observe(): Flow<List<ExerciseExclusion>> = flowOf(emptyList())

        override suspend fun exclusion(exerciseId: String): ExerciseExclusion? = null

        override suspend fun set(exclusion: ExerciseExclusion) = Unit

        override suspend fun clear(exerciseId: String) = Unit
    }

    private object FakeWeightUnitRepository : WeightUnitRepository {
        override suspend fun selectedUnit(): WeightUnit = WeightUnit.KG

        override fun unitFlow(): Flow<WeightUnit> = flowOf(WeightUnit.KG)

        override suspend fun setUnit(unit: WeightUnit) = Unit
    }

    private object FakeDistanceUnitRepository : DistanceUnitRepository {
        override suspend fun selectedUnit(): DistanceUnit = DistanceUnit.METERS

        override fun unitFlow(): Flow<DistanceUnit> = flowOf(DistanceUnit.METERS)

        override suspend fun setUnit(unit: DistanceUnit) = Unit
    }

    private object FakeRestPreferenceRepository : RestPreferenceRepository {
        override fun globalDefaultSecondsFlow() = flowOf(120L)
        override suspend fun globalDefaultSeconds() = 120L
        override suspend fun setGlobalDefaultSeconds(seconds: Long) = Unit
        override suspend fun exerciseOverrideSeconds(exerciseId: String): Long? = null
        override suspend fun setExerciseOverrideSeconds(exerciseId: String, seconds: Long) = Unit
        override suspend fun clearExerciseOverride(exerciseId: String) = Unit
    }

    private object FakeRestCountdownRepository : RestCountdownRepository {
        override fun observe() = MutableStateFlow<PersistedRestCountdown?>(null)
        override suspend fun load(): PersistedRestCountdown? = null
        override suspend fun save(countdown: PersistedRestCountdown) = Unit
        override suspend fun clear() = Unit
    }

    private object FakeTrainingGuidancePreferences : TrainingGuidancePreferenceRepository {
        override fun observe() = flowOf(TrainingGuidancePreferences())
        override suspend fun get() = TrainingGuidancePreferences()
        override suspend fun setRestAlertsEnabled(enabled: Boolean) = Unit
        override suspend fun setShortRestCoachingEnabled(enabled: Boolean) = Unit
        override suspend fun setRestCountdownEnabled(enabled: Boolean) = Unit
        override suspend fun setRestCompletionSignalEnabled(enabled: Boolean) = Unit
    }

    private object FakeOnDeviceTextGenerator : OnDeviceTextGenerator {
        override fun isAvailable(): Boolean = false

        override fun generate(
            prompt: String,
            jsonSchema: String?,
            onProgress: (OnDevicePlanProgress) -> Unit
        ): String = error("Not used by graph verification")
    }

    private object FakeWorkoutPlanSourcesRepository : WorkoutPlanSourcesRepository {
        override fun observe(): Flow<WorkoutPlanSources> = emptyFlow()
    }

    private object FakePlanHistoryRepository : PlanHistoryRepository {
        override fun observeLatest(): Flow<AcceptedPlan?> = flowOf(null)

        override fun observeHistory(): Flow<List<AcceptedPlan>> = flowOf(emptyList())

        override suspend fun latest(): AcceptedPlan? = null

        override suspend fun accept(plan: AcceptedPlan) = Unit

        override suspend fun substitute(
            planId: Long,
            dayIndex: Int,
            position: Int,
            newExerciseId: String,
            newExerciseName: String,
            newWeightKg: Double?,
            newLoadCapability: ExerciseLoadCapability,
            newLoadKind: LoadKind
        ) = Unit

        override suspend fun setMeasure(
            planId: Long,
            dayIndex: Int,
            position: Int,
            reps: Int?,
            durationSeconds: Int?,
            distanceMeters: Int?,
            forceNewtons: Double?
        ) = Unit

        override suspend fun delete(planId: Long) = Unit

        override suspend fun clear() = Unit
    }

    private object FakeExerciseCatalog : ExerciseCatalog {
        override suspend fun all(): List<Exercise> = emptyList()
    }

    private object FakeWorkoutLogRepository : WorkoutLogRepository {
        override suspend fun add(set: WorkoutSet) = Unit

        override suspend fun addCluster(set: WorkoutSet, cluster: WorkoutCluster) = Unit

        override suspend fun appendClusterSegments(
            setId: Long,
            segments: List<PerformedClusterSegment>
        ) = Unit

        override suspend fun assignSession(setId: Long, sessionId: String) = Unit

        override suspend fun delete(id: Long) = Unit

        override suspend fun updateSetPerformedAt(setId: Long, performedAtMillis: Long) = Unit

        override suspend fun all(): List<WorkoutSet> = emptyList()

        override suspend fun lastSetBySession(sessionId: String): WorkoutSet? = null

        override suspend fun setsForOccurrence(occurrenceId: Long): List<WorkoutSet> = emptyList()

        override fun setsFlow(): Flow<List<WorkoutSet>> = emptyFlow()

        override suspend fun loggedSets(): List<LoggedSet> = emptyList()

        override fun loggedSetsFlow(): Flow<List<LoggedSet>> = emptyFlow()

        override suspend fun clear() = Unit
    }

    private object FakeWorkoutSessionRepository : WorkoutSessionRepository {
        override suspend fun create(session: WorkoutSession) = Unit

        override suspend fun end(id: String, endedAtMillis: Long) = Unit

        override suspend fun open(): WorkoutSession? = null

        override fun openFlow(): Flow<WorkoutSession?> = emptyFlow()

        override suspend fun all(): List<WorkoutSession> = emptyList()
    }

    private object FakeExerciseNoteRepository : ExerciseNoteRepository {
        override suspend fun note(exerciseId: String): String? = null

        override suspend fun save(exerciseId: String, text: String) = Unit

        override suspend fun clear(exerciseId: String) = Unit
    }

    private object FakeSessionNoteRepository : SessionNoteRepository {
        override suspend fun note(sessionId: String): String? = null

        override suspend fun save(sessionId: String, text: String) = Unit

        override suspend fun clear(sessionId: String) = Unit
    }

    private object FakeWarmupPreparationOverrideRepository : WarmupPreparationOverrideRepository {
        override fun observeAll(): Flow<Map<WarmupAction, WarmupPreparationItem>> =
            flowOf(emptyMap())

        override suspend fun set(item: WarmupPreparationItem) = Unit

        override suspend fun clear(action: WarmupAction) = Unit
    }

    private object FakeWorkoutHistoryRepository : WorkoutHistoryRepository {
        override fun observeEntries(request: WorkoutHistoryRequest): Flow<WorkoutHistoryPage> =
            flowOf(WorkoutHistoryPage(emptyList(), hasMore = false))

        override fun observeEntry(
            key: WorkoutHistoryKey,
            utcOffsetMillis: Long
        ): Flow<WorkoutHistoryEntry?> = flowOf(null)

        override fun observeEntrySets(
            key: WorkoutHistoryKey,
            request: WorkoutHistorySetRequest
        ): Flow<WorkoutHistorySetPage> = flowOf(WorkoutHistorySetPage(emptyList(), hasMore = false))
    }

    private object FakeSessionResegmenter : SessionResegmenter {
        override suspend fun resegmentAfterSetCorrection(
            setId: Long,
            correction: WorkoutSetCorrection,
            utcOffsetMillis: Long
        ) = Unit

        override suspend fun resegmentAfterTimeCorrection(
            setId: Long,
            performedAtMillis: Long,
            utcOffsetMillis: Long
        ) = Unit
    }

    private object FakeRoutineTemplateRepository : RoutineTemplateRepository {
        override fun observeAll(): Flow<List<RoutineTemplate>> = flowOf(emptyList())

        override suspend fun get(id: Long): RoutineTemplate? = null

        override suspend fun save(template: RoutineTemplate): Long = 0L

        override suspend fun setArchived(id: Long, archivedAtMillis: Long?) = Unit

        override suspend fun isReferencedByActivation(id: Long): Boolean = false

        override suspend fun delete(id: Long) = Unit
    }

    private object FakeWorkoutScheduleRepository : WorkoutScheduleRepository {
        override fun observeScheduleState(): Flow<WorkoutScheduleState> =
            flowOf(WorkoutScheduleState())

        override fun observeActiveActivation(): Flow<TrainingActivation?> = flowOf(null)

        override fun observeOccurrences(activationId: Long): Flow<List<WorkoutOccurrence>> =
            flowOf(emptyList())

        override suspend fun scheduleState(): WorkoutScheduleState = WorkoutScheduleState()

        override suspend fun setScheduleState(state: WorkoutScheduleState) = Unit

        override suspend fun activeActivation(): TrainingActivation? = null

        override suspend fun getActivation(id: Long): TrainingActivation? = null

        override suspend fun occurrences(activationId: Long): List<WorkoutOccurrence> = emptyList()

        override suspend fun getOccurrence(id: Long): WorkoutOccurrence? = null

        override suspend fun acceptAndActivate(
            acceptedPlan: AcceptedPlan?,
            activation: TrainingActivation,
            scheduledEpochDays: List<Long?>,
            replaceActive: Boolean
        ): Long = 0L

        override suspend fun resolveOccurrence(
            occurrenceId: Long,
            expectedRevision: Int,
            status: com.hydrafit.app.core.domain.schedule.OccurrenceStatus,
            resolvedAtMillis: Long,
            entries: List<com.hydrafit.app.core.domain.schedule.OccurrenceEntry>
        ): WorkoutScheduleState = WorkoutScheduleState()

        override suspend fun updateActivationHeader(activation: TrainingActivation) = Unit

        override suspend fun updateOccurrence(occurrence: WorkoutOccurrence) = Unit

        override suspend fun replaceOccurrenceEntries(
            occurrenceId: Long,
            entries: List<com.hydrafit.app.core.domain.schedule.OccurrenceEntry>
        ) = Unit

        override suspend fun deleteOccurrencesForActivation(activationId: Long) = Unit

        override suspend fun isTemplateReferenced(templateId: Long): Boolean = false
    }

    private object FakeSetupRepository : SetupRepository {
        private val state = MutableStateFlow(SetupState())

        override suspend fun current(): SetupState = state.value

        override fun observe(): Flow<SetupState> = state
    }

    private object FakeSetupWriteRepository : SetupWriteRepository {
        override suspend fun commit(command: SetupCommit): SetupState =
            error("Graph verification must not write setup state")
    }

    private object FakeEquipmentRepository : EquipmentRepository {
        override fun observeAll(): Flow<List<Equipment>> = flowOf(emptyList())

        override suspend fun all(): List<Equipment> = emptyList()

        override suspend fun add(name: String): Equipment = error("unused")

        override suspend fun remove(id: EquipmentTag) = error("unused")

        override suspend fun rename(
            id: EquipmentTag,
            name: String,
            maxWeightKg: Double?
        ): Equipment = error("unused")

        override suspend fun setMaxWeight(id: EquipmentTag, maxWeightKg: Double?) = error("unused")
    }

    private object FakeEquipmentSelectionRepository : EquipmentSelectionRepository {
        override suspend fun selected(): Set<EquipmentTag> = emptySet()

        override fun selectedFlow(): Flow<Set<EquipmentTag>> = flowOf(emptySet())

        override suspend fun setSelected(tags: Set<EquipmentTag>) = error("unused")
    }

    private object FakeExerciseOverrideRepository : ExerciseOverrideRepository {
        override suspend fun update(
            exerciseId: String,
            name: String?,
            requiredEquipment: Set<EquipmentTag>,
            movementPattern: MovementPattern?,
            unilateral: Boolean?,
            loadCapability: ExerciseLoadCapability?,
            involvements: Map<MuscleGroup, Double>
        ) = error("unused")

        override suspend fun reset(exerciseId: String) = error("unused")
    }

    private object FakeCustomExerciseRepository : CustomExerciseRepository {
        override suspend fun add(
            name: String,
            requiredEquipment: Set<EquipmentTag>,
            involvements: Map<MuscleGroup, Double>,
            movementPattern: MovementPattern,
            isUnilateral: Boolean,
            loadCapability: ExerciseLoadCapability
        ): Exercise = error("unused")

        override suspend fun update(
            id: String,
            name: String,
            requiredEquipment: Set<EquipmentTag>,
            involvements: Map<MuscleGroup, Double>,
            movementPattern: MovementPattern,
            isUnilateral: Boolean,
            loadCapability: ExerciseLoadCapability
        ) = error("unused")

        override suspend fun delete(id: String) = error("unused")
    }

    private object FakeTrainingGoalRepository : TrainingGoalRepository {
        override suspend fun selectedGoal(): TrainingGoal = TrainingGoal.BALANCED

        override fun goalFlow(): Flow<TrainingGoal> = flowOf(TrainingGoal.BALANCED)

        override suspend fun setGoal(goal: TrainingGoal) = error("unused")
    }

    private object FakePreferredWeekdaysRepository : PreferredWeekdaysRepository {
        override suspend fun current(): Set<DayOfWeek>? = null

        override fun observe(): Flow<Set<DayOfWeek>?> = flowOf(null)

        override suspend fun set(weekdays: Set<DayOfWeek>?) = error("unused")
    }

    private object FakePersonalRecordRepository : PersonalRecordRepository {
        override fun observe(): Flow<List<PersonalRecord>> = flowOf(emptyList())

        override suspend fun set(record: PersonalRecord) = error("unused")

        override suspend fun clear(exerciseId: String) = error("unused")
    }
}

private fun emptyBackupFile(appVersion: String, exportedAtMillis: Long): BackupFile = BackupFile(
    format = BACKUP_FORMAT,
    formatVersion = BACKUP_FORMAT_VERSION,
    appVersion = appVersion,
    exportedAtMillis = exportedAtMillis,
    catalog = BackupCatalogManifest(emptyList(), seedProfiles = emptyList()),
    settings = null,
    customExercises = emptyList(),
    exerciseOverrides = emptyList(),
    equipment = emptyList(),
    selectedEquipment = emptyList(),
    workoutSets = emptyList(),
    workoutSessions = emptyList(),
    plans = emptyList(),
    planDays = emptyList(),
    planEntries = emptyList(),
    volumeExplanations = emptyList(),
    volumeExplanationStates = emptyList(),
    routines = emptyList(),
    routineWorkouts = emptyList(),
    routineEntries = emptyList(),
    activations = emptyList(),
    activationWorkouts = emptyList(),
    activationEntries = emptyList(),
    occurrences = emptyList(),
    occurrenceEntries = emptyList(),
    scheduleState = null,
    personalRecords = emptyList(),
    preferences = emptyList(),
    exclusions = emptyList(),
    restPreferences = listOf(BackupRestPreferenceRecord(null, 120L))
)
