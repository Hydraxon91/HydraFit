package com.hydrafit.app.feature.routines

import com.hydrafit.app.core.domain.engine.SetPrescriptionShape
import com.hydrafit.app.core.domain.engine.SlotProgressionPolicy
import com.hydrafit.app.core.domain.engine.SlotSetTargets
import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.routine.RoutineTemplate
import com.hydrafit.app.core.domain.routine.Superset
import com.hydrafit.app.core.domain.schedule.OccurrenceDateChange
import com.hydrafit.app.core.domain.schedule.ScheduleMode
import com.hydrafit.app.core.domain.schedule.TrainingActivation
import com.hydrafit.app.core.domain.schedule.WorkoutOccurrence
import com.hydrafit.app.core.domain.time.DayOfWeek
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.workout.LoadKind

/** A catalog exercise the routine editor can pick; [isExcluded] marks an EX-01 exclusion. */
data class RoutineExerciseOption(val id: String, val name: String, val isExcluded: Boolean = false)

/** Which measurement a slot is prescribed in; the two are exclusive, never both and never neither. */
enum class EntryMetric { REPETITIONS, DURATION }

/** One editable prescription slot; numbers stay as text until save so partial input is allowed. */
data class EditorEntry(
    val id: Long = 0,
    val exerciseId: String,
    val name: String,
    val sets: String,
    val reps: String,
    val weight: String,
    /** What the weight means under the exercise's capability; drives save and display. */
    val loadKind: LoadKind = LoadKind.EXTERNAL,
    /** The selected measurement; only its field is shown and only its value is saved. */
    val metric: EntryMetric = EntryMetric.REPETITIONS,
    /** Whole seconds as text, used when [metric] is [EntryMetric.DURATION]. */
    val durationSeconds: String = "",
    /** The approved progression policy for this slot, or null when the user has not set one. */
    val progressionPolicy: SlotProgressionPolicy? = null,
    /** The slot's ordered per-set targets, or null when its sets share one repeated prescription. */
    val setTargets: SlotSetTargets? = null,
    /** The pair this slot belongs to with its next slot, or null when the slot is unpaired. */
    val superset: Superset? = null
) {
    /** The prescribed rep count, or null when the slot is timed. */
    val repsValue: Int?
        get() = if (metric == EntryMetric.REPETITIONS) reps.toIntOrNull() else null

    /** The prescribed whole seconds, or null when the slot is counted. */
    val durationValue: Int?
        get() = if (metric == EntryMetric.DURATION) durationSeconds.toIntOrNull() else null
}

data class EditorWorkout(
    val id: Long = 0,
    val name: String,
    val focus: SplitFocus?,
    val entries: List<EditorEntry> = emptyList()
)

/** The full editor draft; [id] == 0 is a new routine, and [unit] is frozen at open time. */
data class RoutineEditorState(
    val id: Long,
    val revision: Int,
    val sourcePlanId: Long?,
    val name: String,
    val unit: WeightUnit,
    val workouts: List<EditorWorkout> = emptyList()
)

/** Which progression-policy field is invalid; the dialog shows the matching localized message. */
enum class ProgressionPolicyError { REPS_RANGE, INCREMENT, EQUIPMENT_ID }

/** The progression-policy dialog draft for one slot; numbers stay as text until save. */
data class ProgressionPolicyEditorState(
    val workoutIndex: Int,
    val entryIndex: Int,
    val minReps: String,
    val maxReps: String,
    val incrementKg: String,
    val equipmentId: String,
    val error: ProgressionPolicyError? = null
)

/** Which per-set-prescription input is invalid; the dialog shows the matching localized message. */
enum class SetTargetsError { SETS_RANGE, REPS_RANGE, LOAD, ROUNDING, RIR_RANGE }

/**
 * The ordered-prescription dialog draft for one slot; numbers stay as text until apply so partial
 * input is allowed. [preview] is the result the current inputs would build, or null while they are
 * not yet a valid request, so the dialog can show the targets before anything is applied.
 */
data class SetTargetsEditorState(
    val workoutIndex: Int,
    val entryIndex: Int,
    val shape: SetPrescriptionShape = SetPrescriptionShape.STRAIGHT,
    val sets: String,
    val startReps: String,
    val endReps: String,
    val startLoadKg: String,
    val endLoadKg: String,
    val roundToKg: String,
    val targetRir: String,
    val error: SetTargetsError? = null,
    val preview: SlotSetTargets? = null,
    /** The slot's current prescription, shown when the inputs do not yet build a valid one. */
    val storedTargets: SlotSetTargets? = null
)

/** Which pair input is invalid; the dialog shows the matching localized message. */
enum class SupersetError { TRANSITION_RANGE, PAIR_REST_RANGE, SAME_EXERCISE }

/**
 * The pair dialog draft for one slot and the slot after it; numbers stay as text until apply so
 * partial input is allowed. Nothing is written until the user applies.
 */
data class SupersetEditorState(
    val workoutIndex: Int,
    val entryIndex: Int,
    val transitionSeconds: String,
    val pairRestSeconds: String,
    val error: SupersetError? = null
)

/** Which workout/entry the exercise picker is filling; [replaceEntryIndex] null means "add". */
data class ExercisePickerState(
    val workoutIndex: Int,
    val replaceEntryIndex: Int? = null,
    val query: String = ""
)

data class ActivationUiState(
    val templateId: Long,
    val templateName: String,
    val workoutCount: Int,
    val mode: ScheduleMode = ScheduleMode.WEEKDAY,
    val weekdays: Set<DayOfWeek> = DefaultWeekdays,
    val startToday: Boolean = true,
    val startEpochDay: Long,
    val preview: List<Long?> = emptyList(),
    /** True when a block is already active, so the replacement choice is worth showing. */
    val hasActiveBlock: Boolean = false,
    val replaceActive: Boolean = false,
    val error: String? = null
)

val DefaultWeekdays: Set<DayOfWeek> = setOf(
    DayOfWeek.MONDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.FRIDAY
)

/** A postponement awaiting confirmation, with the dates the pending suffix would move to. */
data class PendingPostpone(
    val occurrenceId: Long,
    val newEpochDay: Long,
    val changes: List<OccurrenceDateChange>
)

/** A schedule-mode change awaiting confirmation, with the dates pending workouts would take. */
data class PendingSwitchMode(
    val mode: ScheduleMode,
    val weekdays: Set<DayOfWeek>,
    val startEpochDay: Long,
    val changes: List<OccurrenceDateChange>
)

data class RoutinesUiState(
    val templates: List<RoutineTemplate> = emptyList(),
    val isLoading: Boolean = true,
    val exercises: List<RoutineExerciseOption> = emptyList(),
    val editor: RoutineEditorState? = null,
    val picker: ExercisePickerState? = null,
    val progressionPolicyEditor: ProgressionPolicyEditorState? = null,
    val setTargetsEditor: SetTargetsEditorState? = null,
    val supersetEditor: SupersetEditorState? = null,
    /** Active EX-01 exclusions, so routine entries can be marked in place. */
    val excludedExerciseIds: Set<String> = emptySet(),
    val activation: ActivationUiState? = null,
    val activeActivation: TrainingActivation? = null,
    val occurrences: List<WorkoutOccurrence> = emptyList(),
    val pendingPostpone: PendingPostpone? = null,
    val pendingSwitchMode: PendingSwitchMode? = null,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val message: String? = null
)
