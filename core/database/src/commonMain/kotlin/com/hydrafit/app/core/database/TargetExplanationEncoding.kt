package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.engine.AiLoadExplanation
import com.hydrafit.app.core.domain.engine.ExerciseTargetExplanation
import com.hydrafit.app.core.domain.engine.FatigueSetEffect
import com.hydrafit.app.core.domain.engine.FrozenPlanExplanation
import com.hydrafit.app.core.domain.engine.LoadBaselineExplanation
import com.hydrafit.app.core.domain.engine.LoadBaselineSource
import com.hydrafit.app.core.domain.engine.LoadConversionExplanation
import com.hydrafit.app.core.domain.engine.LoadProgressionExplanation
import com.hydrafit.app.core.domain.engine.LoadUnavailableReason
import com.hydrafit.app.core.domain.engine.ProgressionOutcome
import com.hydrafit.app.core.domain.engine.SetCountExplanation

/**
 * The nullable SQL columns for one recorded target explanation. A slot with no recorded reasoning
 * decodes to null; the encoding never writes a default for an absent group.
 */
internal data class TargetExplanationColumns(
    val explanationVersion: Long?,
    val baselineSource: String?,
    val baselineRecentEstimateKg: Double?,
    val baselineQualifyingSetCount: Long?,
    val baselineManualFloorKg: Double?,
    val baselineKg: Double?,
    val progressionOutcome: String?,
    val progressionIncrements: Long?,
    val progressionDeltaKg: Double?,
    val progressionRoundToKg: Double?,
    val progressionSuccessStreakSize: Long?,
    val progressionFailureStreakSize: Long?,
    val conversionReps: Long?,
    val conversionIntensityFraction: Double?,
    val conversionDeloadIntensityScale: Double?,
    val conversionRoundToKg: Double?,
    val conversionUncappedWorkingKg: Double?,
    val conversionEquipmentCeilingKg: Double?,
    val conversionEquipmentCeilingBinds: Long?,
    val aiProposedKg: Double?,
    val aiProposedAccepted: Long?,
    val aiDeloadScaled: Long?,
    val aiDeloadIntensityScale: Double?,
    val aiProgressionBoundKg: Double?,
    val aiBoundedByProgression: Long?,
    val aiEquipmentCeilingKg: Double?,
    val aiCappedByEquipment: Long?,
    val setBaseSets: Long?,
    val setDeloadApplied: Long?,
    val setDeloadVolumeScale: Double?,
    val setFatigueEffect: String?,
    val setFinalSets: Long?,
    val unavailableReason: String?
)

internal val EMPTY_TARGET_EXPLANATION_COLUMNS = TargetExplanationColumns(
    explanationVersion = null,
    baselineSource = null,
    baselineRecentEstimateKg = null,
    baselineQualifyingSetCount = null,
    baselineManualFloorKg = null,
    baselineKg = null,
    progressionOutcome = null,
    progressionIncrements = null,
    progressionDeltaKg = null,
    progressionRoundToKg = null,
    progressionSuccessStreakSize = null,
    progressionFailureStreakSize = null,
    conversionReps = null,
    conversionIntensityFraction = null,
    conversionDeloadIntensityScale = null,
    conversionRoundToKg = null,
    conversionUncappedWorkingKg = null,
    conversionEquipmentCeilingKg = null,
    conversionEquipmentCeilingBinds = null,
    aiProposedKg = null,
    aiProposedAccepted = null,
    aiDeloadScaled = null,
    aiDeloadIntensityScale = null,
    aiProgressionBoundKg = null,
    aiBoundedByProgression = null,
    aiEquipmentCeilingKg = null,
    aiCappedByEquipment = null,
    setBaseSets = null,
    setDeloadApplied = null,
    setDeloadVolumeScale = null,
    setFatigueEffect = null,
    setFinalSets = null,
    unavailableReason = null
)

