package com.hydrafit.app.core.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.hydrafit.app.core.domain.workout.WORKOUT_HISTORY_SESSION_KIND_RANK
import com.hydrafit.app.core.domain.workout.WorkoutHistoryEntry
import com.hydrafit.app.core.domain.workout.WorkoutHistoryKey
import com.hydrafit.app.core.domain.workout.WorkoutHistoryNameSource
import com.hydrafit.app.core.domain.workout.WorkoutHistoryPage
import com.hydrafit.app.core.domain.workout.WorkoutHistoryRepository
import com.hydrafit.app.core.domain.workout.WorkoutHistoryRequest
import com.hydrafit.app.core.domain.workout.WorkoutHistorySet
import com.hydrafit.app.core.domain.workout.WorkoutHistorySetPage
import com.hydrafit.app.core.domain.workout.WorkoutHistorySetRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Bounded, read-only history projection. Every query is limited by the request's page size (plus one
 * lookahead row) so a history read never loads the whole log. Exercise names come from the current
 * catalog, which is current context rather than a historical snapshot.
 */
class SqlDelightWorkoutHistoryRepository(database: HydraFitDatabase) : WorkoutHistoryRepository {
    private val queries = database.workoutHistoryQueries

    override fun observeEntries(request: WorkoutHistoryRequest): Flow<WorkoutHistoryPage> =
        queries.selectHistoryEntries(
            hasSearch = flag(request.hasSearch),
            searchPattern = request.searchPattern,
            utcOffsetMillis = request.utcOffsetMillis,
            hasDay = flag(request.hasDay),
            dayFrom = request.dayFromBound,
            dayTo = request.dayToBound,
            hasCursor = flag(request.hasCursor),
            cursorTimestamp = request.cursorTimestamp,
            cursorKindRank = request.cursorKindRank,
            cursorSortId = request.cursorSortKey,
            limit = request.fetchLimit.toLong()
        ).asFlow().mapToList(Dispatchers.Default).map { rows ->
            WorkoutHistoryPage(
                entries = rows.take(request.pageSize).map { it.toEntry() },
                hasMore = rows.size > request.pageSize
            )
        }

    override fun observeEntry(
        key: WorkoutHistoryKey,
        utcOffsetMillis: Long
    ): Flow<WorkoutHistoryEntry?> = queries.selectHistoryEntry(
        sessionId = (key as? WorkoutHistoryKey.Session)?.sessionId ?: "",
        utcOffsetMillis = utcOffsetMillis,
        setId = (key as? WorkoutHistoryKey.UnassignedSet)?.setId ?: 0L
    ).asFlow().mapToOneOrNull(Dispatchers.Default).map { it?.toEntry() }

    override fun observeEntrySets(
        key: WorkoutHistoryKey,
        request: WorkoutHistorySetRequest
    ): Flow<WorkoutHistorySetPage> = when (key) {
        is WorkoutHistoryKey.Session -> queries.selectSessionHistorySets(
            sessionId = key.sessionId,
            hasCursor = flag(request.hasCursor),
            cursorPerformedAt = request.cursorPerformedAt,
            cursorSetId = request.cursorSetId,
            limit = request.fetchLimit.toLong()
        ).asFlow().mapToList(Dispatchers.Default).map { rows ->
            WorkoutHistorySetPage(
                sets = rows.take(request.pageSize).map { it.toHistorySet() },
                hasMore = rows.size > request.pageSize
            )
        }

        is WorkoutHistoryKey.UnassignedSet -> queries.selectUnassignedSetHistorySet(key.setId)
            .asFlow().mapToOneOrNull(Dispatchers.Default).map { row ->
                WorkoutHistorySetPage(
                    sets = row?.let { listOf(it.toHistorySet()) } ?: emptyList(),
                    hasMore = false
                )
            }
    }

    private fun flag(value: Boolean): Long = if (value) 1L else 0L

    private fun keyOf(kindRank: Long, sortId: String): WorkoutHistoryKey =
        if (kindRank == WORKOUT_HISTORY_SESSION_KIND_RANK) {
            WorkoutHistoryKey.Session(sortId)
        } else {
            // The id is the signed, zero-padded form the query projected, so it parses as written and
            // a stored id round-trips through a cursor unchanged.
            WorkoutHistoryKey.UnassignedSet(sortId.toLong())
        }

    private fun nameSourceFor(setCount: Long): WorkoutHistoryNameSource = if (setCount > 0L) {
        WorkoutHistoryNameSource.CURRENT_CATALOG
    } else {
        WorkoutHistoryNameSource.NONE
    }

    private fun SelectHistoryEntries.toEntry(): WorkoutHistoryEntry = WorkoutHistoryEntry(
        key = keyOf(kindRank, sortId),
        timestampMillis = timestampMillis,
        localEpochDay = localEpochDay,
        sessionMissing = sessionMissing != 0L,
        note = note,
        recordedSetCount = setCount.toInt(),
        nameSource = nameSourceFor(setCount)
    )

    private fun SelectHistoryEntry.toEntry(): WorkoutHistoryEntry = WorkoutHistoryEntry(
        key = keyOf(kindRank, sortId),
        timestampMillis = timestampMillis,
        localEpochDay = localEpochDay,
        sessionMissing = sessionMissing != 0L,
        note = note,
        recordedSetCount = setCount.toInt(),
        nameSource = nameSourceFor(setCount)
    )

    private fun SelectSessionHistorySets.toHistorySet(): WorkoutHistorySet = WorkoutHistorySet(
        id = id,
        exerciseId = exerciseId,
        exerciseName = exerciseName,
        reps = reps?.toInt(),
        weightKg = weightKg,
        loadKind = decodeLoadKind(loadKind),
        station = stationAttributionOf(stationId, stationName, stationLoadBasis),
        isWarmup = isWarmup != 0L,
        rir = rir?.toInt(),
        performedAtMillis = performedAt,
        timingProvenance = decodeWorkoutTimingProvenance(timingProvenance),
        durationSeconds = durationSeconds?.toInt(),
        occurrenceId = occurrenceId,
        distanceMeters = distanceMeters?.toInt()
    )

    private fun SelectUnassignedSetHistorySet.toHistorySet(): WorkoutHistorySet = WorkoutHistorySet(
        id = id,
        exerciseId = exerciseId,
        exerciseName = exerciseName,
        reps = reps?.toInt(),
        weightKg = weightKg,
        loadKind = decodeLoadKind(loadKind),
        station = stationAttributionOf(stationId, stationName, stationLoadBasis),
        isWarmup = isWarmup != 0L,
        rir = rir?.toInt(),
        performedAtMillis = performedAt,
        timingProvenance = decodeWorkoutTimingProvenance(timingProvenance),
        durationSeconds = durationSeconds?.toInt(),
        occurrenceId = occurrenceId,
        distanceMeters = distanceMeters?.toInt()
    )
}
