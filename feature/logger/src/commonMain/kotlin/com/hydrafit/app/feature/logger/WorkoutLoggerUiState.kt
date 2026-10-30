package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.engine.ExerciseTargetExplanation
import com.hydrafit.app.core.domain.engine.SetTarget
import com.hydrafit.app.core.domain.engine.SlotProgressionDecision
import com.hydrafit.app.core.domain.engine.SlotSetTargets
import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.equipment.EquipmentStation
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.StationAttribution
import com.hydrafit.app.core.domain.equipment.matchesExerciseNameQuery
import com.hydrafit.app.core.domain.routine.ClusterPrescription
import com.hydrafit.app.core.domain.time.localEpochDay
import com.hydrafit.app.core.domain.unit.DistanceUnit
import com.hydrafit.app.core.domain.unit.WeightUnit
import com.hydrafit.app.core.domain.workout.DeviceAttribution
import com.hydrafit.app.core.domain.workout.GuidedExerciseProgress
import com.hydrafit.app.core.domain.workout.GuidedWorkoutProgress
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.PerformedClusterSegment
import com.hydrafit.app.core.domain.workout.RestCountdown
import com.hydrafit.app.core.domain.workout.WarmupAction
import com.hydrafit.app.core.domain.workout.WarmupGuidance
import com.hydrafit.app.core.domain.workout.WarmupPreparationItem
import com.hydrafit.app.core.domain.workout.WarmupRampSet
import com.hydrafit.app.core.domain.workout.WorkoutClusterSummary
import com.hydrafit.app.core.domain.workout.WorkoutHistoryKey
import com.hydrafit.app.core.domain.workout.WorkoutSession
import com.hydrafit.app.core.domain.workout.WorkoutSummary
import com.hydrafit.app.core.exercisecontent.ExerciseCautions

data class ExerciseOption(
    val id: String,
    val name: String,
    val isBodyweight: Boolean,
    val isUnilateral: Boolean = false,
    val loadCapability: ExerciseLoadCapability = ExerciseLoadCapability.EXTERNAL,
    val requiredEquipment: Set<EquipmentTag> = emptySet()
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
    val durationSeconds: Int? = null,
    /** Whole metres for a distance set, or null for reps and timed work. */
    val distanceMeters: Int? = null,
    /** Measured force in newtons for a force set, or null for every other metric. */
    val forceNewtons: Double? = null,
    /** The measuring device recorded with a force set, or null for none. Descriptive only. */
    val device: DeviceAttribution? = null,
    /**
     * The cluster identity this set was logged as, or null for ordinary work. The row still shows
     * only the parent set's own measurement; the segments are never listed as further sets.
     */
    val cluster: WorkoutClusterSummary? = null
)

/** Null identity is unrecorded, never a shared session boundary. */
internal fun LoggedSetRow.startsSessionAfter(previous: LoggedSetRow?): Boolean =
    sessionId != null && (previous == null || sessionId != previous.sessionId)

/**
 * Which single measurement a form records: repetitions, elapsed whole seconds, distance or measured
 * force. The entry fields are mutually exclusive because the chosen metric alone is submitted, so a
 * form can never write two.
 */
enum class SetMetric { REPETITIONS, DURATION, DISTANCE, FORCE }

/**
 * True when the device fields describe a storable attribution: a blank name means no device and no
 * rating, and a rating must be finite and positive. A rating without a name is never accepted.
 */
internal fun deviceInputIsValid(name: String, ratingKg: String): Boolean {
    val trimmedName = name.trim()
    val trimmedRating = ratingKg.trim()
    return when {
        trimmedName.isEmpty() -> trimmedRating.isEmpty()
        trimmedRating.isEmpty() -> true
        else -> trimmedRating.toDoubleOrNull()?.let { it.isFinite() && it > 0.0 } == true
    }
}

/** The device an editor describes, or null when the name is blank. Rating stays descriptive. */
internal fun deviceInputOrNull(name: String, ratingKg: String): DeviceAttribution? {
    val trimmedName = name.trim()
    if (trimmedName.isEmpty()) return null
    return DeviceAttribution(trimmedName, ratingKg.trim().toDoubleOrNull())
}

