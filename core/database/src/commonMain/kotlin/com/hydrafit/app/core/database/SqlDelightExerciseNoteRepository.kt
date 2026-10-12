package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.workout.ExerciseNoteRepository
import com.hydrafit.app.core.domain.workout.NOTE_MAX_LENGTH

class SqlDelightExerciseNoteRepository(database: HydraFitDatabase) : ExerciseNoteRepository {
    private val database = database
    private val queries = database.exerciseNoteQueries

    override suspend fun note(exerciseId: String): String? =
        queries.selectById(exerciseId).executeAsOneOrNull()?.note

    override suspend fun save(exerciseId: String, text: String) {
        require(text.length <= NOTE_MAX_LENGTH) {
            "Exercise notes must not exceed $NOTE_MAX_LENGTH characters"
        }
        database.transaction {
            require(database.exerciseQueries.selectById(exerciseId).executeAsOneOrNull() != null) {
                "Exercise does not exist: $exerciseId"
            }
            queries.upsert(exerciseId = exerciseId, note = text)
        }
    }

    override suspend fun clear(exerciseId: String) {
        save(exerciseId, "")
    }
}
