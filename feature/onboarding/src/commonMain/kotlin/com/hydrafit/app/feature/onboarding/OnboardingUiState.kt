package com.hydrafit.app.feature.onboarding

import com.hydrafit.app.core.domain.engine.PersonalRecord
import com.hydrafit.app.core.domain.equipment.Equipment
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.setup.SetupState
import com.hydrafit.app.core.domain.setup.SetupStep
import com.hydrafit.app.core.domain.setup.SetupStepInput
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.navigation.AppTab

enum class OnboardingFailure { LOAD, SAVE, INVALID_INPUT, STALE_CONTEXT }

/** The one render decision the entry screen makes; nothing else derives "what shows now". */
enum class OnboardingEntryPhase { LOADING, FAILURE, READY }

data class OnboardingUiState(
    val committed: SetupState? = null,
    val step: SetupStep = SetupStep.WELCOME,
    val input: SetupStepInput? = null,
    val entryUnit: WeightUnit = WeightUnit.KG,
    val equipment: List<Equipment> = emptyList(),
    val eligibleExercises: List<Exercise> = emptyList(),
    val existingRecords: List<PersonalRecord> = emptyList(),
    val loading: Boolean = false,
    val saving: Boolean = false,
    val finished: Boolean = false,
    val failure: OnboardingFailure? = null,
    /**
     * The first-workout tab to open once the final step's completion write succeeds. It stays set
     * only through a successful completion, so navigation follows the saved setup rather than a tap;
     * a failed write clears it and leaves the retry on the final step.
     */
    val pendingHandoff: AppTab? = null
) {
    /**
     * Loading covers an unresolved journey as well as an in-flight read. A failure outranks the
     * missing boundary, so a first read that fails shows its error and retry instead of a spinner,
     * and the screen never treats "no committed data yet" as ready.
     */
    val entryPhase: OnboardingEntryPhase
        get() = when {
            loading -> OnboardingEntryPhase.LOADING
            failure != null -> OnboardingEntryPhase.FAILURE
            committed == null -> OnboardingEntryPhase.LOADING
            else -> OnboardingEntryPhase.READY
        }
}
