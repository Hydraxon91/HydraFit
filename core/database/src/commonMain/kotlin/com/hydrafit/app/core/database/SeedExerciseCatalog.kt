package com.hydrafit.app.core.database

class SeedExerciseCatalog(private val database: HydraFitDatabase) {
    fun seed() {
        database.exerciseQueries.transaction {
            DefaultExercises.all.forEach { exercise ->
                database.exerciseQueries.insertIgnore(
                    id = exercise.id,
                    name = exercise.name,
                    requiredEquipment = encodeEquipment(exercise.requiredEquipment),
                    movementPattern = exercise.movementPattern.name,
                    loadCapability = exercise.loadCapability.name
                )
                database.exerciseQueries.updateMovementPattern(
                    movementPattern = exercise.movementPattern.name,
                    id = exercise.id
                )
                database.exerciseQueries.updateIsUnilateral(
                    isUnilateral = if (exercise.isUnilateral) 1L else 0L,
                    id = exercise.id
                )
                // Curated capability on built-in rows only; a user's edit lives in exerciseOverride and
                // custom rows keep their own selection.
                database.exerciseQueries.updateLoadCapability(
                    loadCapability = exercise.loadCapability.name,
                    id = exercise.id
                )
                // Always write involvement weights (explicit, or derived from the authored tags)
                // but only where missing, so a user's edit is kept.
                database.exerciseQueries.updateInvolvements(
                    involvements = encodeInvolvements(exercise.effectiveInvolvements),
                    id = exercise.id
                )
            }
            // Bring pre-existing built-in rows onto the CAT-P0 tier scale (0.6/0.4/0.2 -> 0.7/0.5/0.3).
            // Only affects seed-owned weights; custom rows, user overrides and logged snapshots are untouched.
            database.exerciseQueries.normalizeLegacyInvolvementWeights()
        }
    }
}
