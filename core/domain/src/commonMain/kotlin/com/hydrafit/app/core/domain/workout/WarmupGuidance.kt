package com.hydrafit.app.core.domain.workout

import com.hydrafit.app.core.domain.engine.EquipmentWeightLimit
import com.hydrafit.app.core.domain.engine.SetTarget
import com.hydrafit.app.core.domain.engine.SuggestedWeightConfig
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.schedule.OccurrenceEntry
import com.hydrafit.app.core.domain.schedule.WorkoutOccurrence

/**
 * A movement-pattern-relevant preparation action. This is stable identity only: the display name is a
 * localized string resolved by the UI, never a user-facing string held in the domain.
 */
enum class WarmupAction {
    /** Easy whole-body movement; a timed checklist item, never seconds encoded as reps. */
    EASY_GENERAL_MOVEMENT,

    /** Comfortable bodyweight squat and hinge practice. */
    LOWER_BODY_PRACTICE,

    /** Controlled shoulder circles before upper-body work. */
    SHOULDER_CIRCLES,

    /** Light, easy press practice for a push pattern. */
    LIGHT_PUSH_PRACTICE,

    /** Light, easy pull practice for a pull pattern. */
    LIGHT_PULL_PRACTICE
}

/**
 * One optional preparation item. A timed item carries [durationSeconds]; a practice movement carries
 * [reps]. Exactly one of the two is set, so a timed activity is never stored or shown as reps.
 */
data class WarmupPreparationItem(
    val action: WarmupAction,
    val reps: Int? = null,
    val durationSeconds: Int? = null
) {
    init {
        require((reps == null) != (durationSeconds == null)) {
            "A preparation item is either reps-based or timed"
        }
    }
}

/**
 * One ramp suggestion, expressed as a percentage of the working weight it was derived from.
 * [isEquipmentLimited] is true when the current equipment ceiling lowered this load below the
 * percentage, so the percentage no longer describes it.
 */
data class WarmupRampSet(
    val percentOfWorkingWeight: Int,
    val reps: Int,
    /** Rounded load, always positive and strictly below the working weight. */
    val weightKg: Double,
    val isEquipmentLimited: Boolean = false
)

/**
 * The ramp suggestions for one exercise whose target carries a numeric external load. The fields after
 * [rampSets] are the prescription this plan was derived from - the slot's edited effective target when
 * one was supplied, else its frozen prescription: a consumer compares them against the slot it is
 * about to write, so a suggestion can never be recorded against a slot whose exercise, reps or load
 * meaning has since changed. [ceilingKg] is the current equipment ceiling for the slot (null when
 * unbounded or unknown); it bounds edits and is deliberately not part of the prescription fingerprint.
 */
data class WarmupRampPlan(
    val occurrenceEntryId: Long,
    val exerciseId: String,
    val exerciseName: String,
    val movementPattern: MovementPattern,
    val workingWeightKg: Double,
    /** Repetitions for counted work, or null for a timed slot. Never a `0` sentinel. */
    val prescribedReps: Int? = null,
    /** Elapsed whole seconds for a timed slot, or null for counted work. */
    val prescribedDurationSeconds: Int? = null,
    val loadKind: LoadKind,
    val loadCapability: ExerciseLoadCapability,
    val rampSets: List<WarmupRampSet>,
    val ceilingKg: Double? = null
) {
    /**
     * True when [entry] still carries the prescription this plan was built from. A plan derived from
     * the slot's edited [rampTarget] describes that target's load and measure, not the frozen values
     * the entry keeps, so the supplied target is the one compared.
     */
    fun matchesPrescription(entry: OccurrenceEntry, rampTarget: SetTarget? = null): Boolean {
        val targetWeightKg = if (rampTarget == null) entry.weightKg else rampTarget.weightKg
        val targetReps = if (rampTarget == null) entry.reps else rampTarget.reps
        val targetDurationSeconds =
            if (rampTarget == null) entry.durationSeconds else rampTarget.durationSeconds
        val targetLoadKind = if (rampTarget == null) entry.loadKind else rampTarget.loadKind
        return occurrenceEntryId == entry.id &&
            exerciseId == entry.exerciseId &&
            movementPattern == entry.movementPattern &&
            workingWeightKg == targetWeightKg &&
            prescribedReps == targetReps &&
            prescribedDurationSeconds == targetDurationSeconds &&
            loadKind == targetLoadKind &&
            loadCapability == entry.loadCapability
    }

    /** True when [other] describes the same prescription for the same slot. */
    fun samePrescriptionAs(other: WarmupRampPlan): Boolean =
        occurrenceEntryId == other.occurrenceEntryId &&
            exerciseId == other.exerciseId &&
            movementPattern == other.movementPattern &&
            workingWeightKg == other.workingWeightKg &&
            prescribedReps == other.prescribedReps &&
            prescribedDurationSeconds == other.prescribedDurationSeconds &&
            loadKind == other.loadKind &&
            loadCapability == other.loadCapability
}

