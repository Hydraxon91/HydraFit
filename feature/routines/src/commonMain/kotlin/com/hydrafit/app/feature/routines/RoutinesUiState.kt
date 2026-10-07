package com.hydrafit.app.feature.routines

import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.routine.RoutineTemplate
import com.hydrafit.app.core.domain.schedule.ScheduleMode
import com.hydrafit.app.core.domain.schedule.TrainingActivation
import com.hydrafit.app.core.domain.schedule.WorkoutOccurrence
import com.hydrafit.app.core.domain.time.DayOfWeek
import com.hydrafit.app.core.domain.unit.WeightUnit

/** A catalog exercise the routine editor can pick. */
data class RoutineExerciseOption(val id: String, val name: String)

/** One editable prescription slot; numbers stay as text until save so partial input is allowed. */
data class EditorEntry(
    val id: Long = 0,
    val exerciseId: String,
    val name: String,
    val sets: String,
    val reps: String,
    val weight: String
)

data class EditorWorkout(
    val id: Long = 0,
    val name: String,
    val focus: SplitFocus?,
    val entries: List<EditorEntry> = emptyList()
)

/** The full editor draft; [id] == 0 is a new routine. */
data class RoutineEditorState(
    val id: Long,
    val revision: Int,
    val sourcePlanId: Long?,
    val name: String,
    val workouts: List<EditorWorkout> = emptyList()
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

data class RoutinesUiState(
    val templates: List<RoutineTemplate> = emptyList(),
    val isLoading: Boolean = true,
    val exercises: List<RoutineExerciseOption> = emptyList(),
    val editor: RoutineEditorState? = null,
    val picker: ExercisePickerState? = null,
    val activation: ActivationUiState? = null,
    val activeActivation: TrainingActivation? = null,
    val occurrences: List<WorkoutOccurrence> = emptyList(),
    val weightUnit: WeightUnit = WeightUnit.KG,
    val message: String? = null
)
