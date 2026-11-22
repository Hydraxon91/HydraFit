package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup

/**
 * 0.15.3 additions: a wide-grip bench press and the two bench-dip variants. The weights are model
 * parameters set by analogy with the closest existing rows (Barbell Bench Press, Dips and the triceps
 * isolation rows), not measured muscle activation; the rationale is in
 * `docs/exercise-catalog-sources.md`.
 */
internal object DefaultExercisesCatalog0153 {
    val all: List<Exercise> = listOf(
        ex(
            "wide-grip-bench-press",
            "Wide-Grip Bench Press",
            setOf(EquipmentTag.BARBELL, EquipmentTag.BENCH),
            setOf(MuscleGroup.CHEST_UPPER, MuscleGroup.CHEST_LOWER),
            setOf(MuscleGroup.TRICEPS, MuscleGroup.FRONT_DELTS),
            MovementPattern.HORIZONTAL_PUSH,
            involvements = mapOf(
                MuscleGroup.CHEST_UPPER to 0.7,
                MuscleGroup.CHEST_LOWER to 0.7,
                MuscleGroup.FRONT_DELTS to 0.5,
                MuscleGroup.TRICEPS to 0.3,
                MuscleGroup.ABS to 0.3
            )
        ),
        ex(
            "bench-dips",
            "Bench Dips",
            setOf(EquipmentTag.BODYWEIGHT, EquipmentTag.BENCH),
            setOf(MuscleGroup.TRICEPS),
            setOf(MuscleGroup.CHEST_LOWER, MuscleGroup.FRONT_DELTS),
            MovementPattern.TRICEPS_ISOLATION,
            involvements = mapOf(
                MuscleGroup.TRICEPS to 1.0,
                MuscleGroup.CHEST_LOWER to 0.3,
                MuscleGroup.FRONT_DELTS to 0.5
            )
        ),
        ex(
            "feet-elevated-bench-dips",
            "Feet-Elevated Bench Dips",
            setOf(EquipmentTag.BODYWEIGHT, EquipmentTag.BENCH),
            setOf(MuscleGroup.TRICEPS),
            setOf(MuscleGroup.CHEST_LOWER, MuscleGroup.FRONT_DELTS),
            MovementPattern.TRICEPS_ISOLATION,
            involvements = mapOf(
                MuscleGroup.TRICEPS to 1.0,
                MuscleGroup.CHEST_LOWER to 0.5,
                MuscleGroup.FRONT_DELTS to 0.7
            )
        )
    )

    private fun ex(
        id: String,
        name: String,
        equipment: Set<EquipmentTag>,
        primary: Set<MuscleGroup>,
        secondary: Set<MuscleGroup>,
        pattern: MovementPattern,
        involvements: Map<MuscleGroup, Double>
    ) = Exercise(
        id = id,
        name = name,
        requiredEquipment = equipment,
        primaryMuscles = primary,
        secondaryMuscles = secondary,
        movementPattern = pattern,
        loadCapability = ExerciseLoadDefaults.capabilityFor(id),
        involvements = involvements
    )
}