/** The optional warm-up guidance for an active occurrence: preparation and per-pattern ramps. */
data class WarmupGuidance(
    val preparation: List<WarmupPreparationItem>,
    val ramps: List<WarmupRampPlan>
) {
    val isEmpty: Boolean get() = preparation.isEmpty() && ramps.isEmpty()
}

/**
 * Current equipment availability and ceilings for warm-up guidance. [selectedTags] is the equipment
 * the user owns; [maxWeights] records the heaviest weight each tag can provide (absent when
 * unlimited). Used to bound ramp suggestions only — it never rewrites the frozen prescription.
 */
data class WarmupEquipment(
    val selectedTags: Set<EquipmentTag>,
    val maxWeights: Map<EquipmentTag, Double> = emptyMap()
)

/**
 * Editable product defaults, not validated personal optima. Each ramp set is a percentage of the
 * working weight it is suggested from with an easy rep count.
 */
private val RAMP_PROFILE: List<Pair<Int, Int>> = listOf(40 to 8, 60 to 5, 80 to 3)

/** Easy general movement is a timed item; seconds are never stored as reps. */
private const val EASY_GENERAL_MOVEMENT_SECONDS = 180

/** Easy practice reps for a dynamic movement item. */
private const val PRACTICE_REPS = 10

private val LOWER_BODY_PATTERNS = setOf(
    MovementPattern.SQUAT,
    MovementPattern.HINGE,
    MovementPattern.LUNGE,
    MovementPattern.CALF_RAISE,
    MovementPattern.LEG_ISOLATION
)

private val UPPER_PUSH_PATTERNS = setOf(
    MovementPattern.HORIZONTAL_PUSH,
    MovementPattern.VERTICAL_PUSH,
    MovementPattern.CHEST_FLY,
    MovementPattern.TRICEPS_ISOLATION,
    MovementPattern.SHOULDER_ISOLATION
)

private val UPPER_PULL_PATTERNS = setOf(
    MovementPattern.HORIZONTAL_PULL,
    MovementPattern.VERTICAL_PULL,
    MovementPattern.BICEPS_ISOLATION
)

/**
 * Builds the optional warm-up guidance for [occurrence]. Pure: it reads no repository or clock and
 * derives everything from the occurrence's prescription, so the guidance never contradicts the target
 * the user is being asked to perform. [equipment] bounds ramp availability and load against the
 * user's current equipment; a null value means the constraints are not yet known, so ramps are
 * suggested from the target alone.
 *
 * [preparationOverrides] replaces a preparation item's built-in measure with the user's persisted
 * override for that action, so a later occurrence carries the edited value instead of the default.
 * An action with no entry keeps its built-in default.
 *
 * [rampTargets] carries the edited effective target for an entry whose remaining prescription the user
 * changed: its ramp is suggested from that target, while the recorded warm-ups and the frozen
 * prescription stay as they were. An entry absent from the map keeps its frozen behavior.
 */
fun buildWarmupGuidance(
    occurrence: WorkoutOccurrence,
    equipment: WarmupEquipment? = null,
    preparationOverrides: Map<WarmupAction, WarmupPreparationItem> = emptyMap(),
    rampTargets: Map<Long, SetTarget> = emptyMap()
): WarmupGuidance = WarmupGuidance(
    preparation = buildWarmupPreparation(occurrence.entries.map { it.movementPattern })
        .map { item -> preparationOverrides[item.action] ?: item },
    ramps = buildWarmupRamps(occurrence.entries.sortedBy { it.position }, equipment, rampTargets)
)

/**
 * The preparation selection for the patterns present, in a fixed order. General movement is always
 * offered; a pattern's dynamic practice is added only when the workout actually trains it.
 */
internal fun buildWarmupPreparation(patterns: List<MovementPattern>): List<WarmupPreparationItem> {
    val items = mutableListOf(
        WarmupPreparationItem(
            WarmupAction.EASY_GENERAL_MOVEMENT,
            durationSeconds = EASY_GENERAL_MOVEMENT_SECONDS
        )
    )
    if (patterns.any { it in LOWER_BODY_PATTERNS }) {
        items += WarmupPreparationItem(WarmupAction.LOWER_BODY_PRACTICE, reps = PRACTICE_REPS)
    }
    if (patterns.any { it in UPPER_PUSH_PATTERNS || it in UPPER_PULL_PATTERNS }) {
        items += WarmupPreparationItem(WarmupAction.SHOULDER_CIRCLES, reps = PRACTICE_REPS)
    }
    if (patterns.any { it in UPPER_PUSH_PATTERNS }) {
        items += WarmupPreparationItem(WarmupAction.LIGHT_PUSH_PRACTICE, reps = PRACTICE_REPS)
    }
    if (patterns.any { it in UPPER_PULL_PATTERNS }) {
        items += WarmupPreparationItem(WarmupAction.LIGHT_PULL_PRACTICE, reps = PRACTICE_REPS)
    }
    return items
}

