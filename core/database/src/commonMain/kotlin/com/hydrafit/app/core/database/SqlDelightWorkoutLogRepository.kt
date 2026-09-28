package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.fatigue.LoggedSet
import com.hydrafit.app.core.domain.fatigue.MuscleInvolvement
import com.hydrafit.app.core.domain.fatigue.MuscleTarget
import com.hydrafit.app.core.domain.workout.WorkoutLogRepository
import com.hydrafit.app.core.domain.workout.WorkoutSet as DomainWorkoutSet

class SqlDelightWorkoutLogRepository(
    private val database: HydraFitDatabase
) : WorkoutLogRepository {
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
        setQueries.selectAllSets().executeAsList().map { row ->
            DomainWorkoutSet(
                id = row.id,
                exerciseId = row.exerciseId,
                reps = row.reps.toInt(),
                weightKg = row.weightKg,
                performedAtMillis = row.performedAt,
                isWarmup = row.isWarmup != 0L
            )
        }

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

    override suspend fun clear() {
        setQueries.deleteAllSets()
    }

    private fun targetsOf(primary: String, secondary: String): List<MuscleTarget> =
        decodeMuscles(primary).map { MuscleTarget(it, MuscleInvolvement.PRIMARY) } +
            decodeMuscles(secondary).map { MuscleTarget(it, MuscleInvolvement.SECONDARY) }
}