internal fun ExerciseTargetExplanation?.toColumns(): TargetExplanationColumns {
    val explanation = this ?: return EMPTY_TARGET_EXPLANATION_COLUMNS
    val baseline = explanation.baseline
    val progression = explanation.progression
    val conversion = explanation.conversion
    val ai = explanation.aiAdjustment
    val setCount = explanation.setCount
    return TargetExplanationColumns(
        explanationVersion = FrozenPlanExplanation.EXPLANATION_VERSION.toLong(),
        baselineSource = baseline?.source?.name,
        baselineRecentEstimateKg = baseline?.recentEstimateKg,
        baselineQualifyingSetCount = baseline?.qualifyingSetCount?.toLong(),
        baselineManualFloorKg = baseline?.manualFloorKg,
        baselineKg = baseline?.baselineKg,
        progressionOutcome = progression?.outcome?.name,
        progressionIncrements = progression?.increments?.toLong(),
        progressionDeltaKg = progression?.deltaKg,
        progressionRoundToKg = progression?.roundToKg,
        progressionSuccessStreakSize = progression?.successStreakSize?.toLong(),
        progressionFailureStreakSize = progression?.failureStreakSize?.toLong(),
        conversionReps = conversion?.reps?.toLong(),
        conversionIntensityFraction = conversion?.intensityFraction,
        conversionDeloadIntensityScale = conversion?.deloadIntensityScale,
        conversionRoundToKg = conversion?.roundToKg,
        conversionUncappedWorkingKg = conversion?.uncappedWorkingKg,
        conversionEquipmentCeilingKg = conversion?.equipmentCeilingKg,
        conversionEquipmentCeilingBinds = conversion?.equipmentCeilingBinds?.toSqlLong(),
        aiProposedKg = ai?.proposedKg,
        aiProposedAccepted = ai?.proposedAccepted?.toSqlLong(),
        aiDeloadScaled = ai?.deloadScaled?.toSqlLong(),
        aiDeloadIntensityScale = ai?.deloadIntensityScale,
        aiProgressionBoundKg = ai?.progressionBoundKg,
        aiBoundedByProgression = ai?.boundedByProgression?.toSqlLong(),
        aiEquipmentCeilingKg = ai?.equipmentCeilingKg,
        aiCappedByEquipment = ai?.cappedByEquipment?.toSqlLong(),
        setBaseSets = setCount?.baseSets?.toLong(),
        setDeloadApplied = setCount?.deloadApplied?.toSqlLong(),
        setDeloadVolumeScale = setCount?.deloadVolumeScale,
        setFatigueEffect = setCount?.fatigueEffect?.name,
        setFinalSets = setCount?.finalSets?.toLong(),
        unavailableReason = explanation.unavailableReason?.name
    )
}

