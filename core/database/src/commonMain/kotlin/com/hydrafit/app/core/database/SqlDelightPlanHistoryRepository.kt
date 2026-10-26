package com.hydrafit.app.core.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.hydrafit.app.core.domain.engine.AcceptedDay
import com.hydrafit.app.core.domain.engine.AcceptedExercise
import com.hydrafit.app.core.domain.engine.AcceptedPlan
import com.hydrafit.app.core.domain.engine.ArmCoverageUnmetReason
import com.hydrafit.app.core.domain.engine.ArmMuscleCoverage
import com.hydrafit.app.core.domain.engine.ExerciseTargetExplanation
import com.hydrafit.app.core.domain.engine.FrozenPlanExplanation
import com.hydrafit.app.core.domain.engine.PlanAttribution
import com.hydrafit.app.core.domain.engine.PlanHistoryRepository
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.SlotProgressionPolicyJson
import com.hydrafit.app.core.domain.engine.SlotSetTargets
import com.hydrafit.app.core.domain.engine.SlotSetTargetsJson
import com.hydrafit.app.core.domain.engine.SplitFocus
import com.hydrafit.app.core.domain.engine.VolumeExplanationStatus
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.domain.workout.LoadKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class SqlDelightPlanHistoryRepository(private val database: HydraFitDatabase) :
    PlanHistoryRepository {
    private val queries = database.planHistoryQueries
    private val volumeQueries = database.planVolumeExplanationQueries
    private val stateQueries = database.planVolumeExplanationStateQueries
    private val targetQueries = database.planTargetExplanationQueries
    private val targetStateQueries = database.planTargetExplanationStateQueries
    private val planWriter = AcceptedPlanWriter(database)

    /** Every table row a plan needs, loaded once per emission. */
    private data class PlanRows(
        val days: List<PlanHistoryDay>,
        val entries: List<PlanHistoryEntry>,
        val volume: List<PlanVolumeExplanation>,
        val volumeStates: List<PlanVolumeExplanationState>,
        val targets: List<PlanTargetExplanation>,
        val targetStates: List<PlanTargetExplanationState>
    )

    private fun planRowsFlow(): Flow<PlanRows> {
        val targets = combine(
            targetQueries.selectAll().asFlow().mapToList(Dispatchers.Default),
            targetStateQueries.selectAll().asFlow().mapToList(Dispatchers.Default)
        ) { explanations, states -> explanations to states }
        return combine(
            queries.selectAllDays().asFlow().mapToList(Dispatchers.Default),
            queries.selectAllEntries().asFlow().mapToList(Dispatchers.Default),
            volumeQueries.selectAll().asFlow().mapToList(Dispatchers.Default),
            stateQueries.selectAll().asFlow().mapToList(Dispatchers.Default),
            targets
        ) { days, entries, volume, volumeStates, targetRows ->
            PlanRows(
                days = days,
                entries = entries,
                volume = volume,
                volumeStates = volumeStates,
                targets = targetRows.first,
                targetStates = targetRows.second
            )
        }
    }

    override fun observeLatest(): Flow<AcceptedPlan?> {
        val plans = queries.selectLatestPlan().asFlow().mapToOneOrNull(Dispatchers.Default)
        return combine(plans, planRowsFlow()) { plan, rows ->
            plan?.toAcceptedPlan(rows)
        }
    }

    override fun observeHistory(): Flow<List<AcceptedPlan>> {
        val plans = queries.selectAllPlans().asFlow().mapToList(Dispatchers.Default)
        return combine(plans, planRowsFlow()) { planRows, rows ->
            planRows.map { it.toAcceptedPlan(rows) }
        }
    }

    override suspend fun latest(): AcceptedPlan? {
        val plan = queries.selectLatestPlan().executeAsOneOrNull() ?: return null
        return plan.toAcceptedPlan(
            PlanRows(
                days = queries.selectAllDays().executeAsList(),
                entries = queries.selectAllEntries().executeAsList(),
                volume = volumeQueries.selectAll().executeAsList(),
                volumeStates = stateQueries.selectAll().executeAsList(),
                targets = targetQueries.selectAll().executeAsList(),
                targetStates = targetStateQueries.selectAll().executeAsList()
            )
        )
    }

    override suspend fun accept(plan: AcceptedPlan) {
        queries.transaction {
            planWriter.insert(plan)
        }
    }

    override suspend fun substitute(
        planId: Long,
        dayIndex: Int,
        position: Int,
        newExerciseId: String,
        newExerciseName: String,
        newWeightKg: Double?,
        newLoadCapability: ExerciseLoadCapability,
        newLoadKind: LoadKind
    ) {
        substituteWithProvenance(
            planId = planId,
            dayIndex = dayIndex,
            position = position,
            newExerciseId = newExerciseId,
            newExerciseName = newExerciseName,
            newWeightKg = newWeightKg,
            newLoadCapability = newLoadCapability,
            newLoadKind = newLoadKind,
            setTargets = null,
            explanation = null
        )
    }

    override suspend fun substituteWithProvenance(
        planId: Long,
        dayIndex: Int,
        position: Int,
        newExerciseId: String,
        newExerciseName: String,
        newWeightKg: Double?,
        newLoadCapability: ExerciseLoadCapability,
        newLoadKind: LoadKind,
        setTargets: SlotSetTargets?,
        explanation: ExerciseTargetExplanation?
    ) {
        queries.transaction {
            val updated = queries.updateEntryExerciseIdAtPosition(
                newExerciseId = newExerciseId,
                newExerciseName = newExerciseName,
                newWeightKg = newWeightKg,
                newLoadCapability = newLoadCapability.name,
                newLoadKind = newLoadKind.name,
                setTargets = setTargets?.let(SlotSetTargetsJson::encode),
                position = position.toLong(),
                planId = planId,
                dayIndex = dayIndex.toLong()
            ).value
            // Only a real slot replacement invalidates the frozen volume assessment and replaces the
            // slot's recorded reasoning. A missing day/position is a no-op that must leave the plan
            // and its metadata untouched. The reasoning is written in this same transaction, so a
            // slot is never persisted with a load whose provenance failed to write.
            if (updated > 0L) {
                volumeQueries.deleteForPlan(planId)
                stateQueries.invalidateForSlot(planId, dayIndex.toLong(), position.toLong())
                targetQueries.deleteExplanationForSlot(planId, dayIndex.toLong(), position.toLong())
                explanation?.let { recorded ->
                    targetQueries.insertRecorded(
                        planId = planId,
                        dayIndex = dayIndex.toLong(),
                        position = position.toLong(),
                        explanation = recorded
                    )
                }
            }
        }
    }

    override suspend fun setMeasure(
        planId: Long,
        dayIndex: Int,
        position: Int,
        reps: Int?,
        durationSeconds: Int?,
        distanceMeters: Int?
    ) {
        queries.transaction {
            // A missing day/position updates no row and is a no-op, leaving the plan untouched.
            queries.updateEntryMeasureAtPosition(
                reps = reps?.toLong(),
                durationSeconds = durationSeconds?.toLong(),
                distanceMeters = distanceMeters?.toLong(),
                position = position.toLong(),
                planId = planId,
                dayIndex = dayIndex.toLong()
            )
        }
    }

    override suspend fun delete(planId: Long) {
        queries.transaction {
            // An activation keeps its snapshot but loses the now-dangling source-plan provenance.
            database.trainingScheduleQueries.clearActivationSourcePlan(planId)
            queries.deleteEntriesForPlan(planId)
            queries.deleteDaysForPlan(planId)
            volumeQueries.deleteForPlan(planId)
            stateQueries.deleteForPlan(planId)
            targetQueries.deleteExplanationsForPlan(planId)
            targetStateQueries.deleteForPlan(planId)
            queries.deletePlan(planId)
        }
    }

    override suspend fun clear() {
        queries.transaction {
            database.trainingScheduleQueries.clearAllActivationSourcePlans()
            queries.deleteAllEntries()
            queries.deleteAllDays()
            volumeQueries.deleteAll()
            stateQueries.deleteAll()
            targetQueries.deleteAll()
            targetStateQueries.deleteAll()
            queries.deleteAllPlans()
        }
    }

    private fun PlanHistory.toAcceptedPlan(rows: PlanRows): AcceptedPlan {
        val entriesByDay = rows.entries.groupBy { it.dayId }
        val explanationsBySlot = rows.targets
            .filter { it.planId == id }
            .associateBy { it.dayIndex.toInt() to it.position.toInt() }
        // A slot's reasons are only meaningful with their plan-level context. Recording one without
        // the other is a write invariant (see AcceptedPlanWriter), and this read stays consistent
        // with it rather than surfacing slot reasons for a plan that reads as legacy.
        val recordedPlan = rows.targetStates.firstOrNull { it.planId == id }?.toFrozenExplanation()
        val days = rows.days
            .filter { it.planId == id }
            .sortedBy { it.dayIndex }
            .map { day ->
                AcceptedDay(
                    dayIndex = day.dayIndex.toInt(),
                    focus = day.focus.toFocus(),
                    exercises = entriesByDay[day.id].orEmpty()
                        .sortedBy { it.position }
                        .map { row ->
                            AcceptedExercise(
                                exerciseId = row.exerciseId,
                                sets = row.sets.toInt(),
                                reps = row.reps?.toInt(),
                                durationSeconds = row.durationSeconds?.toInt(),
                                name = row.exerciseName,
                                movementPattern = decodeMovementPattern(row.movementPattern),
                                suggestedWeightKg = row.suggestedWeightKg,
                                loadCapability = decodeLoadCapability(row.loadCapability),
                                loadKind = decodeLoadKind(row.loadKind),
                                explanation = if (recordedPlan == null) {
                                    null
                                } else {
                                    explanationsBySlot[
                                        day.dayIndex.toInt() to row.position.toInt()
                                    ]?.toExplanation()
                                },
                                progressionPolicy = row.progressionPolicy
                                    ?.let(SlotProgressionPolicyJson::decode),
                                setTargets = row.setTargets?.let(SlotSetTargetsJson::decode),
                                superset = row.superset?.let(
                                    com.hydrafit.app.core.domain.routine.SupersetJson::decode
                                ),
                                distanceMeters = row.distanceMeters?.toInt()
                            )
                        }
                )
            }
        val explanationState = rows.volumeStates.firstOrNull { it.planId == id }
        val targetState = rows.targetStates.firstOrNull { it.planId == id }
        return AcceptedPlan(
            engine = engineId.toEngineId(),
            acceptedAtMillis = acceptedAt,
            days = days,
            weekNumber = weekNumber.toInt(),
            cycleNumber = cycleNumber.toInt(),
            id = id,
            armCoverage = rows.volume
                .filter { it.planId == id }
                .mapNotNull { it.toCoverage() },
            volumeAttribution = rows.volume
                .firstOrNull { it.planId == id }
                ?.attribution
                ?.let { name -> PlanAttribution.entries.firstOrNull { it.name == name } },
            volumeExplanationInvalidated = explanationState?.status ==
                VolumeExplanationStatus.INVALIDATED_BY_SUBSTITUTION.name,
            explanation = recordedPlan
        )
    }

    private fun PlanTargetExplanation.toExplanation(): ExerciseTargetExplanation? =
        decodeTargetExplanation(
            explanationVersion = explanationVersion,
            baselineSource = baselineSource,
            baselineRecentEstimateKg = baselineRecentEstimateKg,
            baselineQualifyingSetCount = baselineQualifyingSetCount,
            baselineManualFloorKg = baselineManualFloorKg,
            baselineKg = baselineKg,
            progressionOutcome = progressionOutcome,
            progressionIncrements = progressionIncrements,
            progressionDeltaKg = progressionDeltaKg,
            progressionRoundToKg = progressionRoundToKg,
            progressionSuccessStreakSize = progressionSuccessStreakSize,
            progressionFailureStreakSize = progressionFailureStreakSize,
            conversionReps = conversionReps,
            conversionIntensityFraction = conversionIntensityFraction,
            conversionDeloadIntensityScale = conversionDeloadIntensityScale,
            conversionRoundToKg = conversionRoundToKg,
            conversionUncappedWorkingKg = conversionUncappedWorkingKg,
            conversionEquipmentCeilingKg = conversionEquipmentCeilingKg,
            conversionEquipmentCeilingBinds = conversionEquipmentCeilingBinds,
            aiProposedKg = aiProposedKg,
            aiProposedAccepted = aiProposedAccepted,
            aiDeloadScaled = aiDeloadScaled,
            aiDeloadIntensityScale = aiDeloadIntensityScale,
            aiProgressionBoundKg = aiProgressionBoundKg,
            aiBoundedByProgression = aiBoundedByProgression,
            aiEquipmentCeilingKg = aiEquipmentCeilingKg,
            aiCappedByEquipment = aiCappedByEquipment,
            setBaseSets = setBaseSets,
            setDeloadApplied = setDeloadApplied,
            setDeloadVolumeScale = setDeloadVolumeScale,
            setFatigueEffect = setFatigueEffect,
            setFinalSets = setFinalSets,
            unavailableReason = unavailableReason
        )

    private fun PlanTargetExplanationState.toFrozenExplanation(): FrozenPlanExplanation? {
        if (explanationVersion != FrozenPlanExplanation.EXPLANATION_VERSION.toLong()) return null
        val resolved = PlanAttribution.entries.firstOrNull { it.name == attribution } ?: return null
        return FrozenPlanExplanation(
            attribution = resolved,
            isDeload = isDeload != 0L,
            deloadVolumeScale = deloadVolumeScale,
            deloadIntensityScale = deloadIntensityScale,
            version = explanationVersion.toInt()
        )
    }

    private fun PlanVolumeExplanation.toCoverage(): ArmMuscleCoverage? {
        val resolved = MuscleGroup.entries.firstOrNull { it.name == muscle } ?: return null
        val storedReason = unmetReason?.let { name ->
            ArmCoverageUnmetReason.entries.firstOrNull { it.name == name }
        }
        // Compatibility for pre-corrective AI rows: retained numbers remain frozen, but an AI
        // assessment must never be rendered as evidence that a deterministic skip branch ran.
        val reason = if (attribution == PlanAttribution.AI_GENERATED.name &&
            storedReason == ArmCoverageUnmetReason.ALL_COMPATIBLE_CANDIDATES_SKIPPED_FOR_FATIGUE
        ) {
            ArmCoverageUnmetReason.COMPATIBLE_CANDIDATES_ABOVE_SORENESS_THRESHOLD
        } else {
            storedReason
        }
        return ArmMuscleCoverage(
            muscle = resolved,
            targetSets = targetSets.toInt(),
            isTargetEnforced = isTargetEnforced != 0L,
            directIsolationSets = directIsolationSets.toInt(),
            estimatedOtherInvolvementCredits = estimatedOtherInvolvementCredits,
            unmetReason = reason
        )
    }

    private fun String.toFocus(): SplitFocus =
        SplitFocus.entries.firstOrNull { it.name == this } ?: SplitFocus.FULL_BODY

    private fun String.toEngineId(): PlannerEngineId =
        PlannerEngineId.entries.firstOrNull { it.name == this } ?: PlannerEngineId.DETERMINISTIC
}
