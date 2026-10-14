package com.hydrafit.app.feature.onboarding

import com.hydrafit.app.core.domain.setup.SetupState
import kotlin.test.Test
import kotlin.test.assertEquals

class OnboardingUiStateTest {

    @Test
    fun anUninitializedJourneyIsLoading() {
        assertEquals(OnboardingEntryPhase.LOADING, OnboardingUiState().entryPhase)
    }

    @Test
    fun anInFlightReadIsLoadingEvenWithCommittedData() {
        val state = OnboardingUiState(committed = SetupState(), loading = true)

        assertEquals(OnboardingEntryPhase.LOADING, state.entryPhase)
    }

    @Test
    fun anInitialReadFailureIsAFailureNotAnEndlessLoad() {
        val state = OnboardingUiState(failure = OnboardingFailure.LOAD)

        assertEquals(OnboardingEntryPhase.FAILURE, state.entryPhase)
    }

    @Test
    fun aSaveFailureWithCommittedDataIsAFailure() {
        val state = OnboardingUiState(committed = SetupState(), failure = OnboardingFailure.SAVE)

        assertEquals(OnboardingEntryPhase.FAILURE, state.entryPhase)
    }

    @Test
    fun aCommittedJourneyIsReady() {
        val state = OnboardingUiState(committed = SetupState())

        assertEquals(OnboardingEntryPhase.READY, state.entryPhase)
    }
}
