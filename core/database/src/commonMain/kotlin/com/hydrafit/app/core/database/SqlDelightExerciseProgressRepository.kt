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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** Read-only projections; each replay reads a consistent transaction in bounded pages off-main. */
class SqlDelightExerciseProgressRepository(private val database: HydraFitDatabase) :
    ExerciseProgressRepository {
    private val queries = database.exerciseProgressQueries

    override fun observeHistory(request: ExerciseHistoryRequest): Flow<ExerciseHistoryPage> =
        queries.selectExerciseHistory(
            exerciseId = request.exerciseId,
            hasCursor = if (request.cursor == null) 0L else 1L,
            cursorTime = request.cursor?.performedAtMillis ?: 0L,
            cursorId = request.cursor?.setId ?: 0L,
            pageLimit = request.pageSize.toLong() + 1L
        ).asFlow().mapToList(Dispatchers.Default).map { rows ->
            ExerciseHistoryPage(
                rows.take(request.pageSize).map {
                    WorkoutHistorySet(
                        id = it.id,
                        exerciseId = it.exerciseId,
                        exerciseName = it.exerciseName,
                        reps = it.reps.toInt(),
                        weightKg = it.weightKg,
                        loadKind = decodeLoadKind(it.loadKind),
                        isWarmup = it.isWarmup != 0L,
                        rir = it.rir?.toInt(),
                        performedAtMillis = it.performedAt,
                        timingProvenance = decodeWorkoutTimingProvenance(it.timingProvenance),
                        occurrenceId = it.occurrenceId
                    )
                },
                rows.size > request.pageSize
            )
        }

    override fun observeProgress(request: ExerciseProgressRequest): Flow<ExerciseProgress> =
        queries.observeExerciseSets(request.exerciseId).asFlow().map {
            val context = currentCoroutineContext()
            database.transactionWithResult {
                val calculator = ExerciseProgressCalculator(request)
                var time = 0L
                var id = 0L
                var hasCursor = 0L
                do {
                    context.ensureActive()
                    val rows = queries.selectExerciseReplayPage(
                        exerciseId = request.exerciseId,
                        nowMillis = request.nowMillis,
                        hasCursor = hasCursor,
                        cursorTime = time,
                        cursorId = id,
                        pageLimit = REPLAY_PAGE_SIZE
                    ).executeAsList()
                    rows.forEach {
                        calculator.accept(
                            WorkoutSet(
                                id = it.id,
                                exerciseId = it.exerciseId,
                                reps = it.reps.toInt(),
                                weightKg = it.weightKg,
                                loadKind = decodeLoadKind(it.loadKind),
                                performedAtMillis = it.performedAt,
                                isWarmup = it.isWarmup != 0L,
                                occurrenceId = it.occurrenceId
                            )
                        )
                    }
                    rows.lastOrNull()?.let {
                        time = it.performedAt
                        id = it.id
                        hasCursor = 1L
                    }
                } while (rows.size == REPLAY_PAGE_SIZE.toInt())
                calculator.result()
            }
        }.flowOn(Dispatchers.Default)

    private companion object {
        const val REPLAY_PAGE_SIZE = 256L
    }
}
