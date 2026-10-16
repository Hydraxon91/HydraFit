package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup

/** Manual-only profiles; weights are model parameters, not measured muscle activation. */
internal object DefaultExercisesCatalogManual {
    val all: List<Exercise> = listOf(
        Exercise(
            id = "dumbbell-shrug",
            name = "Dumbbell Shrug",
            requiredEquipment = setOf(EquipmentTag.DUMBBELL),
            primaryMuscles = setOf(MuscleGroup.TRAPS),
            movementPattern = MovementPattern.TRAP_ISOLATION,
            involvements = mapOf(MuscleGroup.TRAPS to 1.0)
        ),
        Exercise(
            id = "ez-bar-shrug",
            name = "EZ Bar Shrug",
            requiredEquipment = setOf(EquipmentTag.EZ_BAR),
            primaryMuscles = setOf(MuscleGroup.TRAPS),
            movementPattern = MovementPattern.TRAP_ISOLATION,
            involvements = mapOf(MuscleGroup.TRAPS to 1.0)
        ),
        Exercise(
            id = "trap-bar-shrug",
            name = "Trap Bar Shrug",
            requiredEquipment = setOf(EquipmentTag.TRAP_BAR),
            primaryMuscles = setOf(MuscleGroup.TRAPS),
            movementPattern = MovementPattern.TRAP_ISOLATION,
            involvements = mapOf(MuscleGroup.TRAPS to 1.0)
        ),
        Exercise(
            id = "barbell-shrug",
            name = "Barbell Shrug",
            requiredEquipment = setOf(EquipmentTag.BARBELL),
            primaryMuscles = setOf(MuscleGroup.TRAPS),
            movementPattern = MovementPattern.TRAP_ISOLATION,
            involvements = mapOf(MuscleGroup.TRAPS to 1.0)
        ),
        Exercise(
            id = "forehead-neck-bridge",
            name = "Forehead Neck Bridge",
            requiredEquipment = setOf(EquipmentTag.BODYWEIGHT),
            primaryMuscles = setOf(MuscleGroup.NECK),
            movementPattern = MovementPattern.NECK_ISOLATION,
            loadCapability = ExerciseLoadCapability.BODYWEIGHT_ONLY,
            involvements = mapOf(MuscleGroup.NECK to 1.0)
        ),
        Exercise(
            id = "self-resisted-neck-lateral-flexion",
            name = "Self-Resisted Neck Lateral Flexion",
            requiredEquipment = setOf(EquipmentTag.BODYWEIGHT),
            primaryMuscles = setOf(MuscleGroup.NECK),
            movementPattern = MovementPattern.NECK_ISOLATION,
            isUnilateral = true,
            // Manual resistance has no calibrated kg value; use the existing no-number load kind.
            loadCapability = ExerciseLoadCapability.BODYWEIGHT_ONLY,
            involvements = mapOf(MuscleGroup.NECK to 1.0)
        )
    )
}