/**
 * The stored value as entry text that parses back to the *same* double.
 *
 * Force and a device rating are prefilled into an editable field and re-read on submit, so a
 * one-decimal display formatter would rewrite an untouched 247.35 N as 247.4 N. A weight avoids
 * that by remembering its original text; these fields instead round-trip exactly, which keeps every
 * prefill, quick-fill and no-op correction from drifting the measurement. Exponential output is
 * expanded to plain decimal digits, because the entry filters accept only digits and a dot.
 */
internal fun exactEntryText(value: Double): String {
    val text = value.toString()
    if ('E' !in text && 'e' !in text) return text.removeSuffix(".0")
    // A very small or very large value prints in exponential form, which the digits-and-dot entry
    // filters would truncate on the next keystroke. Expand the mantissa textually so the whole
    // representable range survives, including magnitudes beyond Long.
    val marker = text.indexOfFirst { it == 'E' || it == 'e' }
    val mantissa = text.substring(0, marker)
    val exponent = text.substring(marker + 1).toInt()
    val negative = mantissa.startsWith("-")
    val unsigned = mantissa.removePrefix("-")
    val dot = unsigned.indexOf('.')
    val digits = unsigned.replace(".", "")
    val integerDigits = if (dot < 0) unsigned.length else dot
    val point = integerDigits + exponent
    val raw = when {
        point <= 0 -> "0." + "0".repeat(-point) + digits
        point >= digits.length -> digits + "0".repeat(point - digits.length)
        else -> digits.substring(0, point) + "." + digits.substring(point)
    }
    // Trailing zeros are only redundant after a decimal point; stripping them from a plain integer
    // would change its magnitude.
    val expanded = if ('.' in raw) raw.trimEnd('0').trimEnd('.') else raw
    return if (negative) "-$expanded" else expanded
}

/** One recorded later cluster segment as editable text, entered in the editor's frozen unit. */
data class LoggedClusterSegmentEdit(
    val position: Int,
    val reps: String,
    val weightInput: String,
    val rir: String = ""
) {
    val isValid: Boolean
        get() = (reps.toIntOrNull() ?: 0) > 0 &&
            weightInput.toDoubleOrNull()?.let { it.isFinite() && it > 0.0 } == true &&
            (rir.isBlank() || rir.toIntOrNull()?.let { it in 0..10 } == true)

    /** The performed segment this text describes, in canonical kilograms. */
    fun toPerformed(weightUnit: WeightUnit): PerformedClusterSegment? {
        val parsedReps = reps.toIntOrNull() ?: return null
        val parsedWeight = weightInput.toDoubleOrNull() ?: return null
        return PerformedClusterSegment(
            position = position,
            reps = parsedReps,
            weightKg = weightUnit.displayToKilograms(parsedWeight),
            rir = rir.toIntOrNull()
        )
    }
}

