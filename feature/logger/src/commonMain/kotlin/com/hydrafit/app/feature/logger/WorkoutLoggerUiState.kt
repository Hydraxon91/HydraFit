package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.matchesExerciseNameQuery
import com.hydrafit.app.core.domain.time.localEpochDay
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.workout.GuidedWorkoutProgress
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.RestCountdown
import com.hydrafit.app.core.domain.workout.WorkoutSession

data class ExerciseOption(
    val id: String,
    val name: String,
    val isBodyweight: Boolean,
    val isUnilateral: Boolean = false,
    val loadCapability: ExerciseLoadCapability = ExerciseLoadCapability.EXTERNAL
) {
    /** Only an addable bodyweight movement may reveal a field for added kilograms. */
    val canAddLoad: Boolean get() = loadCapability == ExerciseLoadCapability.BODYWEIGHT_ADDABLE
}

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
    val dayIndex: Int? = null,
    /** What [weightKg] means, so the row can label added or unconfirmed load honestly. */
    val loadKind: LoadKind = LoadKind.LEGACY_UNSPECIFIED,
    val occurrenceId: Long? = null
)

/** A correction of an existing row; unit is frozen for the lifetime of this editor. */
data class LoggedSetEdit(
    val row: LoggedSetRow,
    val weightUnit: WeightUnit,
    val reps: String,
    val weightInput: String,
    val originalWeightInput: String,
    val rir: String,
    val performedAtMillis: Long
) {
    val canSave: Boolean get() = (reps.toIntOrNull() ?: 0) > 0 &&
        (rir.isBlank() || rir.toIntOrNull()?.let { it in 0..10 } == true) &&
        (
            weightInput.isBlank() ||
                weightInput.toDoubleOrNull()?.let { it.isFinite() && it >= 0 } == true
            )
}

/**
 * How a recorded weight reads, given its load kind. Keeps a legacy number from being shown as
 * confirmed external load and labels added kilograms as added, not total resistance.
 */
sealed interface LoadDisplay {
    /** No number recorded for this kind. */
    data object None : LoadDisplay

    /** External resistance in kilograms. */
    data class External(val weightKg: Double) : LoadDisplay

    /** Kilograms added to bodyweight; null when the amount was not recorded. */
    data class Added(val weightKg: Double?) : LoadDisplay

    /** Bodyweight / no added load. */
    data object Bodyweight : LoadDisplay

    /** A recorded historical number whose meaning was never established. */
    data class LegacyUnconfirmed(val weightKg: Double?) : LoadDisplay
}

/** Maps a stored load kind and weight to its display form. Pure so it can be unit-tested. */
fun loadDisplayFor(loadKind: LoadKind, weightKg: Double?): LoadDisplay = when (loadKind) {
    LoadKind.EXTERNAL -> weightKg?.let { LoadDisplay.External(it) } ?: LoadDisplay.None
    LoadKind.ADDED -> LoadDisplay.Added(weightKg)
    LoadKind.BODYWEIGHT -> LoadDisplay.Bodyweight
    LoadKind.LEGACY_UNSPECIFIED ->
        weightKg?.let { LoadDisplay.LegacyUnconfirmed(it) } ?: LoadDisplay.None
}

/** A planned exercise offered in the Logger; nothing here counts until it is confirmed. */
data class DraftSet(
    val exerciseId: String,
    val name: String,
    val sets: Int,
    val reps: Int,
    val weightKg: Double?,
    val loadKind: LoadKind = LoadKind.EXTERNAL,
    /** Load semantics frozen with the prescription; the live catalog may have changed since. */
    val loadCapability: ExerciseLoadCapability = ExerciseLoadCapability.EXTERNAL,
    /** Frozen occurrence slot identity, when this draft came from an active workout. */
    val occurrenceId: Long? = null,
    val occurrenceEntryId: Long? = null
)

