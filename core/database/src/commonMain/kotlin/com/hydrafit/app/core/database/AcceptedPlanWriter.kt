package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.engine.AcceptedPlan
import com.hydrafit.app.core.domain.engine.ExerciseTargetExplanation
import com.hydrafit.app.core.domain.engine.SlotProgressionPolicyJson
import com.hydrafit.app.core.domain.engine.SlotSetTargetsJson
import com.hydrafit.app.core.domain.engine.VolumeExplanationStatus

/** Shared insertion inside the caller's transaction (plain acceptance or accept-and-activate). */
internal class AcceptedPlanWriter(private val database: HydraFitDatabase) {
    fun insert(plan: AcceptedPlan): Long {
        require(
            plan.explanation != null ||
                plan.days.none { day -> day.exercises.any { it.explanation != null } }
        ) {
            "Recorded slot reasoning requires a plan-level explanation record"
        }
        val queries = database.planHistoryQueries
        plan.days.forEach { day ->
            com.hydrafit.app.core.domain.routine.validateSupersets(
                day.exercises.map { it.exerciseId to it.superset }
            )
        }
        queries.insertPlan(
            plan.engine.name,
            plan.acceptedAtMillis,
            plan.weekNumber.toLong(),
            plan.cycleNumber.toLong()
        )
        val planId = queries.lastInsertedPlanId().executeAsOne()
        plan.days.forEach { day ->
            queries.insertDay(planId, day.dayIndex.toLong(), day.focus.name)
            val dayId = queries.lastInsertedPlanId().executeAsOne()
            day.exercises.forEachIndexed { position, exercise ->
                queries.insertEntry(
                    dayId = dayId,
                    position = position.toLong(),
                    exerciseId = exercise.exerciseId,
                    sets = exercise.sets.toLong(),
                    reps = exercise.reps?.toLong(),
                    durationSeconds = exercise.durationSeconds?.toLong(),
                    exerciseName = exercise.name,
                    movementPattern = exercise.movementPattern.name,
                    suggestedWeightKg = exercise.suggestedWeightKg,
                    loadCapability = exercise.loadCapability.name,
                    loadKind = exercise.loadKind.name,
                    progressionPolicy = exercise.progressionPolicy
                        ?.let(SlotProgressionPolicyJson::encode),
                    setTargets = exercise.setTargets?.let(SlotSetTargetsJson::encode),
                    distanceMeters = exercise.distanceMeters?.toLong(),
                    forceNewtons = exercise.forceNewtons
                )
                queries.setEntrySuperset(
                    superset = exercise.superset?.let(
                        com.hydrafit.app.core.domain.routine.SupersetJson::encode
                    ),
                    dayId = dayId,
                    position = position.toLong()
                )
                queries.setPlanEntryCluster(
                    cluster = exercise.cluster?.let(
                        com.hydrafit.app.core.domain.routine.ClusterPrescriptionJson::encode
                    ),
                    dayId = dayId,
                    position = position.toLong()
                )
                val recorded = exercise.explanation ?: return@forEachIndexed
                database.planTargetExplanationQueries.insertRecorded(
                    planId = planId,
                    dayIndex = day.dayIndex.toLong(),
                    position = position.toLong(),
                    explanation = recorded
                )
            }
        }
        plan.explanation?.let { frozen ->
            database.planTargetExplanationStateQueries.upsert(
                planId = planId,
                attribution = frozen.attribution.name,
                isDeload = if (frozen.isDeload) 1L else 0L,
                deloadVolumeScale = frozen.deloadVolumeScale,
                deloadIntensityScale = frozen.deloadIntensityScale,
                explanationVersion = frozen.version.toLong()
            )
        }
        if (plan.volumeExplanationStatus != VolumeExplanationStatus.ABSENT) {
            val version = requireNotNull(plan.volumeAssessmentVersion)
            database.planVolumeExplanationStateQueries.upsert(
                planId,
                plan.volumeExplanationStatus.name,
                version.toLong()
            )
        }
        if (plan.volumeExplanationStatus == VolumeExplanationStatus.AVAILABLE) {
            val attribution = requireNotNull(plan.volumeAttribution)
            require(plan.armCoverage.isNotEmpty()) {
                "Available explanation requires an assessment"
            }
            plan.armCoverage.forEach { coverage ->
                database.planVolumeExplanationQueries.insert(
                    planId = planId,
                    muscle = coverage.muscle.name,
                    targetSets = coverage.targetSets.toLong(),
                    isTargetEnforced = if (coverage.isTargetEnforced) 1L else 0L,
                    directIsolationSets = coverage.directIsolationSets.toLong(),
                    estimatedOtherInvolvementCredits = coverage.estimatedOtherInvolvementCredits,
                    unmetReason = coverage.unmetReason?.name,
                    attribution = attribution.name
                )
            }
        }
        return planId
    }
}

/**
 * Writes one slot's recorded reasoning inside the caller's transaction. Shared by plan acceptance
 * and accepted-plan substitution so both persist the same shape; the caller owns the transaction.
 */
internal fun PlanTargetExplanationQueries.insertRecorded(
    planId: Long,
    dayIndex: Long,
    position: Long,
    explanation: ExerciseTargetExplanation
) {
    val columns = explanation.toColumns()
    insertExplanation(
        planId = planId,
        dayIndex = dayIndex,
        position = position,
        explanationVersion = columns.explanationVersion,
        baselineSource = columns.baselineSource,
        baselineRecentEstimateKg = columns.baselineRecentEstimateKg,
        baselineQualifyingSetCount = columns.baselineQualifyingSetCount,
        baselineManualFloorKg = columns.baselineManualFloorKg,
        baselineKg = columns.baselineKg,
        progressionOutcome = columns.progressionOutcome,
        progressionIncrements = columns.progressionIncrements,
        progressionDeltaKg = columns.progressionDeltaKg,
        progressionRoundToKg = columns.progressionRoundToKg,
        progressionSuccessStreakSize = columns.progressionSuccessStreakSize,
        progressionFailureStreakSize = columns.progressionFailureStreakSize,
        conversionReps = columns.conversionReps,
        conversionIntensityFraction = columns.conversionIntensityFraction,
        conversionDeloadIntensityScale = columns.conversionDeloadIntensityScale,
        conversionRoundToKg = columns.conversionRoundToKg,
        conversionUncappedWorkingKg = columns.conversionUncappedWorkingKg,
        conversionEquipmentCeilingKg = columns.conversionEquipmentCeilingKg,
        conversionEquipmentCeilingBinds = columns.conversionEquipmentCeilingBinds,
        aiProposedKg = columns.aiProposedKg,
        aiProposedAccepted = columns.aiProposedAccepted,
        aiDeloadScaled = columns.aiDeloadScaled,
        aiDeloadIntensityScale = columns.aiDeloadIntensityScale,
        aiProgressionBoundKg = columns.aiProgressionBoundKg,
        aiBoundedByProgression = columns.aiBoundedByProgression,
        aiEquipmentCeilingKg = columns.aiEquipmentCeilingKg,
        aiCappedByEquipment = columns.aiCappedByEquipment,
        setBaseSets = columns.setBaseSets,
        setDeloadApplied = columns.setDeloadApplied,
        setDeloadVolumeScale = columns.setDeloadVolumeScale,
        setFatigueEffect = columns.setFatigueEffect,
        setFinalSets = columns.setFinalSets,
        unavailableReason = columns.unavailableReason
    )
}
