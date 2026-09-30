package com.hydrafit.app.core.userdata.equipment

import com.hydrafit.app.core.domain.equipment.Equipment
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import kotlinx.coroutines.flow.Flow

/** The user's equipment catalog: the seeded built-ins plus any equipment they have added. */
interface EquipmentRepository {
    fun observeAll(): Flow<List<Equipment>>

    suspend fun all(): List<Equipment>

    suspend fun add(name: String): Equipment

    suspend fun remove(id: EquipmentTag)

    /** Sets the heaviest weight this equipment can provide, or null for unlimited (plate-loaded). */
    suspend fun setMaxWeight(id: EquipmentTag, maxWeightKg: Double?)
}

/** CRUD for user-created exercises. Built-in exercises are never created, edited, or deleted here. */
interface CustomExerciseRepository {
    suspend fun add(
        name: String,
        requiredEquipment: Set<EquipmentTag>,
        involvements: Map<MuscleGroup, Double>,
        movementPattern: MovementPattern,
        isUnilateral: Boolean = false
    ): Exercise

    suspend fun update(
        id: String,
        name: String,
        requiredEquipment: Set<EquipmentTag>,
        involvements: Map<MuscleGroup, Double>,
        movementPattern: MovementPattern,
        isUnilateral: Boolean = false
    )

    suspend fun delete(id: String)
}

/** Thrown when a custom exercise cannot be changed: invalid input or an existing reference. */
class CustomExerciseException(message: String) : Exception(message)
