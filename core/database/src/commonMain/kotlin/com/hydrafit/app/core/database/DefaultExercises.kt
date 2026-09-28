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
            setOf(MuscleGroup.CHEST),
            setOf(MuscleGroup.TRICEPS, MuscleGroup.SHOULDERS),
            MovementPattern.HORIZONTAL_PUSH
        ),
        ex(
            "dumbbell-bench-press",
            "Dumbbell Bench Press",
            setOf(EquipmentTag.DUMBBELL, EquipmentTag.BENCH),
            setOf(MuscleGroup.CHEST),
            setOf(MuscleGroup.TRICEPS, MuscleGroup.SHOULDERS),
            MovementPattern.HORIZONTAL_PUSH
        ),
        ex(
            "incline-dumbbell-press",
            "Incline Dumbbell Press",
            setOf(EquipmentTag.DUMBBELL, EquipmentTag.BENCH),
            setOf(MuscleGroup.CHEST),
            setOf(MuscleGroup.SHOULDERS, MuscleGroup.TRICEPS),
            MovementPattern.HORIZONTAL_PUSH
        ),
        ex(
            "push-up",
            "Push-up",
            emptySet(),
            setOf(MuscleGroup.CHEST),
            setOf(MuscleGroup.TRICEPS, MuscleGroup.SHOULDERS, MuscleGroup.CORE),
            MovementPattern.HORIZONTAL_PUSH
        ),
        ex(
            "incline-push-up",
            "Incline Push-up",
            emptySet(),
            setOf(MuscleGroup.CHEST),
            setOf(MuscleGroup.TRICEPS),
            MovementPattern.HORIZONTAL_PUSH
        ),
        ex(
            "cable-fly",
            "Cable Fly",
            setOf(EquipmentTag.CABLE_MACHINE),
            setOf(MuscleGroup.CHEST),
            setOf(MuscleGroup.SHOULDERS),
            MovementPattern.CHEST_FLY
        ),
        ex(
            "band-chest-press",
            "Band Chest Press",
            setOf(EquipmentTag.RESISTANCE_BAND),
            setOf(MuscleGroup.CHEST),
            setOf(MuscleGroup.TRICEPS),
            MovementPattern.HORIZONTAL_PUSH
        ),
        ex(
            "dips",
            "Dips",
            setOf(EquipmentTag.BODYWEIGHT),
            setOf(MuscleGroup.CHEST, MuscleGroup.TRICEPS),
            setOf(MuscleGroup.SHOULDERS),
            MovementPattern.VERTICAL_PUSH
        ),
        ex(
            "barbell-row",
            "Barbell Row",
            setOf(EquipmentTag.BARBELL),
            setOf(MuscleGroup.BACK),
            setOf(MuscleGroup.BICEPS),
            MovementPattern.HORIZONTAL_PULL
        ),
        ex(
            "dumbbell-row",
            "Dumbbell Row",
            setOf(EquipmentTag.DUMBBELL),
            setOf(MuscleGroup.BACK),
            setOf(MuscleGroup.BICEPS),
            MovementPattern.HORIZONTAL_PULL
        ),
        ex(
            "pull-up",
            "Pull-up",
            setOf(EquipmentTag.PULL_UP_BAR, EquipmentTag.BODYWEIGHT),
            setOf(MuscleGroup.BACK),
            setOf(MuscleGroup.BICEPS),
            MovementPattern.VERTICAL_PULL
        ),
        ex(
            "chin-up",
            "Chin-up",
            setOf(EquipmentTag.PULL_UP_BAR, EquipmentTag.BODYWEIGHT),
            setOf(MuscleGroup.BACK, MuscleGroup.BICEPS),
            emptySet(),
            MovementPattern.VERTICAL_PULL
        ),
        ex(
            "lat-pulldown",
            "Lat Pulldown",
            setOf(EquipmentTag.CABLE_MACHINE),
            setOf(MuscleGroup.BACK),
            setOf(MuscleGroup.BICEPS),
            MovementPattern.VERTICAL_PULL
        ),
        ex(
            "seated-cable-row",
            "Seated Cable Row",
            setOf(EquipmentTag.CABLE_MACHINE),
            setOf(MuscleGroup.BACK),
            setOf(MuscleGroup.BICEPS),
            MovementPattern.HORIZONTAL_PULL
        ),
        ex(
            "band-row",
            "Band Row",
            setOf(EquipmentTag.RESISTANCE_BAND),
            setOf(MuscleGroup.BACK),
            setOf(MuscleGroup.BICEPS),
            MovementPattern.HORIZONTAL_PULL
        ),
        ex(
            "band-pull-apart",
            "Band Pull-Apart",
            setOf(EquipmentTag.RESISTANCE_BAND),
            setOf(MuscleGroup.BACK, MuscleGroup.SHOULDERS),
            emptySet(),
            MovementPattern.SHOULDER_ISOLATION
        ),
        ex(
            "overhead-press",
            "Overhead Press",
            setOf(EquipmentTag.BARBELL),
            setOf(MuscleGroup.SHOULDERS),
            setOf(MuscleGroup.TRICEPS),
            MovementPattern.VERTICAL_PUSH
        ),
        ex(
            "dumbbell-shoulder-press",
            "Dumbbell Shoulder Press",
            setOf(EquipmentTag.DUMBBELL),
            setOf(MuscleGroup.SHOULDERS),
            setOf(MuscleGroup.TRICEPS),
            MovementPattern.VERTICAL_PUSH
        ),
        ex(
            "lateral-raise",
            "Lateral Raise",
            setOf(EquipmentTag.DUMBBELL),
            setOf(MuscleGroup.SHOULDERS),
            emptySet(),
            MovementPattern.SHOULDER_ISOLATION
        ),
        ex(
            "cable-lateral-raise",
            "Cable Lateral Raise",
            setOf(EquipmentTag.CABLE_MACHINE),
            setOf(MuscleGroup.SHOULDERS),
            emptySet(),
            MovementPattern.SHOULDER_ISOLATION
        ),
        ex(
            "face-pull",
            "Face Pull",
            setOf(EquipmentTag.CABLE_MACHINE),
            setOf(MuscleGroup.SHOULDERS, MuscleGroup.BACK),
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
            MovementPattern.BICEPS_ISOLATION
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
            setOf(MuscleGroup.CHEST),
            MovementPattern.HORIZONTAL_PUSH
        ),
        ex(
            "back-squat",
            "Back Squat",
            setOf(EquipmentTag.BARBELL),
            setOf(MuscleGroup.QUADS, MuscleGroup.GLUTES),
            setOf(MuscleGroup.HAMSTRINGS, MuscleGroup.CORE),
            MovementPattern.SQUAT
        ),
        ex(
            "front-squat",
            "Front Squat",
            setOf(EquipmentTag.BARBELL),
            setOf(MuscleGroup.QUADS),
            setOf(MuscleGroup.GLUTES, MuscleGroup.CORE),
            MovementPattern.SQUAT
        ),
        ex(
            "goblet-squat",
            "Goblet Squat",
            setOf(EquipmentTag.DUMBBELL),
            setOf(MuscleGroup.QUADS, MuscleGroup.GLUTES),
            setOf(MuscleGroup.CORE),
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
            MovementPattern.LUNGE
        ),
        ex(
            "leg-press",
            "Leg Press",
            setOf(EquipmentTag.CABLE_MACHINE),
            setOf(MuscleGroup.QUADS, MuscleGroup.GLUTES),
            setOf(MuscleGroup.HAMSTRINGS),
            MovementPattern.SQUAT
        ),
        ex(
            "romanian-deadlift",
            "Romanian Deadlift",
            setOf(EquipmentTag.BARBELL),
            setOf(MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES),
            setOf(MuscleGroup.BACK),
            MovementPattern.HINGE
        ),
        ex(
            "dumbbell-rdl",
            "Dumbbell Romanian Deadlift",
            setOf(EquipmentTag.DUMBBELL),
            setOf(MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES),
            setOf(MuscleGroup.BACK),
            MovementPattern.HINGE
        ),
        ex(
            "conventional-deadlift",
            "Conventional Deadlift",
            setOf(EquipmentTag.BARBELL),
            setOf(MuscleGroup.BACK, MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS),
            setOf(MuscleGroup.QUADS, MuscleGroup.CORE),
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
            setOf(EquipmentTag.CABLE_MACHINE),
            setOf(MuscleGroup.HAMSTRINGS),
            emptySet(),
            MovementPattern.LEG_ISOLATION
        ),
        ex(
            "kettlebell-swing",
            "Kettlebell Swing",
            setOf(EquipmentTag.KETTLEBELL),
            setOf(MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS),
            setOf(MuscleGroup.CORE, MuscleGroup.BACK),
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
            setOf(MuscleGroup.CORE),
            emptySet(),
            MovementPattern.CORE
        ),
        ex(
            "hanging-leg-raise",
            "Hanging Leg Raise",
            setOf(EquipmentTag.PULL_UP_BAR, EquipmentTag.BODYWEIGHT),
            setOf(MuscleGroup.CORE),
            emptySet(),
            MovementPattern.CORE
        ),
        ex(
            "cable-crunch",
            "Cable Crunch",
            setOf(EquipmentTag.CABLE_MACHINE),
            setOf(MuscleGroup.CORE),
            emptySet(),
            MovementPattern.CORE
        )
    )

    private fun ex(
        id: String,
        name: String,
        equipment: Set<EquipmentTag>,
        primary: Set<MuscleGroup>,
        secondary: Set<MuscleGroup>,
        pattern: MovementPattern
    ) = Exercise(
        id = id,
        name = name,
        requiredEquipment = equipment,
        primaryMuscles = primary,
        secondaryMuscles = secondary,
        movementPattern = pattern
    )
}
