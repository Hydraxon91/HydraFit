package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.equipment.matchesExerciseNameQuery
import com.hydrafit.app.core.domain.time.localEpochDay
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.workout.WorkoutSession

data class ExerciseOption(
    val id: String,
    val name: String,
    val isBodyweight: Boolean,
    val isUnilateral: Boolean = false
)

data class LoggedSetRow(
    val id: Long,
    val performedAtMillis: Long,
    val exerciseId: String,
    val exerciseName: String,
    val reps: Int,
    val weightKg: Double?,
    val rir: Int? = null,
    val isWarmup: Boolean,
    val weekNumber: Int? = null,
    val dayIndex: Int? = null
)

/** A planned exercise offered in the Logger; nothing here counts until it is confirmed. */
data class DraftSet(
    val exerciseId: String,
    val name: String,
    val sets: Int,
    val reps: Int,
    val weightKg: Double?
)

/** The active block's current workout occurrence, shown so the user can finish or skip it. */
data class ActiveOccurrence(
    val occurrenceId: Long,
    val workoutName: String,
    val performedSets: Int,
    val prescribedSets: Int
)

data class WorkoutLoggerUiState(
    val exercises: List<ExerciseOption> = emptyList(),
    val exerciseSearch: String = "",
    val selectedExerciseId: String? = null,
    val reps: String = "",
    val weightInput: String = "",
    val rir: String = "",
    val isWarmup: Boolean = false,
    val recentSets: List<LoggedSetRow> = emptyList(),
    val draftSets: List<DraftSet> = emptyList(),
    /** The current workout of the active block, or null when no block is active. */
    val activeOccurrence: ActiveOccurrence? = null,
    /** A finish/skip error to surface (e.g. the workout changed elsewhere). */
    val occurrenceMessage: String? = null,
    val todayFocus: SplitFocus? = null,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val weightRevealed: Boolean = false,
    /** The persisted open session, or null when none is open; drives the End/New controls. */
    val activeSession: WorkoutSession? = null,
    /** The explicit backdated time to stamp new sets with, or null to use the current time. */
    val performedAtMillis: Long? = null,
    /** When true, a backdated set always starts a fresh session instead of attaching to the open one. */
    val forceNewSession: Boolean = false,
    /** The local UTC offset, used to display and seed the backdated time picker. */
    val utcOffsetMillis: Long = 0L
) {
    /** True when a backdated time is set; the UI shows a "backdated" indicator. */
    val isBackdated: Boolean
        get() = performedAtMillis != null

    /**
     * The open session a backdated set would attach to, or null when logging will start a new one.
     * Mirrors the domain attach rule: same local day and not before that session began.
     */
    val backdatedTargetSession: WorkoutSession?
        get() {
            val at = performedAtMillis ?: return null
            if (forceNewSession) return null
            val open = activeSession ?: return null
            if (localEpochDay(at, utcOffsetMillis) != open.localEpochDay) return null
            if (at < open.startedAtMillis) return null
            return open
        }

    val canLog: Boolean
        get() = selectedExerciseId != null && (reps.toIntOrNull() ?: 0) > 0

    /**
     * Whether the weight field is shown: always for weighted exercises, and for bodyweight
     * exercises only once the user reveals it to log a weighted variant.
     */
    val showWeightField: Boolean
        get() = weightRevealed ||
            exercises.firstOrNull { it.id == selectedExerciseId }?.isBodyweight != true

    /** True when the selected exercise is one-side-at-a-time, so the entered weight is per hand. */
    val selectedExerciseIsUnilateral: Boolean
        get() = exercises.firstOrNull { it.id == selectedExerciseId }?.isUnilateral == true

    /** Exercises matching the picker search, or all of them when the search is blank. */
    val visibleExercises: List<ExerciseOption>
        get() = if (exerciseSearch.isBlank()) {
            exercises
        } else {
            exercises.filter { matchesExerciseNameQuery(it.name, exerciseSearch) }
        }
}
