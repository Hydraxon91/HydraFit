package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.engine.ExerciseTargetExplanation
import com.hydrafit.app.core.domain.engine.SetTarget
import com.hydrafit.app.core.domain.engine.SlotSetTargets
import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.matchesExerciseNameQuery
import com.hydrafit.app.core.domain.time.localEpochDay
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.workout.GuidedWorkoutProgress
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.RestCountdown
import com.hydrafit.app.core.domain.workout.WarmupAction
import com.hydrafit.app.core.domain.workout.WarmupGuidance
import com.hydrafit.app.core.domain.workout.WarmupPreparationItem
import com.hydrafit.app.core.domain.workout.WarmupRampSet
import com.hydrafit.app.core.domain.workout.WorkoutHistoryKey
import com.hydrafit.app.core.domain.workout.WorkoutSession
import com.hydrafit.app.core.domain.workout.WorkoutSummary
import com.hydrafit.app.core.exercisecontent.ExerciseCautions

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

data class LoggerExerciseInstructions(val exerciseId: String, val exerciseName: String)

/**
 * True when an instruction action should also indicate bundled technique considerations.
 *
 * Both the action and its indication use the same exercise id, so a planned draft or guided entry
 * must pass its exercise id and never its occurrence-slot id. A `user-*` custom identity, a blank id
 * and a display name never match, so a custom exercise cannot inherit seeded content.
 */
internal fun showsTechniqueConsiderations(exerciseId: String?): Boolean =
    ExerciseCautions.hasTechniqueConsiderations(exerciseId)

data class LoggedSetRow(
    val id: Long,
    val performedAtMillis: Long,
    val exerciseId: String,
    val exerciseName: String,
    /** Repetitions for counted work, or null for a timed set. Never a `0` placeholder. */
    val reps: Int?,
    val weightKg: Double?,
    val rir: Int? = null,
    val isWarmup: Boolean,
    val weekNumber: Int? = null,
    val dayIndex: Int? = null,
    /** What [weightKg] means, so the row can label added or unconfirmed load honestly. */
    val loadKind: LoadKind = LoadKind.LEGACY_UNSPECIFIED,
    val occurrenceId: Long? = null,
    val sessionId: String? = null,
    /** Elapsed whole seconds for a timed set, or null for counted work. */
    val durationSeconds: Int? = null
)

/** Null identity is unrecorded, never a shared session boundary. */
internal fun LoggedSetRow.startsSessionAfter(previous: LoggedSetRow?): Boolean =
    sessionId != null && (previous == null || sessionId != previous.sessionId)

/**
 * Which single measurement a form records: repetitions or elapsed whole seconds. The two entry
 * fields are mutually exclusive because the chosen metric alone is submitted, so a form can never
 * write both.
 */
enum class SetMetric { REPETITIONS, DURATION }

