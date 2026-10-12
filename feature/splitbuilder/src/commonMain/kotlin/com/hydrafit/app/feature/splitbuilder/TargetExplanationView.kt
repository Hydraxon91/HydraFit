package com.hydrafit.app.feature.splitbuilder

import com.hydrafit.app.core.domain.engine.ExerciseTargetExplanation
import com.hydrafit.app.core.domain.engine.FatigueSetEffect
import com.hydrafit.app.core.domain.engine.LoadBaselineSource
import com.hydrafit.app.core.domain.engine.LoadUnavailableReason
import com.hydrafit.app.core.domain.engine.ProgressionOutcome

/**
 * How a recorded number reads, so this mapper stays free of units and formatting. The screen converts
 * kilograms through the user's selected unit; a plain number is rendered as recorded.
 */
internal enum class TargetExplanationValueKind { NONE, KILOGRAMS, NUMBER, PERCENT }

/** Every sentence the explanation disclosure can show, as a resource the screen resolves. */
internal enum class TargetExplanationReason {
    SUMMARY_AI_PROPOSAL,
    SUMMARY_CALCULATED,
    SUMMARY_SETS_ONLY,
    SUMMARY_UNAVAILABLE_NOT_LOAD_TRACKED,
    SUMMARY_UNAVAILABLE_INSUFFICIENT_EVIDENCE,
    SUMMARY_UNAVAILABLE_AI_NOT_SHARED,
    SUMMARY_UNAVAILABLE_AI_OMITTED,
    SUMMARY_UNAVAILABLE_AI_REJECTED,
    BASELINE_RECENT_ESTIMATE,
    BASELINE_MANUAL_FLOOR,
    BASELINE_QUALIFYING_SETS,
    BASELINE_EFFECTIVE,
    PROGRESSION_APPLIED,
    PROGRESSION_DELTA,
    PROGRESSION_SUCCESS_STREAK_SIZE,
    PROGRESSION_FAILURE_STREAK_SIZE,
    PROGRESSION_NONE_NO_STREAK,
    PROGRESSION_HELD_AFTER_MISSES,
    PROGRESSION_NONE_NO_PRESCRIPTION,
    PROGRESSION_PAUSED_DELOAD,
    CONVERSION_REPS,
    CONVERSION_INTENSITY,
    CONVERSION_DELOAD_SCALE,
    CONVERSION_ROUNDED_TO,
    CONVERSION_BEFORE_CEILING,
    CONVERSION_CEILING,
    CONVERSION_CEILING_BINDS,
    AI_PROPOSED,
    AI_ACCEPTED,
    AI_NOT_ACCEPTED,
    AI_BOUNDED_BY_PROGRESSION,
    AI_CAPPED_BY_EQUIPMENT,
    AI_EQUIPMENT_CEILING,
    AI_CEILING_NOT_BINDING,
    AI_DELOAD_SCALED,
    AI_DELOAD_SCALE,
    SETS_BASE,
    SETS_DELOAD_SCALE,
    SETS_REDUCED_FOR_FATIGUE,
    SETS_FINAL
}

/** One recorded fact: [reason] names it and [value] is its recorded number, when it has one. */
internal data class TargetExplanationLine(
    val reason: TargetExplanationReason,
    val value: Double? = null,
    val valueKind: TargetExplanationValueKind = TargetExplanationValueKind.NONE
)

/** The one-line summary of what produced a target, plus the facts the disclosure expands. */
internal data class TargetExplanationView(
    val summary: TargetExplanationReason,
    val lines: List<TargetExplanationLine>
)

/**
 * Builds the disclosure for one slot's [ExerciseTargetExplanation], or null when the slot recorded
 * nothing. Only the branches that ran are described, and an absent group produces no line, so the
 * screen never fills a gap with a plausible-looking reason. A fact that carries a number is listed
 * only when that number was recorded.
 */