/**
 * One ramp plan for the first rampable entry of each movement pattern, in entry order. A pattern's
 * later entries have nothing new to rehearse, so only its first ramp is suggested. A bodyweight, added
 * or unknown-load entry has no working weight to ramp from and leaves its pattern eligible for a
 * later rampable entry of the same pattern. When [equipment] is known, an entry whose required
 * equipment is not all owned is skipped, and each suggested load is clamped to that equipment's
 * ceiling. An entry named in [rampTargets] is ramped from that edited target instead of its frozen
 * values, so the suggestion follows the work that is actually still ahead.
 */
internal fun buildWarmupRamps(
    entries: List<OccurrenceEntry>,
    equipment: WarmupEquipment? = null,
    rampTargets: Map<Long, SetTarget> = emptyMap()
): List<WarmupRampPlan> {
    val planned = mutableSetOf<MovementPattern>()
    val plans = mutableListOf<WarmupRampPlan>()
    for (entry in entries) {
        if (entry.movementPattern in planned) continue
        if (entry.loadCapability != ExerciseLoadCapability.EXTERNAL) continue
        val effective = rampTargets[entry.id]
        val loadKind = if (effective == null) entry.loadKind else effective.loadKind
        if (loadKind != LoadKind.EXTERNAL) continue
        // A distance slot is a carry or a locomotion bout, not a rep-loaded lift: there is no
        // meaningful share of its load to ramp through, and distance never progresses automatically.
        val distanceMeters =
            if (effective == null) entry.distanceMeters else effective.distanceMeters
        if (distanceMeters != null) continue
        // A measured-force slot is a device reading, not a rep-loaded lift: it has no shareable load
        // to ramp through and never feeds an automatic progression.
        val forceNewtons = if (effective == null) entry.forceNewtons else effective.forceNewtons
        if (forceNewtons != null) continue
        if (!isEquipmentAvailable(entry, equipment)) continue
        val working = if (effective == null) entry.weightKg else effective.weightKg
        if (working == null || !working.isFinite() || working <= 0.0) continue
        val ceiling = equipmentCeiling(entry, equipment)
        val rampSets = rampSetsFor(working, ceiling)
        if (rampSets.isEmpty()) continue
        planned += entry.movementPattern
        plans += WarmupRampPlan(
            occurrenceEntryId = entry.id,
            exerciseId = entry.exerciseId,
            exerciseName = entry.exerciseName,
            movementPattern = entry.movementPattern,
            workingWeightKg = working,
            prescribedReps = if (effective == null) entry.reps else effective.reps,
            prescribedDurationSeconds =
            if (effective == null) entry.durationSeconds else effective.durationSeconds,
            loadKind = loadKind,
            loadCapability = entry.loadCapability,
            rampSets = rampSets,
            ceilingKg = ceiling
        )
    }
    return plans
}

/** True when the entry's required equipment is all owned; unknown constraints impose no limit. */
private fun isEquipmentAvailable(entry: OccurrenceEntry, equipment: WarmupEquipment?): Boolean {
    if (equipment == null) return true
    return entry.requiredEquipment
        .filterNot { it == EquipmentTag.BODYWEIGHT }
        .all { it in equipment.selectedTags }
}

/** The lowest ceiling among the entry's required equipment, or null when unknown/unbounded. */
private fun equipmentCeiling(entry: OccurrenceEntry, equipment: WarmupEquipment?): Double? =
    equipment?.let { EquipmentWeightLimit.ceilingFor(entry.requiredEquipment, it.maxWeights) }

/**
 * Rounds each profile percentage to the shared plate increment, clamps it to the equipment ceiling,
 * and keeps only loads that are positive and strictly below the working weight. A tiny working weight
 * can round every ramp onto itself or to zero, which yields no ramp instead of an unusable or
 * duplicate one. A load the ceiling lowered is marked so its percentage is not shown as if it were
 * exact.
 */
private fun rampSetsFor(workingWeightKg: Double, ceilingKg: Double?): List<WarmupRampSet> {
    val config = SuggestedWeightConfig()
    val seen = mutableSetOf<Double>()
    return RAMP_PROFILE.mapNotNull { (percent, reps) ->
        val raw = config.roundToIncrement(workingWeightKg * percent / 100.0)
        val rounded = EquipmentWeightLimit.clamp(raw, ceilingKg)
        if (rounded <= 0.0 || rounded >= workingWeightKg) return@mapNotNull null
        if (!seen.add(rounded)) return@mapNotNull null
        WarmupRampSet(
            percentOfWorkingWeight = percent,
            reps = reps,
            weightKg = rounded,
            isEquipmentLimited = rounded != raw
        )
    }
}
