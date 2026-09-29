package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.equipment.Exercise

class SqlDelightExerciseCatalog(database: HydraFitDatabase) : ExerciseCatalog {
    private val queries = database.exerciseQueries
    private val overrideQueries = database.exerciseOverrideQueries

    override suspend fun all(): List<Exercise> {
        val overrides = overrideQueries.selectAll().executeAsList()
            .associateBy { it.exerciseId }
        return queries.selectAll().executeAsList().map { row ->
            val override = overrides[row.id]
            Exercise(
                id = row.id,
                name = override?.name ?: row.name,
                requiredEquipment = decodeEquipment(
                    override?.requiredEquipment ?: row.requiredEquipment
                ),
                primaryMuscles = decodeMuscles(
                    override?.primaryMuscles ?: row.primaryMuscles
                ),
                secondaryMuscles = decodeMuscles(
                    override?.secondaryMuscles ?: row.secondaryMuscles
                ),
                movementPattern = decodeMovementPattern(
                    override?.movementPattern ?: row.movementPattern
                ),
                isCustom = row.isCustom != 0L
            )
        }
    }
}
