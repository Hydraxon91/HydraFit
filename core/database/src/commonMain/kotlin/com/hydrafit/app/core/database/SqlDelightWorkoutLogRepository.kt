package com.hydrafit.app.core.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.hydrafit.app.core.domain.fatigue.LoggedSet
import com.hydrafit.app.core.domain.fatigue.MuscleTarget
import com.hydrafit.app.core.domain.workout.LoadKind
import com.hydrafit.app.core.domain.workout.WorkoutLogRepository
import com.hydrafit.app.core.domain.workout.WorkoutPrescriptionSnapshotJson
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
        val encodedTarget = set.prescriptionSnapshot?.let { target ->
            require(target.exerciseId == set.exerciseId) { "Prescription exercise does not match" }
            require(target.loadKind == set.loadKind) { "Prescription load kind does not match" }
            // A recorded target must describe the same measurement the set does, or the stored row
            // would contradict itself and the exported backup would be refused by our own validator.
            require((target.reps != null) == (set.durationSeconds == null)) {
                "Prescription measurement does not match"
            }
            WorkoutPrescriptionSnapshotJson.encode(target)
        }
        // Snapshot the exercise's effective (override-aware) involvement weights at log time so
        // later catalog edits can't rewrite history.
        val seed = exerciseQueries.selectById(set.exerciseId).executeAsOneOrNull()
        val override = overrideQueries.selectById(set.exerciseId).executeAsOneOrNull()
        setQueries.transaction {
            setQueries.insertSetWithTimingProvenance(
                exerciseId = set.exerciseId,
                reps = set.reps?.toLong(),
                weightKg = set.weightKg,
                performedAt = set.performedAtMillis,
                isWarmup = if (set.isWarmup) 1L else 0L,
                involvements = override?.involvements ?: seed?.involvements,
                weekNumber = set.weekNumber?.toLong(),
                cycleNumber = set.cycleNumber?.toLong(),
                dayIndex = set.dayIndex?.toLong(),
                rir = set.rir?.toLong(),
                sessionId = set.sessionId,
                loadKind = set.loadKind.name,
                timingProvenance = set.timingProvenance.name,
                startedAtElapsedMillis = set.startedAtElapsedMillis,
                completedAtElapsedMillis = set.completedAtElapsedMillis,
                durationSeconds = set.durationSeconds?.toLong(),
                prescriptionSnapshot = encodedTarget
            )
            val setId = setQueries.lastInsertedSetId().executeAsOne()
            set.station?.let { station ->
                setQueries.assignStation(
                    stationId = station.id,
                    stationName = station.name,
                    stationLoadBasis = station.loadBasis.name,
                    id = setId
                )
            }
            if (set.occurrenceId != null || set.occurrenceEntryId != null) {
                setQueries.assignOccurrence(
                    occurrenceId = set.occurrenceId,
                    occurrenceEntryId = set.occurrenceEntryId,
                    id = setId
                )
            }
        }
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

    override suspend fun setsForOccurrence(occurrenceId: Long): List<DomainWorkoutSet> =
        setQueries.selectSetsForOccurrence(occurrenceId).executeAsList().map { it.toDomain() }

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
            // A timed set is real recorded work, but it carries no rep count to replay against the
            // fatigue model, and its seconds are never read as repetitions.
            if (row.durationSeconds != null) return@mapNotNull null
            val reps = row.reps ?: return@mapNotNull null
            val targets = decodeCached(row.involvements, decodeCache)
                ?: targetsByExercise[row.exerciseId]
                ?: return@mapNotNull null
            LoggedSet(
                timestampMillis = row.performedAt,
                targets = targets,
                isWarmup = row.isWarmup != 0L,
                reps = reps.toInt(),
                isCompound = compoundByExercise[row.exerciseId] ?: false,
                exerciseId = row.exerciseId,
                // Neutral relative load for work whose recorded number is not external resistance:
                // added kilograms are not total resistance, and bodyweight carries none. External
                // and legacy rows keep the recorded weight, so historical fatigue is unchanged.
                weightKg = row.weightKg.takeIf {
                    val kind = decodeLoadKind(row.loadKind)
                    kind != LoadKind.BODYWEIGHT && kind != LoadKind.ADDED
                },
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