/** Decodes a stored slot's reasoning, or null when no explanation was recorded for it. */
@Suppress("LongParameterList")
internal fun decodeTargetExplanation(
    explanationVersion: Long?,
    baselineSource: String?,
    baselineRecentEstimateKg: Double?,
    baselineQualifyingSetCount: Long?,
    baselineManualFloorKg: Double?,
    baselineKg: Double?,
    progressionOutcome: String?,
    progressionIncrements: Long?,
    progressionDeltaKg: Double?,
    progressionRoundToKg: Double?,
    progressionSuccessStreakSize: Long?,
    progressionFailureStreakSize: Long?,
    conversionReps: Long?,
    conversionIntensityFraction: Double?,
    conversionDeloadIntensityScale: Double?,
    conversionRoundToKg: Double?,
    conversionUncappedWorkingKg: Double?,
    conversionEquipmentCeilingKg: Double?,
    conversionEquipmentCeilingBinds: Long?,
    aiProposedKg: Double?,
    aiProposedAccepted: Long?,
    aiDeloadScaled: Long?,
    aiDeloadIntensityScale: Double?,
    aiProgressionBoundKg: Double?,
    aiBoundedByProgression: Long?,
    aiEquipmentCeilingKg: Double?,
    aiCappedByEquipment: Long?,
    setBaseSets: Long?,
    setDeloadApplied: Long?,
    setDeloadVolumeScale: Double?,
    setFatigueEffect: String?,
    setFinalSets: Long?,
    unavailableReason: String?
): ExerciseTargetExplanation? {
    if (explanationVersion == null) return null
    // A recorded slot must decode completely. An incomplete or unreadable record is reported as
    // unavailable rather than reconstructed with plausible defaults, so a fabricated reason can
    // never be shown.
    val baseline = if (baselineKg == null) {
        if (baselineSource != null ||
            baselineRecentEstimateKg != null ||
            baselineManualFloorKg != null ||
            baselineQualifyingSetCount != null
        ) {
            return null
        }
        null
    } else {
        LoadBaselineExplanation(
            source = enumValueOrNull(baselineSource, LoadBaselineSource.entries) ?: return null,
            recentEstimateKg = baselineRecentEstimateKg,
            qualifyingSetCount = baselineQualifyingSetCount?.toInt() ?: return null,
            manualFloorKg = baselineManualFloorKg,
            baselineKg = baselineKg
        )
    }
    val progression = if (progressionOutcome == null) {
        if (progressionIncrements != null ||
            progressionDeltaKg != null ||
            progressionRoundToKg != null
        ) {
            return null
        }
        null
    } else {
        LoadProgressionExplanation(
            outcome = enumValueOrNull(
                progressionOutcome,
                ProgressionOutcome.entries
            ) ?: return null,
            increments = progressionIncrements?.toInt() ?: return null,
            deltaKg = progressionDeltaKg ?: return null,
            roundToKg = progressionRoundToKg ?: return null,
            successStreakSize = progressionSuccessStreakSize?.toInt() ?: return null,
            failureStreakSize = progressionFailureStreakSize?.toInt() ?: return null
        )
    }
    val conversion = if (conversionReps == null) {
        if (conversionIntensityFraction != null ||
            conversionDeloadIntensityScale != null ||
            conversionRoundToKg != null
        ) {
            return null
        }
        null
    } else {
        LoadConversionExplanation(
            reps = conversionReps.toInt(),
            intensityFraction = conversionIntensityFraction ?: return null,
            deloadIntensityScale = conversionDeloadIntensityScale ?: return null,
            roundToKg = conversionRoundToKg ?: return null,
            uncappedWorkingKg = conversionUncappedWorkingKg,
            equipmentCeilingKg = conversionEquipmentCeilingKg,
            equipmentCeilingBinds = conversionEquipmentCeilingBinds == 1L
        )
    }
    val aiPresent = aiProposedKg != null ||
        aiProposedAccepted != null ||
        aiDeloadScaled != null ||
        aiProgressionBoundKg != null
    val ai = if (!aiPresent) {
        null
    } else {
        AiLoadExplanation(
            proposedKg = aiProposedKg,
            proposedAccepted = aiProposedAccepted?.let { it == 1L } ?: return null,
            deloadScaled = aiDeloadScaled?.let { it == 1L } ?: return null,
            deloadIntensityScale = aiDeloadIntensityScale ?: return null,
            progressionBoundKg = aiProgressionBoundKg,
            boundedByProgression = aiBoundedByProgression?.let { it == 1L } ?: return null,
            equipmentCeilingKg = aiEquipmentCeilingKg,
            cappedByEquipment = aiCappedByEquipment?.let { it == 1L } ?: return null
        )
    }
    val setCountPresent = setFinalSets != null || setBaseSets != null || setFatigueEffect != null
    val setCount = if (!setCountPresent) {
        null
    } else {
        SetCountExplanation(
            baseSets = setBaseSets?.toInt() ?: return null,
            deloadApplied = setDeloadApplied?.let { it == 1L } ?: return null,
            deloadVolumeScale = setDeloadVolumeScale ?: return null,
            fatigueEffect =
            enumValueOrNull(setFatigueEffect, FatigueSetEffect.entries) ?: return null,
            finalSets = setFinalSets?.toInt() ?: return null
        )
    }
    val reason = if (unavailableReason == null) {
        null
    } else {
        enumValueOrNull(unavailableReason, LoadUnavailableReason.entries) ?: return null
    }
    if (baseline == null &&
        progression == null &&
        conversion == null &&
        ai == null &&
        setCount == null &&
        reason == null
    ) {
        return null
    }
    return ExerciseTargetExplanation(
        baseline = baseline,
        progression = progression,
        conversion = conversion,
        aiAdjustment = ai,
        setCount = setCount,
        unavailableReason = reason
    )
}

private fun Boolean.toSqlLong(): Long = if (this) 1L else 0L

private fun <T : Enum<T>> enumValueOrNull(name: String?, entries: List<T>): T? =
    name?.let { stored -> entries.firstOrNull { it.name == stored } }
