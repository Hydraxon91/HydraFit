package com.hydrafit.app.core.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.hydrafit.app.core.domain.engine.AcceptedDay
import com.hydrafit.app.core.domain.engine.AcceptedExercise
import com.hydrafit.app.core.domain.engine.AcceptedPlan
import com.hydrafit.app.core.domain.engine.PlanHistoryRepository
import com.hydrafit.app.core.domain.engine.PlannerEngineId
import com.hydrafit.app.core.domain.engine.SplitFocus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class SqlDelightPlanHistoryRepository(private val database: HydraFitDatabase) : PlanHistoryRepository {
    private val queries = database.planHistoryQueries

    override fun observeLatest(): Flow<AcceptedPlan?> {
        val plans = queries.selectLatestPlan().asFlow().mapToOneOrNull(Dispatchers.Default)
        val days = queries.selectAllDays().asFlow().mapToList(Dispatchers.Default)
        val entries = queries.selectAllEntries().asFlow().mapToList(Dispatchers.Default)
        return combine(plans, days, entries) { plan, allDays, allEntries ->
            plan?.toAcceptedPlan(allDays, allEntries)
        }
    }

    override fun observeHistory(): Flow<List<AcceptedPlan>> {
        val plans = queries.selectAllPlans().asFlow().mapToList(Dispatchers.Default)
        val days = queries.selectAllDays().asFlow().mapToList(Dispatchers.Default)
        val entries = queries.selectAllEntries().asFlow().mapToList(Dispatchers.Default)
        return combine(plans, days, entries) { planRows, allDays, allEntries ->
            planRows.map { it.toAcceptedPlan(allDays, allEntries) }
        }
    }

    override suspend fun latest(): AcceptedPlan? {
        val plan = queries.selectLatestPlan().executeAsOneOrNull() ?: return null
        return plan.toAcceptedPlan(
            queries.selectAllDays().executeAsList(),
            queries.selectAllEntries().executeAsList()
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
                        suggestedWeightKg = exercise.suggestedWeightKg
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
        newWeightKg: Double?
    ) {
        queries.transaction {
            queries.updateEntryExerciseIdAtPosition(
                newExerciseId = newExerciseId,
                newExerciseName = newExerciseName,
                newWeightKg = newWeightKg,
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
            queries.deletePlan(planId)
        }
    }

    override suspend fun clear() {
        queries.transaction {
            database.trainingScheduleQueries.clearAllActivationSourcePlans()
            queries.deleteAllEntries()
            queries.deleteAllDays()
            queries.deleteAllPlans()
        }
    }

    private fun PlanHistory.toAcceptedPlan(
        allDays: List<PlanHistoryDay>,
        allEntries: List<PlanHistoryEntry>
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
                                suggestedWeightKg = row.suggestedWeightKg
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
            id = id
        )
    }

    private fun String.toFocus(): SplitFocus =
        SplitFocus.entries.firstOrNull { it.name == this } ?: SplitFocus.FULL_BODY

    private fun String.toEngineId(): PlannerEngineId =
        PlannerEngineId.entries.firstOrNull { it.name == this } ?: PlannerEngineId.DETERMINISTIC
}
