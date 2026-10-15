package com.hydrafit.app.core.userdata.equipment

import com.hydrafit.app.core.domain.equipment.Equipment
import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import kotlinx.coroutines.flow.Flow

/** The user's equipment catalog: the seeded built-ins plus any equipment they have added. */
interface EquipmentRepository {
    fun observeAll(): Flow<List<Equipment>>

    suspend fun all(): List<Equipment>

    suspend fun add(name: String): Equipment

    suspend fun remove(id: EquipmentTag)

    suspend fun remove(context: EquipmentProfileContext, id: EquipmentTag) = remove(id)

    /**
     * Renames custom equipment and sets its ceiling atomically, keeping [id] stable so the equipment
     * stays selected and any exercise references remain valid.
     */
    suspend fun rename(id: EquipmentTag, name: String, maxWeightKg: Double?): Equipment

    /** Profile-aware editor write. Implementations without profiles retain their existing behavior. */
    suspend fun rename(
        context: EquipmentProfileContext,
        id: EquipmentTag,
        name: String,
        maxWeightKg: Double?
    ): Equipment = rename(id, name, maxWeightKg)

    /** Sets the heaviest weight this equipment can provide, or null for unlimited (plate-loaded). */
    suspend fun setMaxWeight(id: EquipmentTag, maxWeightKg: Double?)

    suspend fun setMaxWeight(
        context: EquipmentProfileContext,
        id: EquipmentTag,
        maxWeightKg: Double?
    ) = setMaxWeight(id, maxWeightKg)
}

/** CRUD for user-created exercises. Built-in exercises are never created, edited, or deleted here. */
interface CustomExerciseRepository {
    suspend fun add(
        name: String,
        requiredEquipment: Set<EquipmentTag>,
        involvements: Map<MuscleGroup, Double>,
        movementPattern: MovementPattern,
        isUnilateral: Boolean = false,
        loadCapability: ExerciseLoadCapability = ExerciseLoadCapability.EXTERNAL
    ): Exercise

    suspend fun update(
        id: String,
        name: String,
        requiredEquipment: Set<EquipmentTag>,
        involvements: Map<MuscleGroup, Double>,
        movementPattern: MovementPattern,
        isUnilateral: Boolean = false,
        loadCapability: ExerciseLoadCapability = ExerciseLoadCapability.EXTERNAL
    )

    suspend fun delete(id: String)
}

/** Thrown when a custom exercise cannot be changed: invalid input or an existing reference. */
class CustomExerciseException(message: String, val reason: CustomExerciseFailureReason? = null) :
    Exception(message)

/** Typed validation failures that the editor renders with localized guidance. */
enum class CustomExerciseFailureReason {
    NAME_CONFLICT,
    PERSONAL_RECORD_REFERENCE
}
