package com.hydrafit.app.feature.onboarding

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.setup.SaveSetupStepUseCase
import com.hydrafit.app.core.domain.setup.SetupWriteRepository
import com.hydrafit.app.core.domain.setup.SkipSetupUseCase
import com.hydrafit.app.core.domain.startup.StartupReadiness
import com.hydrafit.app.core.userdata.equipment.EquipmentProfileRepository
import com.hydrafit.app.core.userdata.equipment.EquipmentRepository
import com.hydrafit.app.core.userdata.equipment.PersonalRecordRepository
import com.hydrafit.app.core.userdata.settings.PreferredWeekdaysRepository
import com.hydrafit.app.core.userdata.settings.SetupRepository
import com.hydrafit.app.core.userdata.settings.TrainingGoalRepository
import com.hydrafit.app.core.userdata.settings.WeightUnitRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
                EquipmentProfileRepository::class,
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
                    single<EquipmentProfileRepository> { fixture.selection }
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
     * This binding keeps the real default dispatcher. Hold its read open to prove that draining the
     * test scheduler is not completion, then await publication and clear/join the owned ViewModel
     * before resetting Main. The cancellation case checks teardown while the read is suspended.
     */
    @Test
    fun theEntryViewModelResolvesWithoutWriting() = verifyEntryBinding(finishRead = true)

    @Test
    fun theEntryViewModelCleanupWaitsForASuspendedReadToCancel() =
        verifyEntryBinding(finishRead = false)

    private fun verifyEntryBinding(finishRead: Boolean) = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        val fixture = OnboardingFixture()
        val readStarted = CompletableDeferred<Unit>()
        val releaseRead = CompletableDeferred<Unit>()
        val readFinished = CompletableDeferred<Unit>()
        val setup = object : SetupRepository {
            override suspend fun current() = try {
                readStarted.complete(Unit)
                releaseRead.await()
                fixture.setup.current()
            } finally {
                readFinished.complete(Unit)
            }
            override fun observe() = fixture.setup.observe()
        }
        val store = ViewModelStore()
        var entryJob: Job? = null
        val application = koinApplication {
            modules(
                module {
                    single<SetupRepository> { setup }
                    single<SetupWriteRepository> { fixture.writer }
                    single<WeightUnitRepository> { fixture.units }
                    single<EquipmentRepository> { fixture.equipment }
                    single<EquipmentProfileRepository> { fixture.selection }
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
            val viewModel = application.koin.get<OnboardingEntryViewModel>()
            store.put("entry", viewModel)
            entryJob = assertNotNull(viewModel.viewModelScope.coroutineContext[Job])
            runCurrent()
            withContext(Dispatchers.Default) {
                withTimeout(5_000) { readStarted.await() }
            }
            assertEquals(OnboardingEntryState.Loading, viewModel.state.value)
            if (finishRead) {
                releaseRead.complete(Unit)
                val published = withContext(Dispatchers.Default) {
                    withTimeout(5_000) {
                        viewModel.state.first { it != OnboardingEntryState.Loading }
                    }
                }
                assertEquals(OnboardingEntryState.Ready(true), published)
            }
            assertEquals(0, fixture.attempts.size)
        } finally {
            try {
                store.clear()
                withContext(NonCancellable + Dispatchers.Default) {
                    withTimeout(5_000) { entryJob?.join() }
                }
            } finally {
                application.close()
                Dispatchers.resetMain()
            }
        }
        assertTrue(readFinished.isCompleted)
        assertTrue(assertNotNull(entryJob).isCompleted)
        assertEquals(0, fixture.attempts.size)
    }
}
