package com.hydrafit.app.core.database

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.hydrafit.app.core.domain.workout.ExerciseHistoryPage
import com.hydrafit.app.core.domain.workout.ExerciseHistoryRequest
import com.hydrafit.app.core.domain.workout.ExerciseProgress
import com.hydrafit.app.core.domain.workout.ExerciseProgressCalculator
import com.hydrafit.app.core.domain.workout.ExerciseProgressRepository
import com.hydrafit.app.core.domain.workout.ExerciseProgressRequest
import com.hydrafit.app.core.domain.workout.WorkoutHistorySet
import com.hydrafit.app.core.domain.workout.WorkoutSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** Read-only projections; each replay reads a consistent transaction in bounded pages off-main. */
class SqlDelightExerciseProgressRepository(private val database: HydraFitDatabase) :
    ExerciseProgressRepository {
    private val queries = database.exerciseProgressQueries

    override fun observeHistory(request: ExerciseHistoryRequest): Flow<ExerciseHistoryPage> {
        val limit = request.pageSize.toLong() + 1L
        val cursor = request.cursor
        val rows = if (cursor == null) {
            queries.selectExerciseHistoryFirstPage(request.exerciseId, limit, ::historySet)
                .asFlow().mapToList(Dispatchers.Default)
        } else {
            val ties = queries.selectExerciseHistoryTiesAtCursor(
                request.exerciseId,
                cursor.performedAtMillis,
                cursor.setId,
                limit,
                ::historySet
            ).asFlow().mapToList(Dispatchers.Default)
            val before = queries.selectExerciseHistoryBeforeCursorTime(
                request.exerciseId,
                cursor.performedAtMillis,
                limit,
                ::historySet
            ).asFlow().mapToList(Dispatchers.Default)
            combine(ties, before) { tieRows, earlierRows ->
                (tieRows + earlierRows).take(limit.toInt())
            }
        }
        return rows.map { page ->
            ExerciseHistoryPage(page.take(request.pageSize), page.size > request.pageSize)
        }
    }

    override fun observeProgress(request: ExerciseProgressRequest): Flow<ExerciseProgress> =
        queries.observeExerciseSets(request.exerciseId).asFlow().map {
            val context = currentCoroutineContext()
            database.transactionWithResult {
                val calculator = ExerciseProgressCalculator(request)
                var cursor: Pair<Long, Long>? = null
                while (true) {
                    context.ensureActive()
                    val rows = replayPage(request, cursor)
                    rows.forEach(calculator::accept)
                    val last = rows.lastOrNull() ?: break
                    cursor = last.performedAtMillis to last.id
                    if (rows.size < REPLAY_PAGE_SIZE.toInt()) break
                }
                calculator.result()
            }
        }.flowOn(Dispatchers.Default)

    private fun replayPage(
        request: ExerciseProgressRequest,
        cursor: Pair<Long, Long>?
    ): List<WorkoutSet> {
        if (cursor == null) {
            return queries.selectExerciseReplayFirstPage(
                request.exerciseId,
                request.nowMillis,
                REPLAY_PAGE_SIZE,
                ::replaySet
            ).executeAsList()
        }
        val (cursorTime, cursorId) = cursor
        val ties = queries.selectExerciseReplayTiesAtCursor(
            request.exerciseId,
            request.nowMillis,
            cursorTime,
            cursorId,
            REPLAY_PAGE_SIZE,
            ::replaySet
        ).executeAsList()
        if (ties.size.toLong() >= REPLAY_PAGE_SIZE) return ties
        val after = queries.selectExerciseReplayAfterCursorTime(
            request.exerciseId,
            request.nowMillis,
            cursorTime,
            REPLAY_PAGE_SIZE - ties.size,
            ::replaySet
        ).executeAsList()
        return ties + after
    }

    private fun historySet(
        id: Long,
        exerciseId: String,
        exerciseName: String?,
        reps: Long,
        weightKg: Double?,
        loadKind: String,
        isWarmup: Long,
        rir: Long?,
        performedAt: Long,
        timingProvenance: String,
        occurrenceId: Long?
    ) = WorkoutHistorySet(
        id = id,
        exerciseId = exerciseId,
        exerciseName = exerciseName,
        reps = reps.toInt(),
        weightKg = weightKg,
        loadKind = decodeLoadKind(loadKind),
        isWarmup = isWarmup != 0L,
        rir = rir?.toInt(),
        performedAtMillis = performedAt,
        timingProvenance = decodeWorkoutTimingProvenance(timingProvenance),
        occurrenceId = occurrenceId
    )

    private fun replaySet(
        id: Long,
        exerciseId: String,
        reps: Long,
        weightKg: Double?,
        loadKind: String,
        performedAt: Long,
        isWarmup: Long,
        occurrenceId: Long?
    ) = WorkoutSet(
        id = id,
        exerciseId = exerciseId,
        reps = reps.toInt(),
        weightKg = weightKg,
        loadKind = decodeLoadKind(loadKind),
        performedAtMillis = performedAt,
        isWarmup = isWarmup != 0L,
        occurrenceId = occurrenceId
    )

    private companion object {
        const val REPLAY_PAGE_SIZE = 256L
    }
}
