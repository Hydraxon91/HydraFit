package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup

internal object DefaultExercises {
    val all: List<Exercise> = listOf(
        ex(
            "barbell-bench-press",
            "Barbell Bench Press",
            setOf(EquipmentTag.BARBELL, EquipmentTag.BENCH),
            setOf(MuscleGroup.CHEST_UPPER, MuscleGroup.CHEST_LOWER),
            setOf(MuscleGroup.TRICEPS, MuscleGroup.FRONT_DELTS),
            MovementPattern.HORIZONTAL_PUSH,
            involvements = mapOf(
                MuscleGroup.CHEST_UPPER to 0.7,
                MuscleGroup.CHEST_LOWER to 0.7,
                MuscleGroup.FRONT_DELTS to 0.5,
                MuscleGroup.TRICEPS to 0.5,
                MuscleGroup.ABS to 0.3
            )
        ),
        ex(
            "dumbbell-bench-press",
            "Dumbbell Bench Press",
            setOf(EquipmentTag.DUMBBELL, EquipmentTag.BENCH),
            setOf(MuscleGroup.CHEST_UPPER, MuscleGroup.CHEST_LOWER),
            setOf(MuscleGroup.TRICEPS, MuscleGroup.FRONT_DELTS),
            MovementPattern.HORIZONTAL_PUSH
        ),
        ex(
            "incline-dumbbell-press",
            "Incline Dumbbell Press",
            setOf(EquipmentTag.DUMBBELL, EquipmentTag.BENCH),
            setOf(MuscleGroup.CHEST_UPPER),
            setOf(MuscleGroup.FRONT_DELTS, MuscleGroup.TRICEPS),
            MovementPattern.HORIZONTAL_PUSH
        ),
        ex(
            "push-up",
            "Push-up",
            emptySet(),
            setOf(MuscleGroup.CHEST_UPPER, MuscleGroup.CHEST_LOWER),
            setOf(MuscleGroup.TRICEPS, MuscleGroup.FRONT_DELTS, MuscleGroup.ABS),
            MovementPattern.HORIZONTAL_PUSH
        ),
        ex(
            "incline-push-up",
            "Incline Push-up",
            emptySet(),
            setOf(MuscleGroup.CHEST_UPPER),
            setOf(MuscleGroup.TRICEPS),
            MovementPattern.HORIZONTAL_PUSH
        ),
        ex(
            "cable-fly",
            "Cable Fly",
            setOf(EquipmentTag.CABLE_MACHINE),
            setOf(MuscleGroup.CHEST_UPPER, MuscleGroup.CHEST_LOWER),
            setOf(MuscleGroup.FRONT_DELTS),
            MovementPattern.CHEST_FLY
        ),
        ex(
            "band-chest-press",
            "Band Chest Press",
            setOf(EquipmentTag.RESISTANCE_BAND),
            setOf(MuscleGroup.CHEST_UPPER, MuscleGroup.CHEST_LOWER),
            setOf(MuscleGroup.TRICEPS),
            MovementPattern.HORIZONTAL_PUSH
        ),
        ex(
            "dips",
            "Dips",
            setOf(EquipmentTag.BODYWEIGHT),
            setOf(MuscleGroup.CHEST_LOWER, MuscleGroup.TRICEPS),
            setOf(MuscleGroup.FRONT_DELTS),
            MovementPattern.VERTICAL_PUSH
        ),
        ex(
            "barbell-row",
            "Barbell Row",
            setOf(EquipmentTag.BARBELL),
            setOf(MuscleGroup.UPPER_BACK, MuscleGroup.LATS),
            setOf(MuscleGroup.BICEPS),
            MovementPattern.HORIZONTAL_PULL
        ),
        ex(
            "dumbbell-row",
            "Dumbbell Row",
            setOf(EquipmentTag.DUMBBELL),
            setOf(MuscleGroup.LATS, MuscleGroup.UPPER_BACK),
            setOf(MuscleGroup.BICEPS),
            MovementPattern.HORIZONTAL_PULL,
            isUnilateral = true
        ),
        ex(
            "pull-up",
            "Pull-up",
            setOf(EquipmentTag.PULL_UP_BAR, EquipmentTag.BODYWEIGHT),
            setOf(MuscleGroup.LATS),
            setOf(MuscleGroup.BICEPS),
            MovementPattern.VERTICAL_PULL
        ),
        ex(
            "chin-up",
            "Chin-up",
            setOf(EquipmentTag.PULL_UP_BAR, EquipmentTag.BODYWEIGHT),
            setOf(MuscleGroup.LATS, MuscleGroup.BICEPS),
            emptySet(),
            MovementPattern.VERTICAL_PULL
        ),
        ex(
            "lat-pulldown",
            "Lat Pulldown",
            setOf(EquipmentTag.CABLE_MACHINE),
            setOf(MuscleGroup.LATS),
            setOf(MuscleGroup.BICEPS),
            MovementPattern.VERTICAL_PULL
        ),
        ex(
            "seated-cable-row",
            "Seated Cable Row",
            setOf(EquipmentTag.CABLE_MACHINE),
            setOf(MuscleGroup.UPPER_BACK, MuscleGroup.LATS),
            setOf(MuscleGroup.BICEPS),
            MovementPattern.HORIZONTAL_PULL
        ),
        ex(
            "band-row",
            "Band Row",
            setOf(EquipmentTag.RESISTANCE_BAND),
            setOf(MuscleGroup.UPPER_BACK, MuscleGroup.LATS),
            setOf(MuscleGroup.BICEPS),
            MovementPattern.HORIZONTAL_PULL
        ),
        ex(
            "band-pull-apart",
            "Band Pull-Apart",
            setOf(EquipmentTag.RESISTANCE_BAND),
            setOf(MuscleGroup.REAR_DELTS, MuscleGroup.UPPER_BACK),
            emptySet(),
            MovementPattern.SHOULDER_ISOLATION
        ),
        ex(
            "overhead-press",
            "Overhead Press",
            setOf(EquipmentTag.BARBELL),
            setOf(MuscleGroup.FRONT_DELTS, MuscleGroup.SIDE_DELTS),
            setOf(MuscleGroup.TRICEPS),
            MovementPattern.VERTICAL_PUSH
        ),
        ex(
            "dumbbell-shoulder-press",
            "Dumbbell Shoulder Press",
            setOf(EquipmentTag.DUMBBELL),
            setOf(MuscleGroup.FRONT_DELTS, MuscleGroup.SIDE_DELTS),
            setOf(MuscleGroup.TRICEPS),
            MovementPattern.VERTICAL_PUSH
        ),
        ex(
            "lateral-raise",
            "Lateral Raise",
            setOf(EquipmentTag.DUMBBELL),
            setOf(MuscleGroup.SIDE_DELTS),
            emptySet(),
            MovementPattern.SHOULDER_ISOLATION
        ),
        ex(
            "cable-lateral-raise",
            "Cable Lateral Raise",
            setOf(EquipmentTag.CABLE_MACHINE),
            setOf(MuscleGroup.SIDE_DELTS),
            emptySet(),
            MovementPattern.SHOULDER_ISOLATION
        ),
        ex(
            "face-pull",
            "Face Pull",
            setOf(EquipmentTag.CABLE_MACHINE),
            setOf(MuscleGroup.REAR_DELTS, MuscleGroup.UPPER_BACK),
            emptySet(),
            MovementPattern.SHOULDER_ISOLATION
        ),
        ex(
            "barbell-curl",
            "Barbell Curl",
            setOf(EquipmentTag.BARBELL),
            setOf(MuscleGroup.BICEPS),
            emptySet(),
            MovementPattern.BICEPS_ISOLATION
        ),
        ex(
            "dumbbell-curl",
            "Dumbbell Curl",
            setOf(EquipmentTag.DUMBBELL),
            setOf(MuscleGroup.BICEPS),
            emptySet(),
            MovementPattern.BICEPS_ISOLATION,
            isUnilateral = true
        ),
        ex(
            "band-curl",
            "Band Curl",
            setOf(EquipmentTag.RESISTANCE_BAND),
            setOf(MuscleGroup.BICEPS),
            emptySet(),
            MovementPattern.BICEPS_ISOLATION
        ),
        ex(
            "triceps-pushdown",
            "Triceps Pushdown",
            setOf(EquipmentTag.CABLE_MACHINE),
            setOf(MuscleGroup.TRICEPS),
            emptySet(),
            MovementPattern.TRICEPS_ISOLATION
        ),
        ex(
            "overhead-triceps-extension",
            "Overhead Triceps Extension",
            setOf(EquipmentTag.DUMBBELL),
            setOf(MuscleGroup.TRICEPS),
            emptySet(),
            MovementPattern.TRICEPS_ISOLATION
        ),
        ex(
            "close-grip-bench-press",
            "Close-Grip Bench Press",
            setOf(EquipmentTag.BARBELL, EquipmentTag.BENCH),
            setOf(MuscleGroup.TRICEPS),
            setOf(MuscleGroup.CHEST_UPPER, MuscleGroup.CHEST_LOWER),
            MovementPattern.HORIZONTAL_PUSH
        ),
        ex(
            "back-squat",
            "Back Squat",
            setOf(EquipmentTag.BARBELL),
            setOf(MuscleGroup.QUADS, MuscleGroup.GLUTES),
            setOf(MuscleGroup.HAMSTRINGS, MuscleGroup.ABS),
            MovementPattern.SQUAT
        ),
        ex(
            "front-squat",
            "Front Squat",
            setOf(EquipmentTag.BARBELL),
            setOf(MuscleGroup.QUADS),
            setOf(MuscleGroup.GLUTES, MuscleGroup.ABS),
            MovementPattern.SQUAT
        ),
        ex(
            "goblet-squat",
            "Goblet Squat",
            setOf(EquipmentTag.DUMBBELL),
            setOf(MuscleGroup.QUADS, MuscleGroup.GLUTES),
            setOf(MuscleGroup.ABS),
            MovementPattern.SQUAT
        ),
        ex(
            "bodyweight-squat",
            "Bodyweight Squat",
            emptySet(),
            setOf(MuscleGroup.QUADS, MuscleGroup.GLUTES),
            emptySet(),
            MovementPattern.SQUAT
        ),
        ex(
            "bulgarian-split-squat",
            "Bulgarian Split Squat",
            setOf(EquipmentTag.DUMBBELL, EquipmentTag.BENCH),
            setOf(MuscleGroup.QUADS, MuscleGroup.GLUTES),
            setOf(MuscleGroup.HAMSTRINGS),
            MovementPattern.LUNGE,
            isUnilateral = true
        ),
        ex(
            "leg-press",
            "Leg Press",
            setOf(EquipmentTag.LEG_PRESS_MACHINE),
            setOf(MuscleGroup.QUADS, MuscleGroup.GLUTES),
            setOf(MuscleGroup.HAMSTRINGS),
            MovementPattern.SQUAT
        ),
        ex(
            "romanian-deadlift",
            "Romanian Deadlift",
            setOf(EquipmentTag.BARBELL),
            setOf(MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES),
            setOf(MuscleGroup.LOWER_BACK),
            MovementPattern.HINGE
        ),
        ex(
            "dumbbell-rdl",
            "Dumbbell Romanian Deadlift",
            setOf(EquipmentTag.DUMBBELL),
            setOf(MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES),
            setOf(MuscleGroup.LOWER_BACK),
            MovementPattern.HINGE
        ),
        ex(
            "conventional-deadlift",
            "Conventional Deadlift",
            setOf(EquipmentTag.BARBELL),
            setOf(MuscleGroup.LOWER_BACK, MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS),
            setOf(MuscleGroup.QUADS, MuscleGroup.ABS),
            MovementPattern.HINGE
        ),
        ex(
            "hip-thrust",
            "Hip Thrust",
            setOf(EquipmentTag.BARBELL, EquipmentTag.BENCH),
            setOf(MuscleGroup.GLUTES),
            setOf(MuscleGroup.HAMSTRINGS),
            MovementPattern.HINGE
        ),
        ex(
            "glute-bridge",
            "Glute Bridge",
            emptySet(),
            setOf(MuscleGroup.GLUTES),
            setOf(MuscleGroup.HAMSTRINGS),
            MovementPattern.HINGE
        ),
        ex(
            "leg-curl",
            "Leg Curl",
            setOf(EquipmentTag.LEG_CURL_MACHINE),
            setOf(MuscleGroup.HAMSTRINGS),
            emptySet(),
            MovementPattern.LEG_ISOLATION
        ),
        ex(
            "kettlebell-swing",
            "Kettlebell Swing",
            setOf(EquipmentTag.KETTLEBELL),
            setOf(MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS),
            setOf(MuscleGroup.ABS, MuscleGroup.LOWER_BACK),
            MovementPattern.HINGE
        ),
        ex(
            "standing-calf-raise",
            "Standing Calf Raise",
            setOf(EquipmentTag.DUMBBELL),
            setOf(MuscleGroup.CALVES),
            emptySet(),
            MovementPattern.CALF_RAISE
        ),
        ex(
            "bodyweight-calf-raise",
            "Bodyweight Calf Raise",
            emptySet(),
            setOf(MuscleGroup.CALVES),
            emptySet(),
            MovementPattern.CALF_RAISE
        ),
        ex(
            "plank",
            "Plank",
            emptySet(),
            setOf(MuscleGroup.ABS, MuscleGroup.OBLIQUES),
            emptySet(),
            MovementPattern.CORE
        ),
        ex(
            "hanging-leg-raise",
            "Hanging Leg Raise",
            setOf(EquipmentTag.PULL_UP_BAR, EquipmentTag.BODYWEIGHT),
            setOf(MuscleGroup.ABS),
            setOf(MuscleGroup.OBLIQUES),
            MovementPattern.CORE
        ),
        ex(
            "cable-crunch",
            "Cable Crunch",
            setOf(EquipmentTag.CABLE_MACHINE),
            setOf(MuscleGroup.ABS),
            emptySet(),
            MovementPattern.CORE
        ),
        ex(
            "hammer-curl",
            "Hammer Curl",
            setOf(EquipmentTag.DUMBBELL),
            setOf(MuscleGroup.BICEPS),
            emptySet(),
            MovementPattern.BICEPS_ISOLATION,
            isUnilateral = true,
            involvements = mapOf(MuscleGroup.BICEPS to 1.0)
        ),
        ex(
            "supinated-curl",
            "Supinated Curl",
            setOf(EquipmentTag.DUMBBELL),
            setOf(MuscleGroup.BICEPS),
            emptySet(),
            MovementPattern.BICEPS_ISOLATION,
            isUnilateral = true,
            involvements = mapOf(MuscleGroup.BICEPS to 1.0)
        ),
        ex(
            "preacher-curl",
            "Preacher Curl",
            setOf(EquipmentTag.BARBELL, EquipmentTag.BENCH),
            setOf(MuscleGroup.BICEPS),
            emptySet(),
            MovementPattern.BICEPS_ISOLATION,
            involvements = mapOf(MuscleGroup.BICEPS to 1.0)
        ),
        ex(
            "close-grip-pulldown",
            "Close-Grip Pulldown",
            setOf(EquipmentTag.CABLE_MACHINE),
            setOf(MuscleGroup.LATS),
            setOf(MuscleGroup.BICEPS),
            MovementPattern.VERTICAL_PULL,
            involvements = mapOf(MuscleGroup.LATS to 1.0, MuscleGroup.BICEPS to 0.5)
        ),
        ex(
            "wide-grip-pulldown",
            "Wide-Grip Pulldown",
            setOf(EquipmentTag.CABLE_MACHINE),
            setOf(MuscleGroup.LATS),
            setOf(MuscleGroup.BICEPS),
            MovementPattern.VERTICAL_PULL,
            involvements = mapOf(MuscleGroup.LATS to 1.0, MuscleGroup.BICEPS to 0.3)
        ),
        ex(
            "incline-barbell-press",
            "Incline Barbell Press",
            setOf(EquipmentTag.BARBELL, EquipmentTag.BENCH),
            setOf(MuscleGroup.CHEST_UPPER),
            setOf(MuscleGroup.FRONT_DELTS, MuscleGroup.TRICEPS),
            MovementPattern.HORIZONTAL_PUSH,
            involvements = mapOf(
                MuscleGroup.CHEST_UPPER to 1.0,
                MuscleGroup.FRONT_DELTS to 0.5,
                MuscleGroup.TRICEPS to 0.5
            )
        ),
        ex(
            "decline-barbell-press",
            "Decline Barbell Press",
            setOf(EquipmentTag.BARBELL, EquipmentTag.BENCH),
            setOf(MuscleGroup.CHEST_LOWER),
            setOf(MuscleGroup.TRICEPS, MuscleGroup.FRONT_DELTS),
            MovementPattern.HORIZONTAL_PUSH,
            involvements = mapOf(
                MuscleGroup.CHEST_LOWER to 1.0,
                MuscleGroup.TRICEPS to 0.5,
                MuscleGroup.FRONT_DELTS to 0.3
            )
        ),
        ex(
            "ez-bar-upright-row",
            "EZ Bar Upright Row",
            setOf(EquipmentTag.EZ_BAR),
            setOf(MuscleGroup.SIDE_DELTS, MuscleGroup.UPPER_BACK),
            setOf(MuscleGroup.BICEPS),
            MovementPattern.SHOULDER_ISOLATION,
            involvements = mapOf(
                MuscleGroup.SIDE_DELTS to 1.0,
                MuscleGroup.UPPER_BACK to 0.3,
                MuscleGroup.BICEPS to 0.3,
                MuscleGroup.ABS to 0.3
            )
        ),
        ex(
            "seated-ez-bar-curl",
            "Seated EZ Bar Curl",
            setOf(EquipmentTag.EZ_BAR),
            setOf(MuscleGroup.BICEPS),
            setOf(MuscleGroup.FOREARMS),
            MovementPattern.BICEPS_ISOLATION,
            involvements = mapOf(MuscleGroup.BICEPS to 1.0, MuscleGroup.FOREARMS to 0.3)
        ),
        ex(
            "trap-bar-deadlift",
            "Trap Bar Deadlift",
            setOf(EquipmentTag.TRAP_BAR),
            setOf(MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS, MuscleGroup.QUADS),
            setOf(MuscleGroup.LOWER_BACK, MuscleGroup.UPPER_BACK, MuscleGroup.ABS),
            MovementPattern.HINGE,
            involvements = mapOf(
                MuscleGroup.GLUTES to 1.0,
                MuscleGroup.HAMSTRINGS to 1.0,
                MuscleGroup.QUADS to 0.5,
                MuscleGroup.LOWER_BACK to 0.7,
                MuscleGroup.UPPER_BACK to 0.5,
                MuscleGroup.ABS to 0.5
            )
        ),
        ex(
            "dumbbell-lunge",
            "Dumbbell Lunge",
            setOf(EquipmentTag.DUMBBELL),
            setOf(MuscleGroup.QUADS, MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS),
            setOf(MuscleGroup.CALVES, MuscleGroup.LOWER_BACK),
            MovementPattern.LUNGE
        ),
        ex(
            "barbell-shoulder-press",
            "Barbell Shoulder Press",
            setOf(EquipmentTag.BARBELL),
            setOf(MuscleGroup.FRONT_DELTS, MuscleGroup.SIDE_DELTS),
            setOf(MuscleGroup.TRICEPS, MuscleGroup.ABS),
            MovementPattern.VERTICAL_PUSH
        ),
        ex(
            "ab-roll",
            "Ab Roll",
            setOf(EquipmentTag.BODYWEIGHT, EquipmentTag.AB_ROLLER),
            setOf(MuscleGroup.ABS),
            setOf(MuscleGroup.OBLIQUES, MuscleGroup.TRICEPS),
            MovementPattern.CORE,
            involvements = mapOf(MuscleGroup.ABS to 1.0, MuscleGroup.TRICEPS to 0.3)
        ),
        ex(
            "leg-extension",
            "Leg Extension",
            setOf(EquipmentTag.LEG_EXTENSION_MACHINE),
            setOf(MuscleGroup.QUADS),
            setOf(MuscleGroup.ABS),
            MovementPattern.LEG_ISOLATION,
            involvements = mapOf(MuscleGroup.QUADS to 1.0, MuscleGroup.ABS to 0.3)
        ),
        ex(
            "standing-dumbbell-side-bend",
            "Standing Dumbbell Side Bend",
            setOf(EquipmentTag.DUMBBELL),
            setOf(MuscleGroup.OBLIQUES),
            emptySet(),
            MovementPattern.CORE,
            isUnilateral = true,
            involvements = mapOf(MuscleGroup.OBLIQUES to 1.0)
        ),
        ex(
            "dumbbell-triceps-overhead-extension",
            "Dumbbell Triceps Overhead Extension",
            setOf(EquipmentTag.DUMBBELL),
            setOf(MuscleGroup.TRICEPS),
            emptySet(),
            MovementPattern.TRICEPS_ISOLATION,
            involvements = mapOf(MuscleGroup.TRICEPS to 1.0)
        ),
        ex(
            "dumbbell-front-to-lateral-raise-combo",
            "Dumbbell Front to Lateral Raise combo",
            setOf(EquipmentTag.DUMBBELL),
            setOf(MuscleGroup.FRONT_DELTS, MuscleGroup.SIDE_DELTS),
            setOf(MuscleGroup.UPPER_BACK, MuscleGroup.ABS),
            MovementPattern.SHOULDER_ISOLATION,
            isUnilateral = true
        )
    ) + DefaultExercisesCatalogC1.all + DefaultExercisesCatalogP7.all

    private fun ex(
        id: String,
        name: String,
        equipment: Set<EquipmentTag>,
        primary: Set<MuscleGroup>,
        secondary: Set<MuscleGroup>,
        pattern: MovementPattern,
        isUnilateral: Boolean = false,
        involvements: Map<MuscleGroup, Double> = emptyMap()
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
