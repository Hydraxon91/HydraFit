package com.hydrafit.app.core.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.hydrafit.app.core.domain.fatigue.LoggedSet
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
    private val overrideQueries = database.exerciseOverrideQueries

    override suspend fun add(set: DomainWorkoutSet) {
        // Snapshot the exercise's effective (override-aware) involvement weights at log time so
        // later catalog edits can't rewrite history.
        val seed = exerciseQueries.selectById(set.exerciseId).executeAsOneOrNull()
        val override = overrideQueries.selectById(set.exerciseId).executeAsOneOrNull()
        setQueries.insertSet(
            exerciseId = set.exerciseId,
            reps = set.reps.toLong(),
            weightKg = set.weightKg,
            performedAt = set.performedAtMillis,
            isWarmup = if (set.isWarmup) 1L else 0L,
            involvements = override?.involvements ?: seed?.involvements,
            weekNumber = set.weekNumber?.toLong(),
            cycleNumber = set.cycleNumber?.toLong(),
            dayIndex = set.dayIndex?.toLong(),
            rir = set.rir?.toLong(),
            sessionId = set.sessionId
        )
    }

    override suspend fun assignSession(setId: Long, sessionId: String) {
        setQueries.assignSession(sessionId = sessionId, id = setId)
    }

    override suspend fun delete(id: Long) {
        setQueries.deleteSet(id)
    }

    override suspend fun updateSetPerformedAt(setId: Long, performedAtMillis: Long) {
        setQueries.updateSetPerformedAt(performedAt = performedAtMillis, id = setId)
    }

    override suspend fun all(): List<DomainWorkoutSet> =
        setQueries.selectAllSets().executeAsList().map { row -> row.toDomain() }

    override suspend fun lastSetBySession(sessionId: String): DomainWorkoutSet? =
        setQueries.selectLastSetBySession(sessionId).executeAsOneOrNull()?.toDomain()

    override fun setsFlow(): Flow<List<DomainWorkoutSet>> =
        setQueries.selectAllSets().asFlow().mapToList(Dispatchers.Default).map { rows ->
            rows.map { row -> row.toDomain() }
        }

    override suspend fun loggedSets(): List<LoggedSet> = mapLoggedSets(
        exercises = exerciseQueries.selectAll().executeAsList(),
        overrides = overrideQueries.selectAll().executeAsList(),
        setRows = setQueries.selectAllSets().executeAsList()
    )

    override fun loggedSetsFlow(): Flow<List<LoggedSet>> {
        val exerciseRows = exerciseQueries.selectAll().asFlow().mapToList(Dispatchers.Default)
        val overrideRows = overrideQueries.selectAll().asFlow().mapToList(Dispatchers.Default)
        val setRows = setQueries.selectAllSets().asFlow().mapToList(Dispatchers.Default)
        return combine(exerciseRows, overrideRows, setRows) { exercises, overrides, sets ->
            mapLoggedSets(exercises, overrides, sets)
        }
    }

    private fun mapLoggedSets(
        exercises: List<com.hydrafit.app.core.database.Exercise>,
        overrides: List<com.hydrafit.app.core.database.ExerciseOverride>,
        setRows: List<com.hydrafit.app.core.database.WorkoutSet>
    ): List<LoggedSet> {
        val decodeCache = HashMap<String, List<MuscleTarget>>()
        val targetsByExercise = exercises.associate { row ->
            row.id to (decodeCached(row.involvements, decodeCache) ?: emptyList())
        }
        val compoundByExercise = compoundByExercise(exercises, overrides)
        return setRows.mapNotNull { row ->
            val targets = decodeCached(row.involvements, decodeCache)
                ?: targetsByExercise[row.exerciseId]
                ?: return@mapNotNull null
            LoggedSet(
                timestampMillis = row.performedAt,
                targets = targets,
                isWarmup = row.isWarmup != 0L,
                reps = row.reps.toInt(),
                isCompound = compoundByExercise[row.exerciseId] ?: false,
                exerciseId = row.exerciseId,
                weightKg = row.weightKg,
                rir = row.rir?.toInt(),
                sessionId = row.sessionId
            )
        }
    }

    /**
     * Exercise type for the recovery split, derived (never stored) from the resolved catalog
     * movement pattern. A custom exercise with a null pattern resolves to isolation.
     */
    private fun compoundByExercise(
        exercises: List<com.hydrafit.app.core.database.Exercise>,
        overrides: List<com.hydrafit.app.core.database.ExerciseOverride>
    ): Map<String, Boolean> {
        val overridePatterns = overrides.associate { it.exerciseId to it.movementPattern }
        return exercises.associate { row ->
            val pattern = overridePatterns[row.id] ?: row.movementPattern
            row.id to decodeMovementPattern(pattern).isCompound
        }
    }

    override suspend fun clear() {
        setQueries.deleteAllSets()
    }

    private fun decodeCached(
        involvements: String?,
        cache: MutableMap<String, List<MuscleTarget>>
    ): List<MuscleTarget>? = involvements?.let { cache.getOrPut(it) { targetsOf(it) } }

    private fun targetsOf(involvements: String?): List<MuscleTarget> =
        decodeInvolvements(involvements).map { MuscleTarget(it.key, it.value) }
}
