package com.hydrafit.app.feature.onboarding

import com.hydrafit.app.core.domain.setup.SetupDisposition
import com.hydrafit.app.core.domain.setup.SetupState
import com.hydrafit.app.core.domain.setup.SetupStep
import com.hydrafit.app.core.domain.startup.StartupReadiness
import com.hydrafit.app.core.userdata.settings.SetupRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingEntryViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun readsOnlyAfterReadinessUsingTheRestoredDisposition() = runTest(dispatcher) {
        val fixture = Fixture(ready = false)
        val viewModel = fixture.viewModel()
        runCurrent()
        assertEquals(0, fixture.reads)
        assertEquals(OnboardingEntryState.Loading, viewModel.state.value)
        assertFalse(viewModel.claimAutomaticEntry())
        // Models a staged legacy backup replacing fresh disposition before readiness is published.
        fixture.stored.value = SetupState(disposition = SetupDisposition.RETURNING)
        fixture.isReady.value = true
        runCurrent()
        assertEquals(1, fixture.reads)
        assertEquals(OnboardingEntryState.Ready(false), viewModel.state.value)
    }

    @Test
    fun onlyPendingDispositionOffersSetupAndEntryIsClaimedOnce() = runTest(dispatcher) {
        SetupDisposition.entries.forEach { disposition ->
            val fixture = Fixture()
            fixture.stored.value = SetupState(disposition = disposition, step = SetupStep.GOAL)
            val viewModel = fixture.viewModel()
            runCurrent()
            val pending = disposition == SetupDisposition.PENDING
            assertEquals(OnboardingEntryState.Ready(pending), viewModel.state.value)
            assertEquals(pending, viewModel.claimAutomaticEntry())
            assertFalse(viewModel.claimAutomaticEntry())
            assertEquals(1, fixture.reads)
        }
    }

    @Test
    fun closedPendingSetupDoesNotReopenInTheSameActivityButANewProcessCanResume() = runTest(
        dispatcher
    ) {
        val fixture = Fixture()
        fixture.stored.value = SetupState(step = SetupStep.PERSONAL_RECORDS)
        val first = fixture.viewModel()
        runCurrent()
        assertTrue(first.claimAutomaticEntry())
        assertFalse(first.claimAutomaticEntry())
        fixture.stored.value = fixture.stored.value.copy(revision = 1L)
        runCurrent()
        assertEquals(1, fixture.reads)
        val recreated = fixture.viewModel()
        runCurrent()
        assertTrue(recreated.claimAutomaticEntry())
        assertEquals(SetupStep.PERSONAL_RECORDS, fixture.stored.value.step)
    }

    @Test
    fun failureBlocksEntryAndRepeatedRetryReadsOnceWithoutChangingStoredState() = runTest(
        dispatcher
    ) {
        val fixture = Fixture()
        fixture.failure = IllegalStateException("injected")
        val initial = fixture.stored.value
        val viewModel = fixture.viewModel()
        runCurrent()
        assertEquals(OnboardingEntryState.Failed, viewModel.state.value)
        assertFalse(viewModel.claimAutomaticEntry())
        fixture.failure = null
        viewModel.retry()
        viewModel.retry()
        runCurrent()
        assertEquals(2, fixture.reads)
        assertEquals(OnboardingEntryState.Ready(true), viewModel.state.value)
        assertEquals(initial, fixture.stored.value)
    }

    private inner class Fixture(ready: Boolean = true) {
        val isReady = MutableStateFlow(ready)
        val stored = MutableStateFlow(SetupState())
        var reads = 0
        var failure: Exception? = null
        private val setup = object : SetupRepository {
            override suspend fun current(): SetupState {
                reads++
                failure?.let { throw it }
                return stored.value
            }
            override fun observe() = stored
        }
        private val readiness = object : StartupReadiness {
            override val isReady = this@Fixture.isReady
        }

        fun viewModel() = OnboardingEntryViewModel(setup, readiness, dispatcher)
    }
}