/** A correction of an existing row; unit is frozen for the lifetime of this editor. */
data class LoggedSetEdit(
    val row: LoggedSetRow,
    val weightUnit: WeightUnit,
    /** The metric being corrected; the other field is retained but never submitted. */
    val metric: SetMetric,
    val reps: String,
    val durationSeconds: String,
    val weightInput: String,
    val originalWeightInput: String,
    val rir: String,
    val performedAtMillis: Long
) {
    val canSave: Boolean get() = enteredMetricIsPositive &&
        (rir.isBlank() || rir.toIntOrNull()?.let { it in 0..10 } == true) &&
        (
            weightInput.isBlank() ||
                weightInput.toDoubleOrNull()?.let { it.isFinite() && it >= 0 } == true
            )

    /** True when the selected metric holds a positive whole number. */
    val enteredMetricIsPositive: Boolean
        get() = when (metric) {
            SetMetric.REPETITIONS -> (reps.toIntOrNull() ?: 0) > 0
            SetMetric.DURATION -> (durationSeconds.toIntOrNull() ?: 0) > 0
        }

    /** Repetitions to write, or null when this correction records seconds instead. */
    val correctionReps: Int?
        get() = if (metric == SetMetric.REPETITIONS) reps.toIntOrNull() else null

    /** Whole seconds to write, or null when this correction records repetitions instead. */
    val correctionDurationSeconds: Int?
        get() = if (metric == SetMetric.DURATION) durationSeconds.toIntOrNull() else null
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
    /** Repetitions for counted work, or null for a timed slot. Never a `0` sentinel. */
    val reps: Int? = null,
    val weightKg: Double?,
    val loadKind: LoadKind = LoadKind.EXTERNAL,
    /** Load semantics frozen with the prescription; the live catalog may have changed since. */
    val loadCapability: ExerciseLoadCapability = ExerciseLoadCapability.EXTERNAL,
    /** Frozen occurrence slot identity, when this draft came from an active workout. */
    val occurrenceId: Long? = null,
    val occurrenceEntryId: Long? = null,
    /**
     * The frozen slot's recorded reasoning, or null when none was recorded, the slot was replaced or
     * edited, or the draft came from a manual routine. Shown, never written back to a prescription.
     */
    val explanation: ExerciseTargetExplanation? = null,
    /**
     * Prescriptions default to working sets; guided confirmation freezes the user's Warm-up choice.
     */
    val isWarmup: Boolean = false,
    /** Elapsed whole seconds for a timed slot, or null for counted work. */
    val durationSeconds: Int? = null,
    /**
     * The slot's frozen ordered per-set targets, or null when its sets share one repeated
     * prescription. A guided or planned draft keeps them so each logged set records the target it
     * actually followed, rather than the slot's single repeated values.
     */
    val setTargets: SlotSetTargets? = null,
    /**
     * The ordered target this draft's scalar reps/load were seeded from, or null when the slot has no
     * ordered targets. Comparing the draft against it is how an explicit one-off edit is told apart
     * from an untouched draft, so a slot's per-set targets are never discarded as a false override.
     */
    val orderedPrefill: SetTarget? = null
)

/**
 * One-off values for the draft currently being edited; never written back to its prescription.
 * The draft's own measure is frozen for the lifetime of this editor: a timed draft edits whole
 * seconds and a counted draft edits repetitions, and only the frozen one is ever submitted.
 */
data class DraftEdit(
    val draft: DraftSet,
    val reps: String,
    val durationSeconds: String = "",
    val weightInput: String,
    val rir: String,
    val weightRevealed: Boolean,
    val performedAtMillis: Long?,
    val performedAtExplicit: Boolean = false
) {
    /** True when the frozen draft records elapsed seconds instead of repetitions. */
    val isTimed: Boolean get() = draft.durationSeconds != null

    /** True when the frozen measure's field holds a positive whole number. */
    val enteredMetricIsPositive: Boolean
        get() = if (isTimed) {
            (durationSeconds.toIntOrNull() ?: 0) > 0
        } else {
            (reps.toIntOrNull() ?: 0) > 0
        }
}

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

/**
 * What the post-finish summary route shows. The route is open only after a successful Finish
 * workout or Finish partially; `null` in [WorkoutLoggerUiState.summary] means it is closed.
 */
sealed interface WorkoutSummaryScreen {
    /** The recorded work is being read and reconciled with its frozen prescription. */
    data object Loading : WorkoutSummaryScreen

    /** The workout reconciled with its prescription. */
    data class Ready(val summary: WorkoutSummary) : WorkoutSummaryScreen

    /** Reading the summary failed; the route offers a retry. */
    data object Failed : WorkoutSummaryScreen

    /** The occurrence no longer exists, so there is nothing to reconcile. */
    data object Unavailable : WorkoutSummaryScreen
}

