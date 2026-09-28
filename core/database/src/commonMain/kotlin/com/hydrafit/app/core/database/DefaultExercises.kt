package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.fatigue.MuscleGroup

internal object DefaultExercises {
    val all: List<Exercise> = listOf(
        ex(
            "barbell-bench-press",
            "Barbell Bench Press",
            setOf(EquipmentTag.BARBELL, EquipmentTag.BENCH),
            setOf(MuscleGroup.CHEST),
            setOf(MuscleGroup.TRICEPS, MuscleGroup.SHOULDERS)
        ),
        ex(
            "dumbbell-bench-press",
            "Dumbbell Bench Press",
            setOf(EquipmentTag.DUMBBELL, EquipmentTag.BENCH),
            setOf(MuscleGroup.CHEST),
            setOf(MuscleGroup.TRICEPS, MuscleGroup.SHOULDERS)
        ),
        ex(
            "incline-dumbbell-press",
            "Incline Dumbbell Press",
            setOf(EquipmentTag.DUMBBELL, EquipmentTag.BENCH),
            setOf(MuscleGroup.CHEST),
            setOf(MuscleGroup.SHOULDERS, MuscleGroup.TRICEPS)
        ),
        ex(
            "push-up",
            "Push-up",
            emptySet(),
            setOf(MuscleGroup.CHEST),
            setOf(MuscleGroup.TRICEPS, MuscleGroup.SHOULDERS, MuscleGroup.CORE)
        ),
        ex(
            "incline-push-up",
            "Incline Push-up",
            emptySet(),
            setOf(MuscleGroup.CHEST),
            setOf(MuscleGroup.TRICEPS)
        ),
        ex(
            "cable-fly",
            "Cable Fly",
            setOf(EquipmentTag.CABLE_MACHINE),
            setOf(MuscleGroup.CHEST),
            setOf(MuscleGroup.SHOULDERS)
        ),
        ex(
            "band-chest-press",
            "Band Chest Press",
            setOf(EquipmentTag.RESISTANCE_BAND),
            setOf(MuscleGroup.CHEST),
            setOf(MuscleGroup.TRICEPS)
        ),
        ex(
            "dips",
            "Dips",
            setOf(EquipmentTag.BODYWEIGHT),
            setOf(MuscleGroup.CHEST, MuscleGroup.TRICEPS),
            setOf(MuscleGroup.SHOULDERS)
        ),
        ex(
            "barbell-row",
            "Barbell Row",
            setOf(EquipmentTag.BARBELL),
            setOf(MuscleGroup.BACK),
            setOf(MuscleGroup.BICEPS)
        ),
        ex(
            "dumbbell-row",
            "Dumbbell Row",
            setOf(EquipmentTag.DUMBBELL),
            setOf(MuscleGroup.BACK),
            setOf(MuscleGroup.BICEPS)
        ),
        ex(
            "pull-up",
            "Pull-up",
            setOf(EquipmentTag.PULL_UP_BAR, EquipmentTag.BODYWEIGHT),
            setOf(MuscleGroup.BACK),
            setOf(MuscleGroup.BICEPS)
        ),
        ex(
            "chin-up",
            "Chin-up",
            setOf(EquipmentTag.PULL_UP_BAR, EquipmentTag.BODYWEIGHT),
            setOf(MuscleGroup.BACK, MuscleGroup.BICEPS),
            emptySet()
        ),
        ex(
            "lat-pulldown",
            "Lat Pulldown",
            setOf(EquipmentTag.CABLE_MACHINE),
            setOf(MuscleGroup.BACK),
            setOf(MuscleGroup.BICEPS)
        ),
        ex(
            "seated-cable-row",
            "Seated Cable Row",
            setOf(EquipmentTag.CABLE_MACHINE),
            setOf(MuscleGroup.BACK),
            setOf(MuscleGroup.BICEPS)
        ),
        ex(
            "band-row",
            "Band Row",
            setOf(EquipmentTag.RESISTANCE_BAND),
            setOf(MuscleGroup.BACK),
            setOf(MuscleGroup.BICEPS)
        ),
        ex(
            "band-pull-apart",
            "Band Pull-Apart",
            setOf(EquipmentTag.RESISTANCE_BAND),
            setOf(MuscleGroup.BACK, MuscleGroup.SHOULDERS),
            emptySet()
        ),
        ex(
            "overhead-press",
            "Overhead Press",
            setOf(EquipmentTag.BARBELL),
            setOf(MuscleGroup.SHOULDERS),
            setOf(MuscleGroup.TRICEPS)
        ),
        ex(
            "dumbbell-shoulder-press",
            "Dumbbell Shoulder Press",
            setOf(EquipmentTag.DUMBBELL),
            setOf(MuscleGroup.SHOULDERS),
            setOf(MuscleGroup.TRICEPS)
        ),
        ex(
            "lateral-raise",
            "Lateral Raise",
            setOf(EquipmentTag.DUMBBELL),
            setOf(MuscleGroup.SHOULDERS),
            emptySet()
        ),
        ex(
            "cable-lateral-raise",
            "Cable Lateral Raise",
            setOf(EquipmentTag.CABLE_MACHINE),
            setOf(MuscleGroup.SHOULDERS),
            emptySet()
        ),
        ex(
            "face-pull",
            "Face Pull",
            setOf(EquipmentTag.CABLE_MACHINE),
            setOf(MuscleGroup.SHOULDERS, MuscleGroup.BACK),
            emptySet()
        ),
        ex(
            "barbell-curl",
            "Barbell Curl",
            setOf(EquipmentTag.BARBELL),
            setOf(MuscleGroup.BICEPS),
            emptySet()
        ),
        ex(
            "dumbbell-curl",
            "Dumbbell Curl",
            setOf(EquipmentTag.DUMBBELL),
            setOf(MuscleGroup.BICEPS),
            emptySet()
        ),
        ex(
            "band-curl",
            "Band Curl",
            setOf(EquipmentTag.RESISTANCE_BAND),
            setOf(MuscleGroup.BICEPS),
            emptySet()
        ),
        ex(
            "triceps-pushdown",
            "Triceps Pushdown",
            setOf(EquipmentTag.CABLE_MACHINE),
            setOf(MuscleGroup.TRICEPS),
            emptySet()
        ),
        ex(
            "overhead-triceps-extension",
            "Overhead Triceps Extension",
            setOf(EquipmentTag.DUMBBELL),
            setOf(MuscleGroup.TRICEPS),
            emptySet()
        ),
        ex(
            "close-grip-bench-press",
            "Close-Grip Bench Press",
            setOf(EquipmentTag.BARBELL, EquipmentTag.BENCH),
            setOf(MuscleGroup.TRICEPS),
            setOf(MuscleGroup.CHEST)
        ),
        ex(
            "back-squat",
            "Back Squat",
            setOf(EquipmentTag.BARBELL),
            setOf(MuscleGroup.QUADS, MuscleGroup.GLUTES),
            setOf(MuscleGroup.HAMSTRINGS, MuscleGroup.CORE)
        ),
        ex(
            "front-squat",
            "Front Squat",
            setOf(EquipmentTag.BARBELL),
            setOf(MuscleGroup.QUADS),
            setOf(MuscleGroup.GLUTES, MuscleGroup.CORE)
        ),
        ex(
            "goblet-squat",
            "Goblet Squat",
            setOf(EquipmentTag.DUMBBELL),
            setOf(MuscleGroup.QUADS, MuscleGroup.GLUTES),
            setOf(MuscleGroup.CORE)
        ),
        ex(
            "bodyweight-squat",
            "Bodyweight Squat",
            emptySet(),
            setOf(MuscleGroup.QUADS, MuscleGroup.GLUTES),
            emptySet()
        ),
        ex(
            "bulgarian-split-squat",
            "Bulgarian Split Squat",
            setOf(EquipmentTag.DUMBBELL, EquipmentTag.BENCH),
            setOf(MuscleGroup.QUADS, MuscleGroup.GLUTES),
            setOf(MuscleGroup.HAMSTRINGS)
        ),
        ex(
            "leg-press",
            "Leg Press",
            setOf(EquipmentTag.CABLE_MACHINE),
            setOf(MuscleGroup.QUADS, MuscleGroup.GLUTES),
            setOf(MuscleGroup.HAMSTRINGS)
        ),
        ex(
            "romanian-deadlift",
            "Romanian Deadlift",
            setOf(EquipmentTag.BARBELL),
            setOf(MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES),
            setOf(MuscleGroup.BACK)
        ),
        ex(
            "dumbbell-rdl",
            "Dumbbell Romanian Deadlift",
            setOf(EquipmentTag.DUMBBELL),
            setOf(MuscleGroup.HAMSTRINGS, MuscleGroup.GLUTES),
            setOf(MuscleGroup.BACK)
        ),
        ex(
            "conventional-deadlift",
            "Conventional Deadlift",
            setOf(EquipmentTag.BARBELL),
            setOf(MuscleGroup.BACK, MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS),
            setOf(MuscleGroup.QUADS, MuscleGroup.CORE)
        ),
        ex(
            "hip-thrust",
            "Hip Thrust",
            setOf(EquipmentTag.BARBELL, EquipmentTag.BENCH),
            setOf(MuscleGroup.GLUTES),
            setOf(MuscleGroup.HAMSTRINGS)
        ),
        ex(
            "glute-bridge",
            "Glute Bridge",
            emptySet(),
            setOf(MuscleGroup.GLUTES),
            setOf(MuscleGroup.HAMSTRINGS)
        ),
        ex(
            "leg-curl",
            "Leg Curl",
            setOf(EquipmentTag.CABLE_MACHINE),
            setOf(MuscleGroup.HAMSTRINGS),
            emptySet()
        ),
        ex(
            "kettlebell-swing",
            "Kettlebell Swing",
            setOf(EquipmentTag.KETTLEBELL),
            setOf(MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS),
            setOf(MuscleGroup.CORE, MuscleGroup.BACK)
        ),
        ex(
            "standing-calf-raise",
            "Standing Calf Raise",
            setOf(EquipmentTag.DUMBBELL),
            setOf(MuscleGroup.CALVES),
            emptySet()
        ),
        ex(
            "bodyweight-calf-raise",
            "Bodyweight Calf Raise",
            emptySet(),
            setOf(MuscleGroup.CALVES),
            emptySet()
        ),
        ex("plank", "Plank", emptySet(), setOf(MuscleGroup.CORE), emptySet()),
        ex(
            "hanging-leg-raise",
            "Hanging Leg Raise",
            setOf(EquipmentTag.PULL_UP_BAR, EquipmentTag.BODYWEIGHT),
            setOf(MuscleGroup.CORE),
            emptySet()
        ),
        ex(
            "cable-crunch",
            "Cable Crunch",
            setOf(EquipmentTag.CABLE_MACHINE),
            setOf(MuscleGroup.CORE),
            emptySet()
        )
    )

    private fun ex(
        id: String,
        name: String,
        equipment: Set<EquipmentTag>,
        primary: Set<MuscleGroup>,
        secondary: Set<MuscleGroup>
    ) = Exercise(
        id = id,
        name = name,
        requiredEquipment = equipment,
        primaryMuscles = primary,
        secondaryMuscles = secondary
    )
}
