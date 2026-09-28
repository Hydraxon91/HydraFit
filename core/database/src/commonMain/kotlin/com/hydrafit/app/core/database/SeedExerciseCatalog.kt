package com.hydrafit.app.core.database

class SeedExerciseCatalog(
    private val database: HydraFitDatabase
) {
    fun seed() {
        database.exerciseQueries.transaction {
            DefaultExercises.all.forEach { exercise ->
                database.exerciseQueries.insertIgnore(
                    id = exercise.id,
                    name = exercise.name,
                    requiredEquipment = encodeEquipment(exercise.requiredEquipment),
                    primaryMuscles = encodeMuscles(exercise.primaryMuscles),
                    secondaryMuscles = encodeMuscles(exercise.secondaryMuscles)
                )
            }
        }
    }
}
