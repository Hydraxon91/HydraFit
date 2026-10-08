package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.engine.AcceptedPlan
import com.hydrafit.app.core.domain.engine.VolumeExplanationStatus

/** Shared insertion inside the caller's transaction (plain acceptance or accept-and-activate). */
internal class AcceptedPlanWriter(private val database: HydraFitDatabase) {
    fun insert(plan: AcceptedPlan): Long {
        val queries = database.planHistoryQueries
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
                    reps = exercise.reps.toLong(),
                    exerciseName = exercise.name,
                    movementPattern = exercise.movementPattern.name,
                    suggestedWeightKg = exercise.suggestedWeightKg,
                    loadCapability = exercise.loadCapability.name,
                    loadKind = exercise.loadKind.name
                )
            }
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