internal fun ExerciseTargetExplanation.toExplanationView(): TargetExplanationView? {
    if (baseline == null &&
        progression == null &&
        conversion == null &&
        aiAdjustment == null &&
        setCount == null &&
        unavailableReason == null
    ) {
        return null
    }
    val summary = unavailableReason?.summaryReason() ?: when {
        aiAdjustment != null -> TargetExplanationReason.SUMMARY_AI_PROPOSAL
        baseline != null || progression != null || conversion != null ->
            TargetExplanationReason.SUMMARY_CALCULATED
        else -> TargetExplanationReason.SUMMARY_SETS_ONLY
    }
    val lines = buildList {
        baseline?.let { recorded ->
            if (recorded.source == LoadBaselineSource.RECENT_PERFORMANCE) {
                recorded.recentEstimateKg?.let {
                    add(kilograms(TargetExplanationReason.BASELINE_RECENT_ESTIMATE, it))
                }
            }
            recorded.manualFloorKg?.let {
                add(kilograms(TargetExplanationReason.BASELINE_MANUAL_FLOOR, it))
            }
            if (recorded.qualifyingSetCount > 0) {
                add(
                    count(
                        TargetExplanationReason.BASELINE_QUALIFYING_SETS,
                        recorded.qualifyingSetCount
                    )
                )
            }
            add(kilograms(TargetExplanationReason.BASELINE_EFFECTIVE, recorded.baselineKg))
        }
        progression?.let { recorded ->
            when (recorded.outcome) {
                ProgressionOutcome.APPLIED -> {
                    add(count(TargetExplanationReason.PROGRESSION_APPLIED, recorded.increments))
                    add(kilograms(TargetExplanationReason.PROGRESSION_DELTA, recorded.deltaKg))
                }
                ProgressionOutcome.NONE_NO_STREAK_YET ->
                    add(plain(TargetExplanationReason.PROGRESSION_NONE_NO_STREAK))
                ProgressionOutcome.HELD_AFTER_MISSED_SESSIONS ->
                    add(plain(TargetExplanationReason.PROGRESSION_HELD_AFTER_MISSES))
                ProgressionOutcome.NONE_NO_TYPED_PRESCRIPTION ->
                    add(plain(TargetExplanationReason.PROGRESSION_NONE_NO_PRESCRIPTION))
                ProgressionOutcome.PAUSED_DELOAD ->
                    add(plain(TargetExplanationReason.PROGRESSION_PAUSED_DELOAD))
            }
            add(
                count(
                    TargetExplanationReason.PROGRESSION_SUCCESS_STREAK_SIZE,
                    recorded.successStreakSize
                )
            )
            add(
                count(
                    TargetExplanationReason.PROGRESSION_FAILURE_STREAK_SIZE,
                    recorded.failureStreakSize
                )
            )
        }
        conversion?.let { recorded ->
            add(count(TargetExplanationReason.CONVERSION_REPS, recorded.reps))
            add(fraction(TargetExplanationReason.CONVERSION_INTENSITY, recorded.intensityFraction))
            if (recorded.deloadIntensityScale != 1.0) {
                add(
                    fraction(
                        TargetExplanationReason.CONVERSION_DELOAD_SCALE,
                        recorded.deloadIntensityScale
                    )
                )
            }
            add(kilograms(TargetExplanationReason.CONVERSION_ROUNDED_TO, recorded.roundToKg))
            recorded.uncappedWorkingKg?.let {
                add(kilograms(TargetExplanationReason.CONVERSION_BEFORE_CEILING, it))
            }
            recorded.equipmentCeilingKg?.let {
                add(kilograms(TargetExplanationReason.CONVERSION_CEILING, it))
            }
            if (recorded.equipmentCeilingBinds) {
                add(plain(TargetExplanationReason.CONVERSION_CEILING_BINDS))
            }
        }
        aiAdjustment?.let { recorded ->
            recorded.proposedKg?.let {
                add(kilograms(TargetExplanationReason.AI_PROPOSED, it))
            }
            add(
                plain(
                    if (recorded.proposedAccepted) {
                        TargetExplanationReason.AI_ACCEPTED
                    } else {
                        TargetExplanationReason.AI_NOT_ACCEPTED
                    }
                )
            )
            if (recorded.boundedByProgression) {
                recorded.progressionBoundKg?.let {
                    add(kilograms(TargetExplanationReason.AI_BOUNDED_BY_PROGRESSION, it))
                }
            }
            if (recorded.cappedByEquipment) {
                recorded.equipmentCeilingKg?.let {
                    add(kilograms(TargetExplanationReason.AI_CAPPED_BY_EQUIPMENT, it))
                }
            } else {
                recorded.equipmentCeilingKg?.let {
                    add(kilograms(TargetExplanationReason.AI_EQUIPMENT_CEILING, it))
                    add(plain(TargetExplanationReason.AI_CEILING_NOT_BINDING))
                }
            }
            if (recorded.deloadScaled) {
                add(plain(TargetExplanationReason.AI_DELOAD_SCALED))
                add(
                    fraction(TargetExplanationReason.AI_DELOAD_SCALE, recorded.deloadIntensityScale)
                )
            }
        }
        setCount?.let { recorded ->
            add(count(TargetExplanationReason.SETS_BASE, recorded.baseSets))
            if (recorded.deloadApplied) {
                add(
                    fraction(
                        TargetExplanationReason.SETS_DELOAD_SCALE,
                        recorded.deloadVolumeScale
                    )
                )
            }
            if (recorded.fatigueEffect == FatigueSetEffect.REDUCED) {
                add(plain(TargetExplanationReason.SETS_REDUCED_FOR_FATIGUE))
            }
            add(count(TargetExplanationReason.SETS_FINAL, recorded.finalSets))
        }
    }
    return TargetExplanationView(summary = summary, lines = lines)
}

private fun LoadUnavailableReason.summaryReason(): TargetExplanationReason = when (this) {
    LoadUnavailableReason.NOT_LOAD_TRACKED ->
        TargetExplanationReason.SUMMARY_UNAVAILABLE_NOT_LOAD_TRACKED
    LoadUnavailableReason.INSUFFICIENT_RECENT_EVIDENCE ->
        TargetExplanationReason.SUMMARY_UNAVAILABLE_INSUFFICIENT_EVIDENCE
    LoadUnavailableReason.AI_DATA_NOT_SHARED ->
        TargetExplanationReason.SUMMARY_UNAVAILABLE_AI_NOT_SHARED
    LoadUnavailableReason.AI_PROPOSAL_OMITTED ->
        TargetExplanationReason.SUMMARY_UNAVAILABLE_AI_OMITTED
    LoadUnavailableReason.AI_PROPOSAL_REJECTED ->
        TargetExplanationReason.SUMMARY_UNAVAILABLE_AI_REJECTED
}

private fun plain(reason: TargetExplanationReason) = TargetExplanationLine(reason)

private fun kilograms(reason: TargetExplanationReason, kg: Double) =
    TargetExplanationLine(reason, kg, TargetExplanationValueKind.KILOGRAMS)

private fun count(reason: TargetExplanationReason, value: Int) =
    TargetExplanationLine(reason, value.toDouble(), TargetExplanationValueKind.NUMBER)

private fun fraction(reason: TargetExplanationReason, value: Double) =
    TargetExplanationLine(reason, value, TargetExplanationValueKind.PERCENT)
