package com.hydrafit.app.feature.onboarding

import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.setup.SaveSetupStepUseCase
import com.hydrafit.app.core.domain.setup.SetupWriteRepository
import com.hydrafit.app.core.domain.setup.SkipSetupUseCase
import com.hydrafit.app.core.domain.startup.StartupReadiness
import com.hydrafit.app.core.userdata.equipment.EquipmentRepository
import com.hydrafit.app.core.userdata.equipment.EquipmentSelectionRepository
import com.hydrafit.app.core.userdata.equipment.PersonalRecordRepository
import com.hydrafit.app.core.userdata.settings.PreferredWeekdaysRepository
import com.hydrafit.app.core.userdata.settings.SetupRepository
import com.hydrafit.app.core.userdata.settings.TrainingGoalRepository
import com.hydrafit.app.core.userdata.settings.WeightUnitRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.verify.verify

@OptIn(KoinExperimentalAPI::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class OnboardingModuleVerificationTest {
    @Test
    fun exportedModuleVerifiesAgainstItsExternalPorts() {
        onboardingModule.verify(
            extraTypes = listOf(
                SetupRepository::class,
                SetupWriteRepository::class,
                WeightUnitRepository::class,
                EquipmentRepository::class,
                EquipmentSelectionRepository::class,
                TrainingGoalRepository::class,
                PreferredWeekdaysRepository::class,
                PersonalRecordRepository::class,
                ExerciseCatalog::class,
                StartupReadiness::class,
                CoroutineDispatcher::class
            )
        )
    }

    @Test
    fun lambdaBindingsActuallyResolveWithoutStartingOrWritingSetup() {
        val fixture = OnboardingFixture()
        val application = koinApplication {
            modules(
                module {
                    single<SetupRepository> { fixture.setup }
                    single<SetupWriteRepository> { fixture.writer }
                    single<WeightUnitRepository> { fixture.units }
                    single<EquipmentRepository> { fixture.equipment }
                    single<EquipmentSelectionRepository> { fixture.selection }
                    single<TrainingGoalRepository> { fixture.goals }
                    single<PreferredWeekdaysRepository> { fixture.weekdays }
                    single<PersonalRecordRepository> { fixture.records }
                    single<ExerciseCatalog> { fixture.catalog }
                    single<StartupReadiness> { fixture.readiness }
                },
                onboardingModule
            )
        }
        try {
            assertNotNull(application.koin.get<OnboardingViewModel>())
            assertNotNull(application.koin.get<SaveSetupStepUseCase>())
            assertNotNull(application.koin.get<SkipSetupUseCase>())
            assertEquals(0, fixture.attempts.size)
        } finally {
            application.close()
        }
    }

    /**
     * The entry ViewModel is a lambda `viewModel { }` definition, so resolve it over
     * `onboardingModule` to prove its constructor `get()` chain. Constructing it starts its
     * disposition read, which needs a Main dispatcher harness, and that read must not write.
     *
     * The read's published state is asserted in `OnboardingEntryViewModelTest`, which injects the
     * dispatcher; this binding leaves it at the default, which the test scheduler cannot await.
     */
    @Test
    fun theEntryViewModelResolvesWithoutWriting() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val fixture = OnboardingFixture()
        val application = koinApplication {
            modules(
                module {
                    single<SetupRepository> { fixture.setup }
                    single<SetupWriteRepository> { fixture.writer }
                    single<WeightUnitRepository> { fixture.units }
                    single<EquipmentRepository> { fixture.equipment }
                    single<EquipmentSelectionRepository> { fixture.selection }
                    single<TrainingGoalRepository> { fixture.goals }
                    single<PreferredWeekdaysRepository> { fixture.weekdays }
                    single<PersonalRecordRepository> { fixture.records }
                    single<ExerciseCatalog> { fixture.catalog }
                    single<StartupReadiness> { fixture.readiness }
                },
                onboardingModule
            )
        }
        try {
            assertNotNull(application.koin.get<OnboardingEntryViewModel>())
            advanceUntilIdle()
            assertEquals(0, fixture.attempts.size)
        } finally {
            application.close()
            Dispatchers.resetMain()
        }
    }
}