/** One-off values for the draft currently being edited; never written back to its prescription. */
data class DraftEdit(
    val draft: DraftSet,
    val reps: String,
    val weightInput: String,
    val rir: String,
    val weightRevealed: Boolean,
    val performedAtMillis: Long?,
    val performedAtExplicit: Boolean = false
)

/**
 * A draft submission that stopped partway through. [source] is the pending row that was being
 * written, [draft] holds the not-yet-recorded sets with the same confirmed one-off values, and
 * [edit] preserves the editor's RIR and performed-time choice for the retry. In-memory only: it is
 * never written back to a prescription and is dropped on success, dismissal or a context change.
 */
data class DraftWriteRetry(
    val source: DraftSet,
    val draft: DraftSet,
    val edit: DraftEdit?,
    val savedSets: Int = (source.sets - draft.sets).coerceAtLeast(0)
) {
    /** True when at least one set of [source] was recorded before any retry failure. */
    val anyRecorded: Boolean get() = savedSets > 0
}

/** One draft whose stored prescription has no established load meaning and needs a decision. */
data class LegacyResolutionItem(val draft: DraftSet, val capability: ExerciseLoadCapability) {
    /**
     * True when explicitly recording the stored number as external load is allowed. An explicit
     * frozen capability takes precedence; only a legacy draft with frozen UNSPECIFIED capability
     * consults the current catalog. The decision never changes the frozen prescription.
     */
    val canBeExternal: Boolean get() = capability == ExerciseLoadCapability.EXTERNAL
}

/** The queue of legacy drafts the user must resolve before they can be logged. */
data class LegacyResolution(val items: List<LegacyResolutionItem>) {
    val current: LegacyResolutionItem get() = items.first()
}

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
    val loggedSetEdit: LoggedSetEdit? = null,
    val savingLoggedSet: Boolean = false,
    val loggedSetEditFailed: Boolean = false,
    val draftSets: List<DraftSet> = emptyList(),
    val draftEdit: DraftEdit? = null,
    val missingLoadPrompt: DraftSet? = null,
    val confirmingAllDrafts: Boolean = false,
    val draftWriteInProgress: Boolean = false,
    /** Independent partially written drafts awaiting retry in the current context. */
    val draftWriteRetries: List<DraftWriteRetry> = emptyList(),
    /** The current workout of the active block, or null when no block is active. */
    val activeOccurrence: ActiveOccurrence? = null,
    /** A finish/skip error to surface (e.g. the workout changed elsewhere). */
    val occurrenceMessage: String? = null,
    /** Whether the guided-workout preference is on. Off leaves the Logger's existing flow unchanged. */
    val guidedEnabled: Boolean = false,
    /** The active occurrence's guided progress, or null when guided mode is off or no workout is active. */
    val guidedProgress: GuidedWorkoutProgress? = null,
    /** True when the last guided single-set write failed; the pending set is still available to retry. */
    val guidedSetWriteFailed: Boolean = false,
    /** In-memory rest prompt started only by an explicitly live guided completion. */
    val restTimer: RestTimerState? = null,
    /** Editable duration in seconds; retained only for this Logger ViewModel lifetime. */
    val restDurationSeconds: String = "120",
    /** A pending explicit load decision for legacy drafts, or null when none is waiting. */
    val legacyResolution: LegacyResolution? = null,
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

    /** True when guided mode is on and an active occurrence is available to guide. */
    val isGuidedActive: Boolean
        get() = guidedEnabled && guidedProgress != null

    val canStartRestTimer: Boolean
        get() = restDurationSeconds.toLongOrNull()?.let {
            it > 0L && it <= RestCountdown.MAX_DURATION_MILLIS / 1_000L
        } == true

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
        get() {
            val option = exercises.firstOrNull { it.id == selectedExerciseId }
            return when (option?.loadCapability) {
                ExerciseLoadCapability.BODYWEIGHT_ONLY -> false
                ExerciseLoadCapability.BODYWEIGHT_ADDABLE -> weightRevealed
                else -> true
            }
        }

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