/** A correction of an existing row; unit is frozen for the lifetime of this editor. */
data class LoggedSetEdit(
    val row: LoggedSetRow,
    val weightUnit: WeightUnit,
    val distanceUnit: DistanceUnit = DistanceUnit.METERS,
    /** The metric being corrected; the other fields are retained but never submitted. */
    val metric: SetMetric,
    val reps: String,
    val durationSeconds: String,
    val distanceMeters: String,
    /** Measured force in canonical newtons, entered without a unit suffix. */
    val forceNewtons: String = "",
    /** The measuring device's name, or blank for none; only submitted for a force correction. */
    val deviceName: String = "",
    /** The device's stated resistance rating in kilograms, or blank for none. */
    val deviceRatingKg: String = "",
    val weightInput: String,
    val originalWeightInput: String,
    val rir: String,
    val performedAtMillis: Long,
    /**
     * The recorded later cluster segments (positions 1..n) when this row is a cluster. The parent's
     * own opening segment stays in the ordinary fields above, so editing a cluster never invents an
     * extra set position.
     */
    val clusterSegments: List<LoggedClusterSegmentEdit> = emptyList()
) {
    /** True when this correction also rewrites a performed cluster's later segments. */
    val hasCluster: Boolean get() = row.cluster != null

    val canSave: Boolean get() = enteredMetricIsPositive &&
        deviceIsValid &&
        (rir.isBlank() || rir.toIntOrNull()?.let { it in 0..10 } == true) &&
        (
            weightInput.isBlank() ||
                weightInput.toDoubleOrNull()?.let { it.isFinite() && it >= 0 } == true
            ) &&
        // A cluster parent always records the opening segment's external load.
        (!hasCluster || weightInput.toDoubleOrNull()?.let { it.isFinite() && it > 0.0 } == true) &&
        clusterSegments.all { it.isValid }

    /**
     * True when the device fields are storable. A force correction may carry no device at all, but a
     * rating without a name is never valid.
     */
    val deviceIsValid: Boolean
        get() = metric != SetMetric.FORCE || deviceInputIsValid(deviceName, deviceRatingKg)

    /** True when the selected metric holds a positive whole number. */
    val enteredMetricIsPositive: Boolean
        get() = when (metric) {
            SetMetric.REPETITIONS -> (reps.toIntOrNull() ?: 0) > 0
            SetMetric.DURATION -> (durationSeconds.toIntOrNull() ?: 0) > 0
            SetMetric.DISTANCE -> (distanceMeters.toDoubleOrNull() ?: 0.0) > 0.0
            SetMetric.FORCE -> forceNewtons.toDoubleOrNull()
                ?.let { it.isFinite() && it > 0.0 } == true
        }

    /** Repetitions to write, or null when this correction records another measure. */
    val correctionReps: Int?
        get() = if (metric == SetMetric.REPETITIONS) reps.toIntOrNull() else null

    /** Whole seconds to write, or null when this correction records another measure. */
    val correctionDurationSeconds: Int?
        get() = if (metric == SetMetric.DURATION) durationSeconds.toIntOrNull() else null

    /** Whole metres to write, or null for a non-distance correction. */
    val correctionDistanceMeters: Int?
        get() = if (metric == SetMetric.DISTANCE) {
            distanceMeters.toDoubleOrNull()?.let(distanceUnit::displayToMetres)
        } else {
            null
        }

    /** Measured newtons to write, or null for a non-force correction. */
    val correctionForceNewtons: Double?
        get() = if (metric == SetMetric.FORCE) forceNewtons.toDoubleOrNull() else null

    /** The desired device state for a force correction; null explicitly records no device. */
    val correctionDevice: DeviceAttribution?
        get() = if (metric == SetMetric.FORCE) {
            deviceInputOrNull(deviceName, deviceRatingKg)
        } else {
            null
        }

    /**
     * The complete desired segment list for a cluster correction, with the parent's own opening
     * segment at position zero, or null when [weightKg] cannot express one. Only a rep-based cluster
     * edit produces segments: a cluster is rep-based external-load work, and this editor keeps it so.
     */
    fun correctionCluster(weightKg: Double?): List<PerformedClusterSegment>? {
        if (!hasCluster || metric != SetMetric.REPETITIONS) return null
        val openingReps = correctionReps ?: return null
        val openingWeight = weightKg ?: return null
        val later = clusterSegments.map { it.toPerformed(weightUnit) ?: return null }
        return listOf(
            PerformedClusterSegment(
                position = 0,
                reps = openingReps,
                weightKg = openingWeight,
                rir = rir.toIntOrNull()
            )
        ) + later
    }
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
    /** Whole metres for a distance slot, or null for reps and timed work. */
    val distanceMeters: Int? = null,
    /** Measured force in newtons for a force slot, or null for every other metric. */
    val forceNewtons: Double? = null,
    /** The measuring device carried by a force slot, or null for none. Descriptive only. */
    val device: DeviceAttribution? = null,
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
    val orderedPrefill: SetTarget? = null,
    /**
     * The slot's frozen cluster prescription, or null for ordinary work. A cluster is one prescribed
     * set position: it never changes [sets], and its later segments are recorded beside the parent
     * set rather than as additional sets.
     */
    val cluster: ClusterPrescription? = null
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
    val distanceMeters: String = "",
    /** Measured force in canonical newtons, entered without a unit suffix. */
    val forceNewtons: String = "",
    /** The measuring device's name, or blank for none; only submitted for a force draft. */
    val deviceName: String = "",
    /** The device's stated resistance rating in kilograms, or blank for none. */
    val deviceRatingKg: String = "",
    /**
     * The display unit [distanceMeters] was typed in; frozen for the editor's lifetime like the
     * weight unit so a later preference change cannot reinterpret the same digits.
     */
    val distanceUnit: DistanceUnit = DistanceUnit.METERS,
    val weightInput: String,
    /**
     * The display unit [weightInput] was typed in. The editor freezes it for its lifetime so a
     * later preference change converts the text instead of reinterpreting the same digits.
     */
    val weightUnit: WeightUnit,
    /**
     * The exact stored kilograms behind an untouched weight field. Reopening converts it for
     * display, and an unedited re-apply keeps this canonical value through unit changes.
     */
    val canonicalWeightKg: Double? = null,
    val rir: String,
    val weightRevealed: Boolean,
    val performedAtMillis: Long?,
    val performedAtExplicit: Boolean = false,
    /**
     * The cluster's later segments as entered, seeded from the prescription when the editor opens.
     * Segment 1 is the editor's own reps/weight/RIR fields, so this list is empty for ordinary work.
     */
    val laterSegments: List<ClusterSegmentInput> = emptyList(),
    /** True when Reset staged original guided prescription values for the next Apply. */
    val resetGuidedTargets: Boolean = false
) {
    /** True when the frozen draft records elapsed seconds instead of repetitions. */
    val isTimed: Boolean get() = draft.durationSeconds != null

    /** True when the frozen draft records a distance instead of reps or seconds. */
    val isDistance: Boolean get() = draft.distanceMeters != null

    /** True when the frozen draft records measured force instead of the other measures. */
    val isForce: Boolean get() = draft.forceNewtons != null

    /**
     * True when the device fields are storable. Only a force draft can carry one, and a rating
     * without a name is never valid.
     */
    val deviceIsValid: Boolean get() = !isForce || deviceInputIsValid(deviceName, deviceRatingKg)

    /** True when the frozen measure's field holds a positive whole number. */
    val enteredMetricIsPositive: Boolean
        get() = when {
            isForce -> forceNewtons.toDoubleOrNull()?.let { it.isFinite() && it > 0.0 } == true
            isDistance -> (distanceMeters.toDoubleOrNull() ?: 0.0) > 0.0
            isTimed -> (durationSeconds.toIntOrNull() ?: 0) > 0
            else -> (reps.toIntOrNull() ?: 0) > 0
        }

    /**
     * True when this editor still describes the same pending row: same occurrence entry, same
     * exercise, same measure, the same remaining-set position and the same ordered target. A
     * replacement in that entry, a measure edit, a consumed set (the remaining count/position
     * changed) or a newly consumed set therefore expires the edit instead of showing stale values.
     */
    fun targetsPendingDraft(draft: DraftSet): Boolean = this.draft.occurrenceEntryId != null &&
        this.draft.occurrenceEntryId == draft.occurrenceEntryId &&
        this.draft.exerciseId == draft.exerciseId &&
        this.draft.sets == draft.sets &&
        isTimed == (draft.durationSeconds != null) &&
        isDistance == (draft.distanceMeters != null) &&
        isForce == (draft.forceNewtons != null) &&
        this.draft.orderedPrefill == draft.orderedPrefill

    fun metadataBelongsToPendingDraft(draft: DraftSet): Boolean =
        this.draft.occurrenceEntryId != null &&
            this.draft.occurrenceEntryId == draft.occurrenceEntryId &&
            this.draft.exerciseId == draft.exerciseId &&
            this.draft.sets == draft.sets &&
            isTimed == (draft.durationSeconds != null) &&
            isDistance == (draft.distanceMeters != null) &&
            isForce == (draft.forceNewtons != null)
}

