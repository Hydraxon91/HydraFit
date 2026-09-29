package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.userdata.equipment.ExerciseMuscleRepository

class SqlDelightExerciseMuscleRepository(database: HydraFitDatabase) :
    ExerciseMuscleRepository {
    private val queries = database.exerciseMuscleEditQueries

    override suspend fun update(
        exerciseId: String,
        primaryMuscles: Set<MuscleGroup>,
        secondaryMuscles: Set<MuscleGroup>
    ) {
        queries.upsert(
            exerciseId,
            encodeMuscles(primaryMuscles),
            encodeMuscles(secondaryMuscles)
        )
    }

    override suspend fun reset(exerciseId: String) {
        queries.deleteById(exerciseId)
    }
}
