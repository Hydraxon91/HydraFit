package com.hydrafit.app.core.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.hydrafit.app.core.domain.engine.AcceptedDay
import com.hydrafit.app.core.domain.engine.AcceptedExercise
import com.hydrafit.app.core.domain.engine.AcceptedPlan
import com.hydrafit.app.core.domain.engine.ArmCoverageUnmetReason
import com.hydrafit.app.core.domain.engine.ArmMuscleCoverage
import com.hydrafit.app.core.domain.engine.PlanAttribution
import com.hydrafit.app.core.domain.engine.PlanHistoryRepository
import com.hydrafit.app.core.domain.engine.PlannerEngineId
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
    private val planWriter = AcceptedPlanWriter(database)

    override fun observeLatest(): Flow<AcceptedPlan?> {
        val plans = queries.selectLatestPlan().asFlow().mapToOneOrNull(Dispatchers.Default)
        val days = queries.selectAllDays().asFlow().mapToList(Dispatchers.Default)
        val entries = queries.selectAllEntries().asFlow().mapToList(Dispatchers.Default)
        val explanations = volumeQueries.selectAll().asFlow().mapToList(Dispatchers.Default)
        val states = stateQueries.selectAll().asFlow().mapToList(Dispatchers.Default)
        return combine(plans, days, entries, explanations, states) {
                plan,
                allDays,
                allEntries,
                allExplanations,
                allStates
            ->
            plan?.toAcceptedPlan(allDays, allEntries, allExplanations, allStates)
        }
    }

    override fun observeHistory(): Flow<List<AcceptedPlan>> {
        val plans = queries.selectAllPlans().asFlow().mapToList(Dispatchers.Default)
        val days = queries.selectAllDays().asFlow().mapToList(Dispatchers.Default)
        val entries = queries.selectAllEntries().asFlow().mapToList(Dispatchers.Default)
        val explanations = volumeQueries.selectAll().asFlow().mapToList(Dispatchers.Default)
        val states = stateQueries.selectAll().asFlow().mapToList(Dispatchers.Default)
        return combine(plans, days, entries, explanations, states) {
                planRows,
                allDays,
                allEntries,
                allExplanations,
                allStates
            ->
            planRows.map { it.toAcceptedPlan(allDays, allEntries, allExplanations, allStates) }
        }
    }

    override suspend fun latest(): AcceptedPlan? {
        val plan = queries.selectLatestPlan().executeAsOneOrNull() ?: return null
        return plan.toAcceptedPlan(
            queries.selectAllDays().executeAsList(),
            queries.selectAllEntries().executeAsList(),
            volumeQueries.selectAll().executeAsList(),
            stateQueries.selectAll().executeAsList()
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
        queries.transaction {
            queries.updateEntryExerciseIdAtPosition(
                newExerciseId = newExerciseId,
                newExerciseName = newExerciseName,
                newWeightKg = newWeightKg,
                newLoadCapability = newLoadCapability.name,
                newLoadKind = newLoadKind.name,
                position = position.toLong(),
                planId = planId,
                dayIndex = dayIndex.toLong()
            )
            // A manual substitution invalidates the frozen volume assessment: it no longer describes
            // the plan, and it must not be silently reconstructed from today's catalog.
            volumeQueries.deleteForPlan(planId)
            stateQueries.invalidateForSlot(planId, dayIndex.toLong(), position.toLong())
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
            queries.deleteAllPlans()
        }
    }

    private fun PlanHistory.toAcceptedPlan(
        allDays: List<PlanHistoryDay>,
        allEntries: List<PlanHistoryEntry>,
        allExplanations: List<PlanVolumeExplanation>,
        allStates: List<PlanVolumeExplanationState>
    ): AcceptedPlan {
        val entriesByDay = allEntries.groupBy { it.dayId }
        val days = allDays
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
                                reps = row.reps.toInt(),
                                name = row.exerciseName,
                                movementPattern = decodeMovementPattern(row.movementPattern),
                                suggestedWeightKg = row.suggestedWeightKg,
                                loadCapability = decodeLoadCapability(row.loadCapability),
                                loadKind = decodeLoadKind(row.loadKind)
                            )
                        }
                )
            }
        val explanationState = allStates.firstOrNull { it.planId == id }
        return AcceptedPlan(
            engine = engineId.toEngineId(),
            acceptedAtMillis = acceptedAt,
            days = days,
            weekNumber = weekNumber.toInt(),
            cycleNumber = cycleNumber.toInt(),
            id = id,
            armCoverage = allExplanations
                .filter { it.planId == id }
                .mapNotNull { it.toCoverage() },
            volumeAttribution = allExplanations
                .firstOrNull { it.planId == id }
                ?.attribution
                ?.let { name -> PlanAttribution.entries.firstOrNull { it.name == name } },
            volumeExplanationInvalidated = explanationState?.status ==
                VolumeExplanationStatus.INVALIDATED_BY_SUBSTITUTION.name
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
