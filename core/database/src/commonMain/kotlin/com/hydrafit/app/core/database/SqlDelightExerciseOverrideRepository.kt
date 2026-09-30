package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import com.hydrafit.app.core.userdata.equipment.ExerciseOverrideRepository

class SqlDelightExerciseOverrideRepository(database: HydraFitDatabase) :
    ExerciseOverrideRepository {
    private val queries = database.exerciseOverrideQueries

    override suspend fun update(
        exerciseId: String,
        name: String?,
        requiredEquipment: Set<EquipmentTag>,
        primaryMuscles: Set<MuscleGroup>,
        secondaryMuscles: Set<MuscleGroup>,
        movementPattern: MovementPattern?,
        unilateral: Boolean?
    ) {
        queries.upsert(
            exerciseId = exerciseId,
            name = name,
            requiredEquipment = encodeEquipment(requiredEquipment),
            primaryMuscles = encodeMuscles(primaryMuscles),
            secondaryMuscles = encodeMuscles(secondaryMuscles),
            movementPattern = movementPattern?.name,
            isUnilateral = unilateral?.let { if (it) 1L else 0L }
        )
    }

    override suspend fun reset(exerciseId: String) {
        queries.deleteById(exerciseId)
    }
}
