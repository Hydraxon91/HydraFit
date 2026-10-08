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

    override fun observeLatest(): Flow<AcceptedPlan?> {
        val plans = queries.selectLatestPlan().asFlow().mapToOneOrNull(Dispatchers.Default)
        val days = queries.selectAllDays().asFlow().mapToList(Dispatchers.Default)
        val entries = queries.selectAllEntries().asFlow().mapToList(Dispatchers.Default)
        val explanations = volumeQueries.selectAll().asFlow().mapToList(Dispatchers.Default)
        return combine(plans, days, entries, explanations) {
                plan,
                allDays,
                allEntries,
                allExplanations
            ->
            plan?.toAcceptedPlan(allDays, allEntries, allExplanations)
        }
    }

    override fun observeHistory(): Flow<List<AcceptedPlan>> {
        val plans = queries.selectAllPlans().asFlow().mapToList(Dispatchers.Default)
        val days = queries.selectAllDays().asFlow().mapToList(Dispatchers.Default)
        val entries = queries.selectAllEntries().asFlow().mapToList(Dispatchers.Default)
        val explanations = volumeQueries.selectAll().asFlow().mapToList(Dispatchers.Default)
        return combine(plans, days, entries, explanations) {
                planRows,
                allDays,
                allEntries,
                allExplanations
            ->
            planRows.map { it.toAcceptedPlan(allDays, allEntries, allExplanations) }
        }
    }

    override suspend fun latest(): AcceptedPlan? {
        val plan = queries.selectLatestPlan().executeAsOneOrNull() ?: return null
        return plan.toAcceptedPlan(
            queries.selectAllDays().executeAsList(),
            queries.selectAllEntries().executeAsList(),
            volumeQueries.selectAll().executeAsList()
        )
    }

    override suspend fun accept(plan: AcceptedPlan) {
        queries.transaction {
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
            val attribution = plan.volumeAttribution
            if (attribution != null) {
                plan.armCoverage.forEach { coverage ->
                    val credits = coverage.estimatedOtherInvolvementCredits
                    volumeQueries.insert(
                        planId = planId,
                        muscle = coverage.muscle.name,
                        targetSets = coverage.targetSets.toLong(),
                        isTargetEnforced = if (coverage.isTargetEnforced) 1L else 0L,
                        directIsolationSets = coverage.directIsolationSets.toLong(),
                        estimatedOtherInvolvementCredits = credits,
                        unmetReason = coverage.unmetReason?.name,
                        attribution = attribution.name
                    )
                }
            }
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
        }
    }

    override suspend fun delete(planId: Long) {
        queries.transaction {
            // An activation keeps its snapshot but loses the now-dangling source-plan provenance.
            database.trainingScheduleQueries.clearActivationSourcePlan(planId)
            queries.deleteEntriesForPlan(planId)
            queries.deleteDaysForPlan(planId)
            volumeQueries.deleteForPlan(planId)
            queries.deletePlan(planId)
        }
    }

    override suspend fun clear() {
        queries.transaction {
            database.trainingScheduleQueries.clearAllActivationSourcePlans()
            queries.deleteAllEntries()
            queries.deleteAllDays()
            volumeQueries.deleteAll()
            queries.deleteAllPlans()
        }
    }

    private fun PlanHistory.toAcceptedPlan(
        allDays: List<PlanHistoryDay>,
        allEntries: List<PlanHistoryEntry>,
        allExplanations: List<PlanVolumeExplanation>
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
                ?.let { name -> PlanAttribution.entries.firstOrNull { it.name == name } }
        )
    }

    private fun PlanVolumeExplanation.toCoverage(): ArmMuscleCoverage? {
        val resolved = MuscleGroup.entries.firstOrNull { it.name == muscle } ?: return null
        return ArmMuscleCoverage(
            muscle = resolved,
            targetSets = targetSets.toInt(),
            isTargetEnforced = isTargetEnforced != 0L,
            directIsolationSets = directIsolationSets.toInt(),
            estimatedOtherInvolvementCredits = estimatedOtherInvolvementCredits,
            unmetReason = unmetReason?.let { name ->
                ArmCoverageUnmetReason.entries.firstOrNull { it.name == name }
            }
        )
    }

    private fun String.toFocus(): SplitFocus =
        SplitFocus.entries.firstOrNull { it.name == this } ?: SplitFocus.FULL_BODY

    private fun String.toEngineId(): PlannerEngineId =
        PlannerEngineId.entries.firstOrNull { it.name == this } ?: PlannerEngineId.DETERMINISTIC
}
