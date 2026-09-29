package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.userdata.equipment.ExerciseEquipmentRepository

class SqlDelightExerciseEquipmentRepository(database: HydraFitDatabase) :
    ExerciseEquipmentRepository {
    private val queries = database.exerciseEditQueries

    override suspend fun update(exerciseId: String, equipment: Set<EquipmentTag>) {
        queries.upsert(exerciseId, encodeEquipment(equipment))
    }

    override suspend fun reset(exerciseId: String) {
        queries.deleteById(exerciseId)
    }
}