data class WorkoutLoggerUiState(
    val exerciseInstructions: LoggerExerciseInstructions? = null,
    val exercises: List<ExerciseOption> = emptyList(),
    val exerciseSearch: String = "",
    val selectedExerciseId: String? = null,
    /** Which metric the manual form records; the other entry field is never submitted. */
    val metric: SetMetric = SetMetric.REPETITIONS,
    val reps: String = "",
    val durationSeconds: String = "",
    val weightInput: String = "",
    val rir: String = "",
    val isWarmup: Boolean = false,
    val recentSets: List<LoggedSetRow> = emptyList(),
    val loggedSetEdit: LoggedSetEdit? = null,
    val savingLoggedSet: Boolean = false,
    val loggedSetEditFailed: Boolean = false,
    /**
     * True while a manual "Log set" write and its post-write success handling are in flight. The
     * guard is claimed synchronously, so a repeated tap cannot submit a second set.
     */
    val manualLogInProgress: Boolean = false,
    /** True when the last manual set write failed; every entered value is retained for retry. */
    val manualLogFailed: Boolean = false,
    /**
     * True when the last manual set was persisted but the post-write list/occurrence refresh failed.
     * The set is saved, so this never reads as an unsaved set or invites a duplicate re-log.
     */
    val manualLogRefreshFailed: Boolean = false,
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
    /**
     * The post-finish summary route, or null when it is closed. Opened only by a successful Finish
     * workout or Finish partially; it is transient and never persisted.
     */
    val summary: WorkoutSummaryScreen? = null,
    /**
     * A history entry the summary asked to open, consumed by the history browser once it is on the
     * back stack. Null sends the user to the browser list instead.
     */
    val pendingHistoryKey: WorkoutHistoryKey? = null,
    /** Whether the guided-workout preference is on. Off leaves the Logger's existing flow unchanged. */
    val guidedEnabled: Boolean = false,
    /** The active occurrence's guided progress, or null when guided mode is off or no workout is active. */
    val guidedProgress: GuidedWorkoutProgress? = null,
    /**
     * One paired turn the user explicitly skipped. It records no set and satisfies no target, and is
     * cleared after the next guided write so the outstanding member is selected again.
     */
    val supersetSkippedEntryId: Long? = null,
    /** Pair keys unpaired for this workout only; stored prescriptions and recorded sets are untouched. */
    val supersetUnpairedKeys: Set<String> = emptySet(),
    /**
     * The active occurrence's optional warm-up guidance, or null when no workout is active. Derived
     * from the frozen occurrence; shown only while guided mode is active and the user has not hidden it.
     */
    val warmupGuidance: WarmupGuidance? = null,
    /** Preparation actions checked off in the current workout. In-memory only; never persisted. */
    val warmupPreparationChecked: Set<WarmupAction> = emptySet(),
    /** True when the user hid the current workout's warm-up guidance. In-memory only. */
    val warmupGuidanceHidden: Boolean = false,
    /** True when the last suggested ramp write failed; the suggestion stays available to retry. */
    val warmupRampWriteFailed: Boolean = false,
    /** Transient preparation value edits for the current workout; never persisted. */
    val warmupPreparationEdits: Map<WarmupAction, WarmupPreparationItem> = emptyMap(),
    /**
     * The persisted preparation overrides, keyed by action. Global rather than per occurrence, so an
     * edited measure survives the next occurrence and an app restart; an action absent here uses its
     * built-in default.
     */
    val warmupPreparationOverrides: Map<WarmupAction, WarmupPreparationItem> = emptyMap(),
    /** Preparation items the user skipped for the current workout; never persisted. */
    val warmupPreparationSkipped: Set<WarmupAction> = emptySet(),
    /** Transient ramp value edits for the current workout, keyed by slot and row; never persisted. */
    val warmupRampEdits: Map<WarmupRampKey, WarmupRampSet> = emptyMap(),
    /** The guidance value open in the transient editor, or null when none is open. */
    val warmupEditor: WarmupGuidanceEditor? = null,
    /**
     * True once the first equipment snapshot has been observed. Until then ramp availability and
     * ceilings are unknown, so only preparation is shown and no ramp can be considered.
     */
    val warmupEquipmentKnown: Boolean = false,
    /** True when the last guided single-set write failed; the pending set is still available to retry. */
    val guidedSetWriteFailed: Boolean = false,
    /** The occurrence entry whose current set was explicitly started; it is not performed work. */
    val startedSetEntryId: Long? = null,
    /**
     * Occurrence entry ids whose slot already holds recorded work (working or warm-up), by the domain
     * freeze rule. Replacement is hidden for them: a swap would be refused, so offering it misleads.
     */
    val frozenEntryIds: Set<Long> = emptySet(),
    /** In-memory rest prompt started only by an explicitly live guided completion. */
    val restTimer: RestTimerState? = null,
    val shortRestCoachingExerciseName: String? = null,
    /** Current effective duration in seconds for the active rest timer. */
    val restDurationSeconds: String = "120",
    /** True when the current exercise uses an explicit rest-duration override. */
    val restDurationIsOverride: Boolean = false,
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
    val utcOffsetMillis: Long = 0L,
    /** The draft text for the exercise setup note being edited. */
    val noteText: String = "",
    /** The exercise id whose note is currently being edited, or null when no note editor is open. */
    val noteExerciseId: String? = null,
    /** True while a note save/clear is in flight. */
    val noteSaving: Boolean = false,
    /** True when the last note save/clear failed; the draft is retained for retry. */
    val noteFailed: Boolean = false,
    /** True when the last note load failed; the editor shows an error with retry. */
    val noteLoadFailed: Boolean = false,
    /** True briefly after a successful note save/clear to show a confirmation. */
    val noteSaved: Boolean = false,
    /** The explicit session id whose note is being edited, or null when no session-note editor is open. */
    val sessionNoteSessionId: String? = null,
    /** The draft text for the current session note being edited. */
    val sessionNoteText: String = "",
    /** True while a session-note read is in flight, before the editor can be shown. */
    val sessionNoteLoading: Boolean = false,
    /** True while a session-note save/clear is in flight. */
    val sessionNoteSaving: Boolean = false,
    /** True when the last session-note save/clear failed; the draft is retained for retry. */
    val sessionNoteFailed: Boolean = false,
    /** True when the last session-note load failed; the editor shows an error with retry. */
    val sessionNoteLoadFailed: Boolean = false,
    /** True briefly after a successful session-note save/clear to show a confirmation. */
    val sessionNoteSaved: Boolean = false
) {
    /** True when a backdated time is set; the UI shows a "backdated" indicator. */
    val isBackdated: Boolean
        get() = performedAtMillis != null

    /** True when guided mode is on and an active occurrence is available to guide. */
    val isGuidedActive: Boolean
        get() = guidedEnabled && guidedProgress != null

    /**
     * The entry the guided workflow recommends next, alternating paired members by recorded working
     * sets. Null when every slot is complete or actively skipped.
     */
    val nextGuidedEntryId: Long?
        get() = guidedProgress?.nextExercise(
            skippedEntryIds = supersetSkippedEntryId?.let(::setOf) ?: emptySet(),
            unpairedKeys = supersetUnpairedKeys
        )?.occurrenceEntryId

    /**
     * True when replacement is available for [entryId]: its slot holds no recorded work and it is not
     * the currently started slot. An untouched later slot stays replaceable.
     */
    fun canReplaceEntry(entryId: Long): Boolean =
        entryId !in frozenEntryIds && entryId != startedSetEntryId

    /**
     * The warm-up guidance the UI shows: non-empty, while guided is active and the user has not hidden
     * it. Null leaves the Logger's existing layout unchanged.
     */
    val visibleWarmupGuidance: WarmupGuidance?
        get() = warmupGuidance?.takeIf { isGuidedActive && !warmupGuidanceHidden && !it.isEmpty }

    /**
     * The warm-up guidance rows the UI renders, with the current workout's transient edits and skips
     * applied. Null when nothing is shown. Until the first equipment snapshot arrives the ramp rows
     * are withheld, because availability and ceilings are not yet known; preparation still shows.
     */
    val visibleWarmupView: WarmupGuidanceView?
        get() = visibleWarmupGuidance?.let { guidance ->
            buildWarmupGuidanceView(
                guidance = if (warmupEquipmentKnown) {
                    guidance
                } else {
                    guidance.copy(ramps = emptyList())
                },
                preparationEdits = warmupPreparationEdits,
                overridden = warmupPreparationOverrides.keys,
                skipped = warmupPreparationSkipped,
                rampEdits = warmupRampEdits
            ).takeIf { !it.isEmpty }
        }

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
        get() = selectedExerciseId != null && enteredMetricIsPositive

    /** True when the metric selected in the manual form holds a positive whole number. */
    val enteredMetricIsPositive: Boolean
        get() = when (metric) {
            SetMetric.REPETITIONS -> (reps.toIntOrNull() ?: 0) > 0
            SetMetric.DURATION -> (durationSeconds.toIntOrNull() ?: 0) > 0
        }

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