/**
 * The open "Change measurement" dialog for one unstarted occurrence slot. The metric is chosen
 * explicitly; the typed value is never derived from the previous measure.
 */
data class MeasureChangeDialog(
    val occurrenceEntryId: Long,
    val exerciseName: String,
    val metric: SetMetric,
    val value: String = "",
    val applying: Boolean = false,
    /** True when the typed value is not a positive number; the screen shows its localized hint. */
    val invalidValue: Boolean = false,
    /** Why the last apply was refused; the dialog stays open so a corrected value can be sent. */
    val error: String? = null
)

/**
 * How a numeric dumbbell load is stated. A stored value is always per implement: unilateral work
 * is per hand, other dumbbell work per dumbbell. It is never doubled or halved for display.
 */
enum class LoadBasis { PER_HAND, PER_DUMBBELL }

/** Null for non-dumbbell equipment, whose loads stay unlabeled. */
internal fun loadBasisFor(requiresDumbbell: Boolean, isUnilateral: Boolean): LoadBasis? = when {
    !requiresDumbbell -> null
    isUnilateral -> LoadBasis.PER_HAND
    else -> LoadBasis.PER_DUMBBELL
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

/** True when both drafts describe the same frozen slot: the same occurrence entry, else exercise. */
internal fun DraftSet.sameSlotAs(other: DraftSet): Boolean =
    if (occurrenceEntryId != null || other.occurrenceEntryId != null) {
        occurrenceEntryId == other.occurrenceEntryId
    } else {
        exerciseId == other.exerciseId
    }

/** Which stage a foreground timed-set timer is in. */
enum class SetTimerPhase { PREPARING, WORKING, CONFIRMING }

/** A one-shot explanation shown after a timed confirmation is closed without writing its value. */
enum class SetTimerNotice {
    /** A pre-write gate took over the slot, so the measured seconds could not be carried into it. */
    GATE_TOOK_OVER
}

/**
 * The foreground-only timer for one seconds-based draft. Preparation is a fixed countdown before the
 * work countdown starts, and neither phase records anything: reaching zero or stopping early only
 * opens [SetTimerPhase.CONFIRMING], whose measured work seconds Log set writes. The prescribed
 * target in [draft] is never changed.
 */
data class SetTimerState(
    /** The draft the timer was started for; it identifies the one target slot. */
    val draft: DraftSet,
    val phase: SetTimerPhase,
    /** Whole seconds left in the current phase; zero while confirming. */
    val remainingSeconds: Int,
    /** Measured whole work seconds, excluding preparation; the value Log set writes. */
    val measuredSeconds: Int = 0,
    /** True while the measured set is being written, so a repeated Log tap cannot submit twice. */
    val logInProgress: Boolean = false,
    /** True when the last write failed; the confirmation stays open for an explicit retry. */
    val writeFailed: Boolean = false
)

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
    /** Dumbbell load basis per exercise id; non-dumbbell and unknown ids stay absent. */
    val exerciseLoadBasis: Map<String, LoadBasis> = emptyMap(),
    val exerciseSearch: String = "",
    /** Active profile stations; retired rows stay in history but are not offered here. */
    val stations: List<EquipmentStation> = emptyList(),
    val selectedExerciseId: String? = null,
    /** The station chosen for the manual form, or null to log unattributed work. */
    val selectedStationId: Long? = null,
    /** Which metric the manual form records; the other entry field is never submitted. */
    val metric: SetMetric = SetMetric.REPETITIONS,
    val reps: String = "",
    val durationSeconds: String = "",
    val distanceMeters: String = "",
    /** Measured force in canonical newtons for the manual form, entered as a plain number. */
    val forceNewtons: String = "",
    /** The measuring device's name for a force set, or blank for none. Descriptive only. */
    val deviceName: String = "",
    /** The device's stated resistance rating in kilograms, or blank for none. */
    val deviceRatingKg: String = "",
    val distanceUnit: DistanceUnit = DistanceUnit.METERS,
    val weightInput: String = "",
    /** Canonical value represented by the manual weight text, retained across display-unit changes. */
    val manualWeightKg: Double? = null,
    /** Canonical whole-metre value represented by the manual distance text. */
    val manualDistanceMetres: Int? = null,
    val rir: String = "",
    val isWarmup: Boolean = false,
    val recentSets: List<LoggedSetRow> = emptyList(),
    val loggedSetEdit: LoggedSetEdit? = null,
    val clusterLog: ClusterLogState? = null,
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
    /**
     * The active occurrence's progression decisions, keyed by occurrence entry id. Only slots that
     * carry an approved policy appear; the Logger shows one only when it also passes
     * [SlotProgressionDecision.canApply]. Computed once per occurrence refresh, never per draft, and
     * cleared when the workout context changes.
     */
    val progressionSuggestions: Map<Long, SlotProgressionDecision> = emptyMap(),
    /** The entry whose suggestion Apply is writing, or null when no apply is in flight. */
    val progressionApplyEntryId: Long? = null,
    /** True when the last suggestion apply failed; the suggestion stays available to retry. */
    val progressionApplyFailed: Boolean = false,
    /**
     * Unstarted, structurally simple occurrence slots whose measurement can still be corrected.
     * Empty once the occurrence has any recorded set or its slot carries ordered targets or a cluster.
     */
    val measureChangeEntryIds: Set<Long> = emptySet(),
    /** The open measurement dialog, or null when none is open. */
    val measureChange: MeasureChangeDialog? = null,
    val draftEdit: DraftEdit? = null,
    /**
     * Why the last cluster draft submission was refused, or null when none was. Nothing is written
     * for a refused cluster and its draft and editor stay in place; the error clears on the next
     * edit, so a corrected segment can be submitted again.
     */
    val clusterDraftError: ClusterDraftError? = null,
    val missingLoadPrompt: DraftSet? = null,
    val confirmingAllDrafts: Boolean = false,
    val draftWriteInProgress: Boolean = false,
    /** Independent partially written drafts awaiting retry in the current context. */
    val draftWriteRetries: List<DraftWriteRetry> = emptyList(),
    /**
     * Guided drafts whose editor values were applied but not yet logged, keyed by occurrence entry.
     * In-memory only: they seed the pending row through ordinary refreshes and are dropped when the
     * set is logged, the editor's draft is removed, the slot's exercise or measure changes, the
     * pending ordered target advances, or the workout context changes.
     */
    val appliedGuidedEdits: Map<Long, DraftEdit> = emptyMap(),
    /**
     * Completed guided exercises the user explicitly expanded, keyed by occurrence entry with the
     * recorded working sets at expansion. In-memory only: an expansion lasts only while that
     * entry's completion is unchanged, and it is dropped with the workout.
     */
    val guidedExpandedEntries: Map<Long, Int> = emptyMap(),
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
    /** True while the persisted guided target Apply/Reset transaction is in flight. */
    val guidedTargetsSaving: Boolean = false,
    /** A failed target transaction leaves the editor open and retryable. */
    val guidedTargetsFailed: Boolean = false,
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
    /**
     * The foreground timed-set timer for one seconds-based draft, or null when none is running. It is
     * transient and never persisted: leaving the screen cancels it with nothing recorded.
     */
    val setTimer: SetTimerState? = null,
    /** A transient explanation about a timed confirmation that ended without logging. */
    val setTimerNotice: SetTimerNotice? = null,
    val shortRestCoachingExerciseName: String? = null,
    /** Current effective duration in seconds for the active rest timer. */
    val restDurationSeconds: String = "120",
    /** True when the current exercise uses an explicit rest-duration override. */
    val restDurationIsOverride: Boolean = false,
    /**
     * True when the active rest prompt came from a superset override. Its duration is pair rest, not a
     * per-exercise preference, so the field is read-only and editing it must never write an override.
     */
    val restDurationIsPairRest: Boolean = false,
    /** True when the active prompt is the short transition between paired members. */
    val restDurationIsSupersetTransition: Boolean = false,
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
     * The paired entry that owns the current round: an explicitly started member, else the entry
     * [nextGuidedEntryId] already selects. Null for a single exercise and for an unpaired pair.
     */
    private val activeSupersetEntryId: Long?
        get() {
            val entries = guidedProgress?.exercises ?: return null
            val started = entries.firstOrNull { it.occurrenceEntryId == startedSetEntryId }
                ?.takeIf { entry ->
                    entry.superset?.let { it.key !in supersetUnpairedKeys } == true
                }
            val turnId = started?.occurrenceEntryId ?: nextGuidedEntryId
            val turn = entries.firstOrNull { it.occurrenceEntryId == turnId } ?: return null
            val pair = turn.superset ?: return null
            if (pair.key in supersetUnpairedKeys) return null
            return turn.occurrenceEntryId
        }

    /**
     * True when [entry] shows its start/log/edit controls. A single exercise keeps them while work
     * remains; a paired member only while it owns the round, so the partner can never log the same
     * round as its duplicate.
     */
    fun showsGuidedActions(entry: GuidedExerciseProgress): Boolean {
        if (entry.isComplete) return false
        val key = entry.superset?.key ?: return true
        if (key in supersetUnpairedKeys) return true
        return entry.occurrenceEntryId == activeSupersetEntryId
    }

    /**
     * True when [entry] shows skip/resume/unpair. That is the paired member holding the turn, plus
     * the skipped member when its pair has no turn left, so Resume stays reachable.
     */
    fun showsSupersetControls(entry: GuidedExerciseProgress): Boolean {
        val key = entry.superset?.key ?: return false
        if (key in supersetUnpairedKeys || entry.isComplete) return false
        if (entry.occurrenceEntryId == activeSupersetEntryId) return true
        if (entry.occurrenceEntryId != supersetSkippedEntryId) return false
        val active = guidedProgress?.exercises
            ?.firstOrNull { it.occurrenceEntryId == activeSupersetEntryId }
        return active?.superset?.key != key
    }

    /**
     * True when [entry]'s prescribed work is shown as rows. Unfinished work is never collapsed; a
     * complete exercise collapses unless the user expanded it for this exact completion.
     */
    fun isGuidedEntryExpanded(entry: GuidedExerciseProgress): Boolean = !entry.isComplete ||
        entry.occurrenceEntryId == activeSupersetEntryId ||
        guidedExpandedEntries[entry.occurrenceEntryId] == entry.performedWorkingSets

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

    /** False while the prompt is pair rest: editing the field would write a per-exercise override. */
    val restDurationIsEditable: Boolean
        get() = !restDurationIsPairRest && !restDurationIsSupersetTransition

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
        get() = selectedExerciseId != null &&
            enteredMetricIsPositive &&
            deviceIsValid &&
            manualWeightIsValid &&
            manualDistanceIsValid

    private val manualWeightIsValid: Boolean
        get() = !showWeightField ||
            weightInput.isBlank() ||
            weightInput.toDoubleOrNull()?.let { value ->
                value.isFinite() && value >= 0.0 && manualWeightKg != null
            } == true

    private val manualDistanceIsValid: Boolean
        get() = metric != SetMetric.DISTANCE ||
            distanceMeters.isBlank() ||
            distanceMeters.toDoubleOrNull()?.let { value ->
                value.isFinite() && manualDistanceMetres != null
            } == true

    /**
     * True when the manual form's device fields may be submitted: only a force reading may carry a
     * device, and a rating without a name is never valid.
     */
    val deviceIsValid: Boolean
        get() = metric != SetMetric.FORCE || deviceInputIsValid(deviceName, deviceRatingKg)

    /** True when the metric selected in the manual form holds a positive whole number. */
    val enteredMetricIsPositive: Boolean
        get() = when (metric) {
            SetMetric.REPETITIONS -> (reps.toIntOrNull() ?: 0) > 0
            SetMetric.DURATION -> (durationSeconds.toIntOrNull() ?: 0) > 0
            SetMetric.DISTANCE -> (manualDistanceMetres ?: 0) > 0
            SetMetric.FORCE -> forceNewtons.toDoubleOrNull()
                ?.let { it.isFinite() && it > 0.0 } == true
        }

    /**
     * Whether the weight field is shown: always for weighted exercises, and for bodyweight
     * exercises only once the user reveals it to log a weighted variant. A force reading is
     * load-free, so it never shows a load field.
     */
    val showWeightField: Boolean
        get() {
            if (metric == SetMetric.FORCE) return false
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

    /** Stations compatible with the selected exercise's current equipment tags. */
    val availableStations: List<EquipmentStation>
        get() {
            val required = exercises.firstOrNull { it.id == selectedExerciseId }
                ?.requiredEquipment
                .orEmpty()
            return stations.filter { !it.retired && it.equipmentId in required }
        }

    val selectedStation: EquipmentStation?
        get() = availableStations.firstOrNull { it.id == selectedStationId }

    /** The frozen attribution submitted with the next set, or null for unattributed work. */
    val selectedStationAttribution: StationAttribution?
        get() = selectedStation?.attribution()

    /** Exercises matching the picker search, or all of them when the search is blank. */
    val visibleExercises: List<ExerciseOption>
        get() = if (exerciseSearch.isBlank()) {
            exercises
        } else {
            exercises.filter { matchesExerciseNameQuery(it.name, exerciseSearch) }
        }
}

/**
 * The rest-duration state after a prompt starts. A superset override is pair rest, so it never also
 * counts as a per-exercise override.
 */
internal fun WorkoutLoggerUiState.withRestStarted(
    seconds: Long,
    hasExerciseOverride: Boolean,
    fromPairOverride: Boolean,
    fromTransition: Boolean = false
): WorkoutLoggerUiState = copy(
    restDurationSeconds = seconds.toString(),
    restDurationIsOverride = hasExerciseOverride,
    restDurationIsPairRest = fromPairOverride && !fromTransition,
    restDurationIsSupersetTransition = fromTransition
)

/** Clears the superset rest label when its prompt is dismissed or otherwise ends. */
internal fun WorkoutLoggerUiState.withRestCleared(): WorkoutLoggerUiState =
    copy(restDurationIsPairRest = false, restDurationIsSupersetTransition = false)

/**
 * Toggles an explicit expansion for a completed entry. Unfinished work never collapses, so such an
 * entry is returned unchanged; an expansion is recorded against the completion it was made for.
 */
internal fun WorkoutLoggerUiState.withGuidedExpansionToggled(
    entry: GuidedExerciseProgress
): WorkoutLoggerUiState {
    if (!entry.isComplete) return this
    val expanded = guidedExpandedEntries[entry.occurrenceEntryId] == entry.performedWorkingSets
    return copy(
        guidedExpandedEntries = if (expanded) {
            guidedExpandedEntries - entry.occurrenceEntryId
        } else {
            guidedExpandedEntries + (entry.occurrenceEntryId to entry.performedWorkingSets)
        }
    )
}

/**
 * Publishes refreshed guided progress and reconciles explicit expansions with it. An expansion
 * belongs to one recorded completion, so removing a recorded set drops it and the exercise shows
 * its rows again; switching workout forgets every expansion.
 */
internal fun WorkoutLoggerUiState.withGuidedProgress(
    progress: GuidedWorkoutProgress,
    sameOccurrence: Boolean
): WorkoutLoggerUiState = copy(
    guidedProgress = progress,
    guidedExpandedEntries = if (!sameOccurrence) {
        emptyMap()
    } else {
        guidedExpandedEntries.filter { (entryId, performedAtExpansion) ->
            progress.exercises.any { entry ->
                entry.occurrenceEntryId == entryId &&
                    entry.performedWorkingSets == performedAtExpansion
            }
        }
    }
)
