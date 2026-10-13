package com.hydrafit.app.feature.logger

import androidx.compose.ui.Modifier
import com.hydrafit.app.core.domain.workout.WarmupAction

/**
 * Stable external identities for the Logger's interactive controls. Android exposes each tag as a
 * resource id ([loggerTestTagsAsResourceId]) so a UI-automation tool can target a control by identity
 * instead of its current position, its displayed text or a translated label. Tags are technical
 * metadata: they never carry user-facing copy and are never announced as accessibility descriptions.
 */
internal object LoggerTestTags {
    /** The single Logger scroll container; a gesture must target it rather than a nested picker. */
    const val LIST = "logger_list"

    /** The fixed-height exercise picker nested inside [LIST]. */
    const val EXERCISE_PICKER = "logger_exercise_picker"

    const val MANUAL_INSTRUCTIONS = "logger_manual_instructions"

    fun draftInstructions(draft: DraftSet): String =
        "logger_draft_instructions_${draft.occurrenceEntryId ?: draft.exerciseId}"

    fun guidedInstructions(entryId: Long): String = "logger_guided_instructions_$entryId"

    /** The optional warm-up guidance card. */
    const val WARMUP_CARD = "logger_warmup_card"

    /** The transient warm-up editor dialog and its inputs/actions. */
    const val WARMUP_EDITOR = "logger_warmup_editor"
    const val WARMUP_EDITOR_DURATION = "logger_warmup_editor_duration"
    const val WARMUP_EDITOR_REPS = "logger_warmup_editor_reps"
    const val WARMUP_EDITOR_WEIGHT = "logger_warmup_editor_weight"
    const val WARMUP_EDITOR_SAVE = "logger_warmup_editor_save"
    const val WARMUP_EDITOR_CANCEL = "logger_warmup_editor_cancel"

    /** One exercise choice in the picker, keyed by the exercise's own stable id. */
    fun exercise(exerciseId: String): String = "logger_exercise_$exerciseId"

    /**
     * The labeled toggle for one preparation action. The identity is independent of the toggle's
     * checked state and of the value shown, so an edit or a toggle never changes it.
     */
    fun preparationToggle(action: WarmupAction): String = "logger_warmup_prep_${action.name}"

    fun preparationEdit(action: WarmupAction): String = "logger_warmup_prep_edit_${action.name}"

    fun preparationSkip(action: WarmupAction): String = "logger_warmup_prep_skip_${action.name}"

    /** One ramp row, keyed by its freezing occurrence entry and its index within that entry. */
    fun rampRow(occurrenceEntryId: Long, rampIndex: Int): String =
        "logger_warmup_ramp_${occurrenceEntryId}_$rampIndex"

    fun rampEdit(occurrenceEntryId: Long, rampIndex: Int): String =
        "logger_warmup_ramp_edit_${occurrenceEntryId}_$rampIndex"

    fun rampLog(occurrenceEntryId: Long, rampIndex: Int): String =
        "logger_warmup_ramp_log_${occurrenceEntryId}_$rampIndex"
}

/** Enables [LoggerTestTags] as Android resource ids for the wrapped subtree; a no-op elsewhere. */
internal expect fun Modifier.loggerTestTagsAsResourceId(): Modifier
