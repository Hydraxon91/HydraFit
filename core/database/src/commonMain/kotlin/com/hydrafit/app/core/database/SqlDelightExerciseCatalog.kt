package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.equipment.Exercise

class SqlDelightExerciseCatalog(database: HydraFitDatabase) : ExerciseCatalog {
    private val queries = database.exerciseQueries

    override suspend fun all(): List<Exercise> = queries.selectAll().executeAsList().map { row ->
        Exercise(
            id = row.id,
            name = row.name,
            requiredEquipment = decodeEquipment(row.requiredEquipment),
            primaryMuscles = decodeMuscles(row.primaryMuscles),
            secondaryMuscles = decodeMuscles(row.secondaryMuscles),
            movementPattern = decodeMovementPattern(row.movementPattern)
        )
    }
}
