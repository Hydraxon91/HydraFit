package com.hydrafit.app.core.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.hydrafit.app.core.domain.fatigue.LoggedSet
import com.hydrafit.app.core.domain.fatigue.MuscleInvolvement
import com.hydrafit.app.core.domain.fatigue.MuscleTarget
import com.hydrafit.app.core.domain.workout.WorkoutLogRepository
import com.hydrafit.app.core.domain.workout.WorkoutSet as DomainWorkoutSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class SqlDelightWorkoutLogRepository(private val database: HydraFitDatabase) :
    WorkoutLogRepository {
    private val setQueries = database.workoutLogQueries
    private val exerciseQueries = database.exerciseQueries

    override suspend fun add(set: DomainWorkoutSet) {
        setQueries.insertSet(
            exerciseId = set.exerciseId,
            reps = set.reps.toLong(),
            weightKg = set.weightKg,
            performedAt = set.performedAtMillis,
            isWarmup = if (set.isWarmup) 1L else 0L
        )
    }

    override suspend fun all(): List<DomainWorkoutSet> =
        setQueries.selectAllSets().executeAsList().map { row -> row.toDomain() }

    override fun setsFlow(): Flow<List<DomainWorkoutSet>> =
        setQueries.selectAllSets().asFlow().mapToList(Dispatchers.Default).map { rows ->
            rows.map { row -> row.toDomain() }
        }

    private fun com.hydrafit.app.core.database.WorkoutSet.toDomain() = DomainWorkoutSet(
        id = id,
        exerciseId = exerciseId,
        reps = reps.toInt(),
        weightKg = weightKg,
        performedAtMillis = performedAt,
        isWarmup = isWarmup != 0L
    )

    override suspend fun loggedSets(): List<LoggedSet> {
        val targetsByExercise = exerciseQueries.selectAll().executeAsList().associate { row ->
            row.id to targetsOf(row.primaryMuscles, row.secondaryMuscles)
        }
        return setQueries.selectAllSets().executeAsList().mapNotNull { row ->
            val targets = targetsByExercise[row.exerciseId] ?: return@mapNotNull null
            LoggedSet(
                timestampMillis = row.performedAt,
                targets = targets,
                isWarmup = row.isWarmup != 0L
            )
        }
    }

    override fun loggedSetsFlow(): Flow<List<LoggedSet>> {
        val exerciseRows = exerciseQueries.selectAll().asFlow().mapToList(Dispatchers.Default)
        val setRows = setQueries.selectAllSets().asFlow().mapToList(Dispatchers.Default)
        return combine(exerciseRows, setRows) { exercises, sets ->
            val targetsByExercise = exercises.associate { row ->
                row.id to targetsOf(row.primaryMuscles, row.secondaryMuscles)
            }
            sets.mapNotNull { row ->
                val targets = targetsByExercise[row.exerciseId] ?: return@mapNotNull null
                LoggedSet(
                    timestampMillis = row.performedAt,
                    targets = targets,
                    isWarmup = row.isWarmup != 0L
                )
            }
        }
    }

    override suspend fun clear() {
        setQueries.deleteAllSets()
    }

    private fun targetsOf(primary: String, secondary: String): List<MuscleTarget> =
        decodeMuscles(primary).map { MuscleTarget(it, MuscleInvolvement.PRIMARY) } +
            decodeMuscles(secondary).map { MuscleTarget(it, MuscleInvolvement.SECONDARY) }
}
