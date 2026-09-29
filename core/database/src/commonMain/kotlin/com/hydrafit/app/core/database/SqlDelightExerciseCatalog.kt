package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.equipment.Exercise

class SqlDelightExerciseCatalog(database: HydraFitDatabase) : ExerciseCatalog {
    private val queries = database.exerciseQueries
    private val editQueries = database.exerciseEditQueries

    override suspend fun all(): List<Exercise> {
        val overrides = editQueries.selectAll().executeAsList()
            .associate { it.exerciseId to it.requiredEquipment }
        return queries.selectAll().executeAsList().map { row ->
            Exercise(
                id = row.id,
                name = row.name,
                requiredEquipment = decodeEquipment(
                    overrides[row.id] ?: row.requiredEquipment
                ),
                primaryMuscles = decodeMuscles(row.primaryMuscles),
                secondaryMuscles = decodeMuscles(row.secondaryMuscles),
                movementPattern = decodeMovementPattern(row.movementPattern)
            )
        }
    }
}
