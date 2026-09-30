package com.hydrafit.app.core.database

class SeedExerciseCatalog(private val database: HydraFitDatabase) {
    fun seed() {
        database.exerciseQueries.transaction {
            DefaultExercises.all.forEach { exercise ->
                database.exerciseQueries.insertIgnore(
                    id = exercise.id,
                    name = exercise.name,
                    requiredEquipment = encodeEquipment(exercise.requiredEquipment),
                    primaryMuscles = encodeMuscles(exercise.primaryMuscles),
                    secondaryMuscles = encodeMuscles(exercise.secondaryMuscles),
                    movementPattern = exercise.movementPattern.name
                )
                database.exerciseQueries.updateMovementPattern(
                    movementPattern = exercise.movementPattern.name,
                    id = exercise.id
                )
                database.exerciseQueries.updateIsUnilateral(
                    isUnilateral = if (exercise.isUnilateral) 1L else 0L,
                    id = exercise.id
                )
                // Fill involvement weights only where they are missing, so a user's edit is kept.
                encodeInvolvements(exercise.involvements)?.let { encoded ->
                    database.exerciseQueries.updateInvolvements(
                        involvements = encoded,
                        id = exercise.id
                    )
                }
            }
        }
    }
}
