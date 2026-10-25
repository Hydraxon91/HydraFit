package com.hydrafit.app.core.domain.engine

/**
 * Recorded provenance for a generated target, so the load, progression, equipment, fatigue and
 * deload decisions behind a plan can be explained from the branches that produced them rather than
 * reconstructed from the final numbers.
 *
 * These records are inert data: carrying them never changes selection, sets, reps or loads, and a
 * record that disagrees with the output it accompanies is a bug. They describe what the app's own
 * calculation did — never a model's rationale, and modeled fatigue is not a measurement of
 * readiness.
 */

/** The evidence that determined the pre-progression baseline. */
enum class LoadBaselineSource {
    RECENT_PERFORMANCE,
    MANUAL_PERSONAL_RECORD
}

/**
 * The baseline stage of a numeric load, captured where it is computed.
 *
 * [source] names the input that set [baselineKg]; the other eligible input is still recorded for
 * provenance. [recentEstimateKg] and [manualFloorKg] are the two eligible inputs, and [baselineKg] is
 * their effective maximum (the recent estimate, or the manual record when recent evidence was thin or
 * lower). A record is only produced when a baseline exists, so "no usable baseline" is reported
 * through [LoadUnavailableReason] instead.
 */
data class LoadBaselineExplanation(
    val source: LoadBaselineSource,
    /**
     * Session-balanced median recent external estimate: each session votes with its best qualifying
     * set and an even number of sessions averages the two middle votes. Null when the window held too
     * little evidence.
     */
    val recentEstimateKg: Double? = null,
    /** Qualifying non-warm-up external working sets counted inside the recent window. */
    val qualifyingSetCount: Int = 0,
    /** An eligible external manual record of at least three reps used as a floor, or null. */
    val manualFloorKg: Double? = null,
    /** The baseline before progression. */
    val baselineKg: Double
)

/** What the existing progression rule did to the baseline. */
enum class ProgressionOutcome {
    APPLIED,

    /** There is an accepted prescription but not yet a completed success streak. */
    NONE_NO_STREAK_YET,

    /**
     * The rule removed at least one increment for missed sessions and the result stayed at the
     * baseline, so the hold is a failure-driven correction rather than simply "too little evidence".
     */
    HELD_AFTER_MISSED_SESSIONS,
    NONE_NO_TYPED_PRESCRIPTION,
    PAUSED_DELOAD
}

/**
 * The progression stage: the thresholds involved and the applied rounded delta.
 *
 * [deltaKg] is an adjustment the rule applied, not a measured strength change, and it can later be
 * hidden by rounding or an equipment ceiling.
 */
data class LoadProgressionExplanation(
    val outcome: ProgressionOutcome,
    val increments: Int = 0,
    val deltaKg: Double = 0.0,
    val roundToKg: Double = ProgressionConfig.DEFAULT_ROUND_TO_KG,
    val successStreakSize: Int = ProgressionConfig.DEFAULT_SUCCESS_STREAK,
    val failureStreakSize: Int = ProgressionConfig.DEFAULT_FAILURE_STREAK
)

/**
 * A model-proposed number and each adjustment the app itself applied to it.
 *
 * The app never runs a model proposal through the rep-intensity conversion, so no conversion
 * parameters are recorded here — only the proposal the model supplied and the app's own operations.
 * Both bounds are recorded independently, because a proposal can be bound by progression and then
 * further capped by equipment.
 */
data class AiLoadExplanation(
    /** The number the model returned, before any app adjustment; null when it returned none. */
    val proposedKg: Double?,
    /**
     * True when the app accepted the proposal as this exercise's numeric load. False when the
     * proposal was unusable (out of range, withheld, not shared) or the exercise does not track an
     * external load, in which case the bounds below are null and not reported as applied.
     */
    val proposedAccepted: Boolean,
    /** True when the app scaled an accepted proposal for a deload week. */
    val deloadScaled: Boolean,
    val deloadIntensityScale: Double,
    /** The progression bound the app applied, or null when it had no bound for this exercise. */
    val progressionBoundKg: Double?,
    /** True when [progressionBoundKg] lowered the value. */
    val boundedByProgression: Boolean,
    /** The equipment ceiling that applies, whether or not it bound. */
    val equipmentCeilingKg: Double?,
    /** True when the ceiling lowered the value. */
    val cappedByEquipment: Boolean
)

