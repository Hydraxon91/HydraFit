package com.hydrafit.app.core.database

class SeedExerciseCatalog(private val database: HydraFitDatabase) {
    fun seed() {
        database.exerciseQueries.transaction {
            DefaultExercises.all.forEach { exercise ->
                database.exerciseQueries.insertIgnore(
                    id = exercise.id,
                    name = exercise.name,
                    requiredEquipment = encodeEquipment(exercise.requiredEquipment),
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
                // Always write involvement weights (explicit, or derived from the authored tags)
                // but only where missing, so a user's edit is kept.
                database.exerciseQueries.updateInvolvements(
                    involvements = encodeInvolvements(exercise.effectiveInvolvements),
                    id = exercise.id
                )
            }
        }
    }
}
