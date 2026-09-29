package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.engine.ExerciseCatalog
import com.hydrafit.app.core.domain.equipment.Exercise

class SqlDelightExerciseCatalog(database: HydraFitDatabase) : ExerciseCatalog {
    private val queries = database.exerciseQueries
    private val editQueries = database.exerciseEditQueries
    private val muscleEditQueries = database.exerciseMuscleEditQueries

    override suspend fun all(): List<Exercise> {
        val overrides = editQueries.selectAll().executeAsList()
            .associate { it.exerciseId to it.requiredEquipment }
        val muscleOverrides = muscleEditQueries.selectAll().executeAsList()
            .associateBy { it.exerciseId }
        return queries.selectAll().executeAsList().map { row ->
            val muscleEdit = muscleOverrides[row.id]
            Exercise(
                id = row.id,
                name = row.name,
                requiredEquipment = decodeEquipment(
                    overrides[row.id] ?: row.requiredEquipment
                ),
                primaryMuscles = decodeMuscles(muscleEdit?.primaryMuscles ?: row.primaryMuscles),
                secondaryMuscles = decodeMuscles(
                    muscleEdit?.secondaryMuscles ?: row.secondaryMuscles
                ),
                movementPattern = decodeMovementPattern(row.movementPattern),
                isCustom = row.isCustom != 0L
            )
        }
    }
}