/**
 * The observed conversion from an estimate to the prescribed working load.
 *
 * This describes the app's own deterministic conversion. A model proposal is described by
 * [AiLoadExplanation] instead: it is scaled and bounded, not converted through this curve, so none
 * of these parameters are attached to it.
 *
 * [uncappedWorkingKg] is the load before the equipment ceiling; a ceiling that applies but does not
 * bind is recorded with [equipmentCeilingBinds] false rather than described as limiting the target.
 */
data class LoadConversionExplanation(
    val reps: Int,
    /** The NSCA curve value at [reps], discounted by the RIR buffer. */
    val intensityFraction: Double,
    val deloadIntensityScale: Double = 1.0,
    val roundToKg: Double = SuggestedWeightConfig.DEFAULT_ROUND_TO_KG,
    /** The working load before the equipment ceiling, or null when no number was produced. */
    val uncappedWorkingKg: Double? = null,
    /** The equipment ceiling that applies here, whether or not it bound. */
    val equipmentCeilingKg: Double? = null,
    val equipmentCeilingBinds: Boolean = false
)

/** How fatigue affected the prescribed set count. */
enum class FatigueSetEffect { NONE, REDUCED }

/** How the final set count was reached from the goal's base count. */
data class SetCountExplanation(
    val baseSets: Int,
    val deloadApplied: Boolean,
    val deloadVolumeScale: Double,
    val fatigueEffect: FatigueSetEffect = FatigueSetEffect.NONE,
    val finalSets: Int
)

/** Why an exercise carries no automatic numeric load. */
enum class LoadUnavailableReason {
    /** The exercise's capability is not external resistance, so no number is generated. */
    NOT_LOAD_TRACKED,

    /** External, but no qualifying recent evidence and no usable manual record. */
    INSUFFICIENT_RECENT_EVIDENCE,

    /** A model plan whose workout data was not shared, so the app generates no number. */
    AI_DATA_NOT_SHARED,

    /** The model supplied no usable number for this exercise. */
    AI_PROPOSAL_OMITTED,

    /** The model's number was outside the accepted range and was dropped. */
    AI_PROPOSAL_REJECTED
}

/** One generated exercise's recorded target reasoning. */
data class ExerciseTargetExplanation(
    val baseline: LoadBaselineExplanation? = null,
    val progression: LoadProgressionExplanation? = null,
    /** The app's deterministic conversion; null for a model-proposed number. */
    val conversion: LoadConversionExplanation? = null,
    /** The model's proposal and the app's adjustments to it; null for a deterministic target. */
    val aiAdjustment: AiLoadExplanation? = null,
    val setCount: SetCountExplanation? = null,
    val unavailableReason: LoadUnavailableReason? = null
)

/** Plan-level context shared by its exercises. */
data class PlanExplanation(
    val engine: PlannerEngineId,
    val attribution: PlanAttribution,
    val isDeload: Boolean,
    val deloadVolumeScale: Double,
    val deloadIntensityScale: Double,
    /**
     * Candidates a deterministic pick skipped because raw targeted fatigue reached the skip
     * threshold. Accessories the ranking never reached are not listed; this is not a full ordering
     * audit.
     */
    val fatigueSkippedExerciseIds: List<String> = emptyList()
)

/** Baseline plus progression provenance for one exercise id, produced before any engine runs. */
data class LoadOriginExplanation(
    val baseline: LoadBaselineExplanation,
    val progression: LoadProgressionExplanation
)
