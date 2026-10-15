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

/**
 * The one personal-record entry the user has open, kept as raw text so Next folds exactly what is on
 * screen. It is not a draft edit: only Save or a valid Next turns its text into a record, so an
 * unparseable or dismissed entry records nothing for that exercise.
 *
 * [origin] is the record the entry was pre-filled from — the value the row already showed when it
 * opened. Keeping it lets an entry whose text still matches what was pre-filled submit nothing, and
 * lets a single changed field keep the other field's exact value instead of its rounded display text.
 */
data class PersonalRecordEntryDraft(
    val exerciseId: String,
    val weightText: String = "",
    val repsText: String = "",
    val origin: PersonalRecord? = null
)

data class OnboardingUiState(
    val committed: SetupState? = null,
    val step: SetupStep = SetupStep.WELCOME,
    val input: SetupStepInput? = null,
    val entryUnit: WeightUnit = WeightUnit.KG,
    val equipment: List<Equipment> = emptyList(),
    val equipmentProfileName: String? = null,
    val eligibleExercises: List<Exercise> = emptyList(),
    val existingRecords: List<PersonalRecord> = emptyList(),
    /** The PR entry the user has open, if any; occupied only on the Personal records step. */
    val personalRecordEntry: PersonalRecordEntryDraft? = null,
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
