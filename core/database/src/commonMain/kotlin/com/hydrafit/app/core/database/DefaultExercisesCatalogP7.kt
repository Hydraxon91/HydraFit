package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup

/** CAT-P7 additions; row-level provenance and modeled involvement limits live in the source doc. */
internal object DefaultExercisesCatalogP7 {
    val all: List<Exercise> = listOf(
        ex(
            "flat-bench-cable-fly",
            "Flat Bench Cable Fly",
            setOf(EquipmentTag.CABLE_MACHINE, EquipmentTag.BENCH),
            setOf(MuscleGroup.CHEST_UPPER, MuscleGroup.CHEST_LOWER),
            emptySet(),
            MovementPattern.CHEST_FLY,
            involvements = mapOf(
                MuscleGroup.CHEST_UPPER to 0.7,
                MuscleGroup.CHEST_LOWER to 0.7
            )
        ),
        ex(
            "single-arm-cable-crossover",
            "Single-Arm Cable Crossover",
            setOf(EquipmentTag.CABLE_MACHINE),
            setOf(MuscleGroup.CHEST_UPPER, MuscleGroup.CHEST_LOWER),
            emptySet(),
            MovementPattern.CHEST_FLY,
            isUnilateral = true,
            involvements = mapOf(
                MuscleGroup.CHEST_UPPER to 0.7,
                MuscleGroup.CHEST_LOWER to 0.7
            )
        ),
        ex(
            "seated-single-arm-cable-row",
            "Seated Single-Arm Cable Row",
            setOf(EquipmentTag.CABLE_MACHINE),
            setOf(MuscleGroup.UPPER_BACK),
            setOf(MuscleGroup.LATS, MuscleGroup.BICEPS),
            MovementPattern.HORIZONTAL_PULL,
            isUnilateral = true,
            involvements = mapOf(
                MuscleGroup.UPPER_BACK to 0.7,
                MuscleGroup.LATS to 0.5,
                MuscleGroup.BICEPS to 0.5
            )
        ),
        ex(
            "dumbbell-floor-press",
            "Dumbbell Floor Press",
            setOf(EquipmentTag.DUMBBELL),
            setOf(MuscleGroup.TRICEPS),
            setOf(MuscleGroup.CHEST_UPPER, MuscleGroup.CHEST_LOWER, MuscleGroup.FRONT_DELTS),
            MovementPattern.HORIZONTAL_PUSH,
            involvements = mapOf(
                MuscleGroup.TRICEPS to 0.7,
                MuscleGroup.CHEST_UPPER to 0.5,
                MuscleGroup.CHEST_LOWER to 0.5,
                MuscleGroup.FRONT_DELTS to 0.5
            )
        ),
        ex(
            "seated-arnold-dumbbell-press",
            "Seated Arnold Dumbbell Press",
            setOf(EquipmentTag.DUMBBELL, EquipmentTag.BENCH),
            setOf(MuscleGroup.FRONT_DELTS, MuscleGroup.SIDE_DELTS),
            setOf(MuscleGroup.TRICEPS),
            MovementPattern.VERTICAL_PUSH,
            involvements = mapOf(
                MuscleGroup.FRONT_DELTS to 0.7,
                MuscleGroup.SIDE_DELTS to 0.7,
                MuscleGroup.TRICEPS to 0.5
            )
        ),
        ex(
            "single-arm-kettlebell-row",
            "Single-Arm Kettlebell Row",
            setOf(EquipmentTag.KETTLEBELL),
            setOf(MuscleGroup.UPPER_BACK),
            setOf(MuscleGroup.LATS, MuscleGroup.BICEPS),
            MovementPattern.HORIZONTAL_PULL,
            isUnilateral = true,
            involvements = mapOf(
                MuscleGroup.UPPER_BACK to 0.7,
                MuscleGroup.LATS to 0.5,
                MuscleGroup.BICEPS to 0.5
            )
        ),
        ex(
            "single-leg-kettlebell-deadlift",
            "Single-Leg Kettlebell Deadlift",
            setOf(EquipmentTag.KETTLEBELL),
            setOf(MuscleGroup.HAMSTRINGS),
            setOf(MuscleGroup.GLUTES, MuscleGroup.LOWER_BACK),
            MovementPattern.HINGE,
            isUnilateral = true,
            involvements = mapOf(
                MuscleGroup.HAMSTRINGS to 1.0,
                MuscleGroup.GLUTES to 0.5,
                MuscleGroup.LOWER_BACK to 0.3
            )
        ),
        ex(
            "incline-dumbbell-curl",
            "Incline Dumbbell Curl",
            setOf(EquipmentTag.DUMBBELL, EquipmentTag.BENCH),
            setOf(MuscleGroup.BICEPS),
            emptySet(),
            MovementPattern.BICEPS_ISOLATION,
            involvements = mapOf(MuscleGroup.BICEPS to 1.0)
        ),
        ex(
            "single-leg-cable-kickback",
            "Single-Leg Cable Kickback",
            setOf(EquipmentTag.CABLE_MACHINE),
            setOf(MuscleGroup.GLUTES),
            setOf(MuscleGroup.HAMSTRINGS),
            MovementPattern.LEG_ISOLATION,
            isUnilateral = true,
            involvements = mapOf(
                MuscleGroup.GLUTES to 1.0,
                MuscleGroup.HAMSTRINGS to 0.5
            )
        ),
        ex(
            "bicycle-crunch",
            "Bicycle Crunch",
            setOf(EquipmentTag.BODYWEIGHT),
            setOf(MuscleGroup.ABS),
            setOf(MuscleGroup.OBLIQUES),
            MovementPattern.CORE,
            involvements = mapOf(
                MuscleGroup.ABS to 1.0,
                MuscleGroup.OBLIQUES to 0.5
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
        isUnilateral: Boolean = false,
        involvements: Map<MuscleGroup, Double>
    ) = Exercise(
        id = id,
        name = name,
        requiredEquipment = equipment,
        primaryMuscles = primary,
        secondaryMuscles = secondary,
        movementPattern = pattern,
        isUnilateral = isUnilateral,
        loadCapability = ExerciseLoadDefaults.capabilityFor(id),
        involvements = involvements
    )
}
