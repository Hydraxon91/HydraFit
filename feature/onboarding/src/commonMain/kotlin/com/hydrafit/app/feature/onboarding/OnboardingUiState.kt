package com.hydrafit.app.feature.onboarding

import com.hydrafit.app.core.domain.engine.PersonalRecord
import com.hydrafit.app.core.domain.equipment.Equipment
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.setup.SetupState
import com.hydrafit.app.core.domain.setup.SetupStep
import com.hydrafit.app.core.domain.setup.SetupStepInput
import com.hydrafit.app.core.domain.unit.WeightUnit

enum class OnboardingFailure { LOAD, SAVE, INVALID_INPUT, STALE_CONTEXT }

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
    val failure: OnboardingFailure? = null
)
