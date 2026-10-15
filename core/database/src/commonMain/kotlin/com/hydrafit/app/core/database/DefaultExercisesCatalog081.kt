package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup

/** Four independently sourced identities; numerical tiers are modeled, not fatigue measurements. */
internal object DefaultExercisesCatalog081 {
    val all: List<Exercise> = listOf(
        Exercise(
            id = "machine-pec-deck-fly",
            name = "Machine Pec Deck Fly",
            requiredEquipment = setOf(EquipmentTag.PEC_DECK_MACHINE),
            primaryMuscles = setOf(MuscleGroup.CHEST_LOWER),
            secondaryMuscles = setOf(MuscleGroup.CHEST_UPPER),
            movementPattern = MovementPattern.CHEST_FLY,
            loadCapability = ExerciseLoadCapability.EXTERNAL,
            involvements = mapOf(MuscleGroup.CHEST_LOWER to 1.0, MuscleGroup.CHEST_UPPER to 0.5)
        ),
        Exercise(
            id = "reverse-machine-fly",
            name = "Reverse Machine Fly",
            requiredEquipment = setOf(EquipmentTag.REVERSE_FLY_MACHINE),
            primaryMuscles = setOf(MuscleGroup.REAR_DELTS, MuscleGroup.SIDE_DELTS),
            movementPattern = MovementPattern.SHOULDER_ISOLATION,
            loadCapability = ExerciseLoadCapability.EXTERNAL,
            involvements = mapOf(MuscleGroup.REAR_DELTS to 1.0, MuscleGroup.SIDE_DELTS to 0.7)
        ),
        Exercise(
            id = "seated-dumbbell-wrist-curl",
            name = "Seated Dumbbell Wrist Curl",
            requiredEquipment = setOf(EquipmentTag.DUMBBELL, EquipmentTag.BENCH),
            primaryMuscles = setOf(MuscleGroup.FOREARMS),
            movementPattern = MovementPattern.FOREARM_ISOLATION,
            isUnilateral = true,
            loadCapability = ExerciseLoadCapability.EXTERNAL,
            involvements = mapOf(MuscleGroup.FOREARMS to 1.0)
        ),
        Exercise(
            id = "seated-dumbbell-wrist-extension",
            name = "Seated Dumbbell Wrist Extension",
            requiredEquipment = setOf(EquipmentTag.DUMBBELL, EquipmentTag.BENCH),
            primaryMuscles = setOf(MuscleGroup.FOREARMS),
            movementPattern = MovementPattern.FOREARM_ISOLATION,
            isUnilateral = true,
            loadCapability = ExerciseLoadCapability.EXTERNAL,
            involvements = mapOf(MuscleGroup.FOREARMS to 1.0)
        )
    )
}
