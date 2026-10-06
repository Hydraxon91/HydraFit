package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.equipment.EquipmentTag
import com.hydrafit.app.core.domain.equipment.Exercise
import com.hydrafit.app.core.domain.equipment.MovementPattern
import com.hydrafit.app.core.domain.fatigue.MuscleGroup

/**
 * CAT-P1 researched catalog batch (catalog P3). Sourced from yuhonas/free-exercise-db
 * (Unlicense) with per-muscle involvement weights calibrated against the EMG reviews cited
 * in `docs/exercise-catalog-rows-c1p1.md` and `docs/exercise-catalog-sources.md`.
 *
 * `primaryMuscles`/`secondaryMuscles` are the dataset's primary muscles (mapped to the
 * HydraFit muscle model), with the remainder of [involvements] as secondary. Bodyweight rows
 * carry [EquipmentTag.BODYWEIGHT], which `Exercise.isAvailableWith` ignores (so it is
 * behaviourally equivalent to an empty equipment set).
 */
internal object DefaultExercisesCatalogC1 {
    val all: List<Exercise> = listOf(
        ex(
            "bench-press-with-chains",
            "Bench Press with Chains",
            setOf(EquipmentTag.BARBELL),
            setOf(MuscleGroup.TRICEPS),
            setOf(
                MuscleGroup.CHEST_LOWER,
                MuscleGroup.CHEST_UPPER,
                MuscleGroup.FRONT_DELTS,
                MuscleGroup.LATS,
                MuscleGroup.SIDE_DELTS
            ),
            MovementPattern.HORIZONTAL_PUSH,
            involvements = mapOf(
                MuscleGroup.CHEST_LOWER to 0.5,
                MuscleGroup.CHEST_UPPER to 0.5,
                MuscleGroup.FRONT_DELTS to 0.5,
                MuscleGroup.LATS to 0.5,
                MuscleGroup.SIDE_DELTS to 0.5,
                MuscleGroup.TRICEPS to 0.7
            )
        ),
        ex(
            "cable-chest-press",
            "Cable Chest Press",
            setOf(EquipmentTag.CABLE_MACHINE),
            setOf(MuscleGroup.CHEST_LOWER, MuscleGroup.CHEST_UPPER),
            setOf(MuscleGroup.FRONT_DELTS, MuscleGroup.SIDE_DELTS, MuscleGroup.TRICEPS),
            MovementPattern.HORIZONTAL_PUSH,
            involvements = mapOf(
                MuscleGroup.CHEST_LOWER to 0.7,
                MuscleGroup.CHEST_UPPER to 0.7,
                MuscleGroup.FRONT_DELTS to 0.5,
                MuscleGroup.SIDE_DELTS to 0.5,
                MuscleGroup.TRICEPS to 0.5
            )
        ),
        ex(
            "handstand-push-ups",
            "Handstand Push-Ups",
            setOf(EquipmentTag.BODYWEIGHT),
            setOf(MuscleGroup.FRONT_DELTS, MuscleGroup.SIDE_DELTS),
            setOf(MuscleGroup.TRICEPS),
            MovementPattern.HORIZONTAL_PUSH,
            involvements = mapOf(
                MuscleGroup.FRONT_DELTS to 0.7,
                MuscleGroup.SIDE_DELTS to 0.7,
                MuscleGroup.TRICEPS to 0.5
            )
        ),
        ex(
            "incline-push-up-medium",
            "Incline Push-Up Medium",
            setOf(EquipmentTag.BODYWEIGHT),
            setOf(MuscleGroup.CHEST_UPPER),
            setOf(
                MuscleGroup.ABS,
                MuscleGroup.FRONT_DELTS,
                MuscleGroup.SIDE_DELTS,
                MuscleGroup.TRICEPS
            ),
            MovementPattern.HORIZONTAL_PUSH,
            involvements = mapOf(
                MuscleGroup.ABS to 0.3,
                MuscleGroup.CHEST_UPPER to 0.7,
                MuscleGroup.FRONT_DELTS to 0.5,
                MuscleGroup.SIDE_DELTS to 0.5,
                MuscleGroup.TRICEPS to 0.5
            )
        ),
        ex(
            "incline-push-up-wide",
            "Incline Push-Up Wide",
            setOf(EquipmentTag.BODYWEIGHT),
            setOf(MuscleGroup.CHEST_UPPER),
            setOf(
                MuscleGroup.ABS,
                MuscleGroup.FRONT_DELTS,
                MuscleGroup.SIDE_DELTS,
                MuscleGroup.TRICEPS
            ),
            MovementPattern.HORIZONTAL_PUSH,
            involvements = mapOf(
                MuscleGroup.ABS to 0.3,
                MuscleGroup.CHEST_UPPER to 0.7,
                MuscleGroup.FRONT_DELTS to 0.5,
                MuscleGroup.SIDE_DELTS to 0.5,
                MuscleGroup.TRICEPS to 0.5
            )
        ),
        ex(
            "push-up-to-side-plank",
            "Push Up to Side Plank",
            setOf(EquipmentTag.BODYWEIGHT),
            setOf(MuscleGroup.CHEST_LOWER, MuscleGroup.CHEST_UPPER),
            setOf(MuscleGroup.ABS, MuscleGroup.SIDE_DELTS, MuscleGroup.TRICEPS),
            MovementPattern.HORIZONTAL_PUSH,
            involvements = mapOf(
                MuscleGroup.ABS to 0.3,
                MuscleGroup.CHEST_LOWER to 0.7,
                MuscleGroup.CHEST_UPPER to 0.7,
                MuscleGroup.SIDE_DELTS to 0.5,
                MuscleGroup.TRICEPS to 0.5
            )
        ),
        ex(
            "push-up-wide",
            "Push-Up Wide",
            setOf(EquipmentTag.BODYWEIGHT),
            setOf(MuscleGroup.CHEST_LOWER, MuscleGroup.CHEST_UPPER),
            setOf(
                MuscleGroup.ABS,
                MuscleGroup.FRONT_DELTS,
                MuscleGroup.SIDE_DELTS,
                MuscleGroup.TRICEPS
            ),
            MovementPattern.HORIZONTAL_PUSH,
            involvements = mapOf(
                MuscleGroup.ABS to 0.3,
                MuscleGroup.CHEST_LOWER to 0.7,
                MuscleGroup.CHEST_UPPER to 0.7,
                MuscleGroup.FRONT_DELTS to 0.5,
                MuscleGroup.SIDE_DELTS to 0.5,
                MuscleGroup.TRICEPS to 0.5
            )
        ),
        ex(
            "single-arm-push-up",
            "Single-Arm Push-Up",
            setOf(EquipmentTag.BODYWEIGHT),
            setOf(MuscleGroup.CHEST_LOWER, MuscleGroup.CHEST_UPPER),
            setOf(MuscleGroup.FRONT_DELTS, MuscleGroup.SIDE_DELTS, MuscleGroup.TRICEPS),
            MovementPattern.HORIZONTAL_PUSH,
            isUnilateral = true,
            involvements = mapOf(
                MuscleGroup.CHEST_LOWER to 0.7,
                MuscleGroup.CHEST_UPPER to 0.7,
                MuscleGroup.FRONT_DELTS to 0.5,
                MuscleGroup.SIDE_DELTS to 0.5,
                MuscleGroup.TRICEPS to 0.5
            )
        ),
        ex(
            "cable-shoulder-press",
            "Cable Shoulder Press",
            setOf(EquipmentTag.CABLE_MACHINE),
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
            "dip-machine",
            "Dip Machine",
            setOf(EquipmentTag.DIP_BAR),
            setOf(MuscleGroup.TRICEPS),
            setOf(
                MuscleGroup.CHEST_LOWER,
                MuscleGroup.CHEST_UPPER,
                MuscleGroup.FRONT_DELTS,
                MuscleGroup.SIDE_DELTS
            ),
            MovementPattern.VERTICAL_PUSH,
            involvements = mapOf(
                MuscleGroup.CHEST_LOWER to 0.5,
                MuscleGroup.CHEST_UPPER to 0.5,
                MuscleGroup.FRONT_DELTS to 0.5,
                MuscleGroup.SIDE_DELTS to 0.5,
                MuscleGroup.TRICEPS to 0.7
            )
        ),
        ex(
            "double-kettlebell-push-press",
            "Double Kettlebell Push Press",
            setOf(EquipmentTag.KETTLEBELL),
            setOf(MuscleGroup.FRONT_DELTS, MuscleGroup.SIDE_DELTS),
            setOf(MuscleGroup.CALVES, MuscleGroup.QUADS, MuscleGroup.TRICEPS),
            MovementPattern.VERTICAL_PUSH,
            involvements = mapOf(
                MuscleGroup.CALVES to 0.5,
                MuscleGroup.FRONT_DELTS to 0.7,
                MuscleGroup.QUADS to 0.5,
                MuscleGroup.SIDE_DELTS to 0.7,
                MuscleGroup.TRICEPS to 0.5
            )
        ),
        ex(
            "push-press",
            "Push Press",
            setOf(EquipmentTag.BARBELL),
            setOf(MuscleGroup.FRONT_DELTS, MuscleGroup.SIDE_DELTS),
            setOf(MuscleGroup.QUADS, MuscleGroup.TRICEPS),
            MovementPattern.VERTICAL_PUSH,
            involvements = mapOf(
                MuscleGroup.FRONT_DELTS to 0.7,
                MuscleGroup.QUADS to 0.5,
                MuscleGroup.SIDE_DELTS to 0.7,
                MuscleGroup.TRICEPS to 0.5
            )
        ),
        ex(
            "push-press-behind-the-neck",
            "Push Press - Behind the Neck",
            setOf(EquipmentTag.BARBELL),
            setOf(MuscleGroup.FRONT_DELTS, MuscleGroup.SIDE_DELTS),
            setOf(MuscleGroup.CALVES, MuscleGroup.QUADS, MuscleGroup.TRICEPS),
            MovementPattern.VERTICAL_PUSH,
            involvements = mapOf(
                MuscleGroup.CALVES to 0.5,
                MuscleGroup.FRONT_DELTS to 0.7,
                MuscleGroup.QUADS to 0.5,
                MuscleGroup.SIDE_DELTS to 0.7,
                MuscleGroup.TRICEPS to 0.5
            )
        ),
        ex(
            "seated-cable-shoulder-press",
            "Seated Cable Shoulder Press",
            setOf(EquipmentTag.CABLE_MACHINE),
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
            "shoulder-press-with-bands",
            "Shoulder Press - With Bands",
            setOf(EquipmentTag.RESISTANCE_BAND),
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
            "standing-military-press",
            "Standing Military Press",
            setOf(EquipmentTag.BARBELL),
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
            "barbell-rear-delt-row",
            "Barbell Rear Delt Row",
            setOf(EquipmentTag.BARBELL),
            setOf(MuscleGroup.REAR_DELTS),
            setOf(MuscleGroup.BICEPS, MuscleGroup.LATS, MuscleGroup.UPPER_BACK),
            MovementPattern.HORIZONTAL_PULL,
            involvements = mapOf(
                MuscleGroup.BICEPS to 0.5,
                MuscleGroup.LATS to 0.5,
                MuscleGroup.REAR_DELTS to 0.7,
                MuscleGroup.UPPER_BACK to 0.5
            )
        ),
        ex(
            "bent-over-barbell-row",
            "Bent Over Barbell Row",
            setOf(EquipmentTag.BARBELL),
            setOf(MuscleGroup.UPPER_BACK),
            setOf(
                MuscleGroup.BICEPS,
                MuscleGroup.FRONT_DELTS,
                MuscleGroup.LATS,
                MuscleGroup.SIDE_DELTS
            ),
            MovementPattern.HORIZONTAL_PULL,
            involvements = mapOf(
                MuscleGroup.BICEPS to 0.5,
                MuscleGroup.FRONT_DELTS to 0.5,
                MuscleGroup.LATS to 0.5,
                MuscleGroup.SIDE_DELTS to 0.5,
                MuscleGroup.UPPER_BACK to 0.7
            )
        ),
        ex(
            "dumbbell-incline-row",
            "Dumbbell Incline Row",
            setOf(EquipmentTag.DUMBBELL),
            setOf(MuscleGroup.UPPER_BACK),
            setOf(
                MuscleGroup.BICEPS,
                MuscleGroup.FOREARMS,
                MuscleGroup.FRONT_DELTS,
                MuscleGroup.LATS,
                MuscleGroup.SIDE_DELTS
            ),
            MovementPattern.HORIZONTAL_PULL,
            involvements = mapOf(
                MuscleGroup.BICEPS to 0.5,
                MuscleGroup.FOREARMS to 0.3,
                MuscleGroup.FRONT_DELTS to 0.5,
                MuscleGroup.LATS to 0.5,
                MuscleGroup.SIDE_DELTS to 0.5,
                MuscleGroup.UPPER_BACK to 0.7
            )
        ),
        ex(
            "one-arm-dumbbell-row",
            "One-Arm Dumbbell Row",
            setOf(EquipmentTag.DUMBBELL),
            setOf(MuscleGroup.UPPER_BACK),
            setOf(
                MuscleGroup.BICEPS,
                MuscleGroup.FRONT_DELTS,
                MuscleGroup.LATS,
                MuscleGroup.SIDE_DELTS
            ),
            MovementPattern.HORIZONTAL_PULL,
            isUnilateral = true,
            involvements = mapOf(
                MuscleGroup.BICEPS to 0.5,
                MuscleGroup.FRONT_DELTS to 0.5,
                MuscleGroup.LATS to 0.5,
                MuscleGroup.SIDE_DELTS to 0.5,
                MuscleGroup.UPPER_BACK to 0.7
            )
        ),
        ex(
            "shotgun-row",
            "Shotgun Row",
            setOf(EquipmentTag.CABLE_MACHINE),
            setOf(MuscleGroup.LATS),
            setOf(MuscleGroup.BICEPS, MuscleGroup.UPPER_BACK),
            MovementPattern.HORIZONTAL_PULL,
            involvements = mapOf(
                MuscleGroup.BICEPS to 0.5,
                MuscleGroup.LATS to 0.7,
                MuscleGroup.UPPER_BACK to 0.5
            )
        ),
        ex(
            "t-bar-row-with-handle",
            "T-Bar Row with Handle",
            setOf(EquipmentTag.BARBELL),
            setOf(MuscleGroup.UPPER_BACK),
            setOf(MuscleGroup.BICEPS, MuscleGroup.LATS),
            MovementPattern.HORIZONTAL_PULL,
            involvements = mapOf(
                MuscleGroup.BICEPS to 0.5,
                MuscleGroup.LATS to 0.5,
                MuscleGroup.UPPER_BACK to 0.7
            )
        ),
        ex(
            "upright-barbell-row",
            "Upright Barbell Row",
            setOf(EquipmentTag.BARBELL),
            setOf(MuscleGroup.FRONT_DELTS, MuscleGroup.SIDE_DELTS),
            setOf(MuscleGroup.UPPER_BACK),
            MovementPattern.HORIZONTAL_PULL,
            involvements = mapOf(
                MuscleGroup.FRONT_DELTS to 0.7,
                MuscleGroup.SIDE_DELTS to 0.7,
                MuscleGroup.UPPER_BACK to 0.5
            )
        ),
        ex(
            "upright-cable-row",
            "Upright Cable Row",
            setOf(EquipmentTag.CABLE_MACHINE),
            setOf(MuscleGroup.TRAPS),
            setOf(MuscleGroup.FRONT_DELTS, MuscleGroup.SIDE_DELTS),
            MovementPattern.VERTICAL_PULL,
            involvements = mapOf(
                MuscleGroup.FRONT_DELTS to 0.5,
                MuscleGroup.SIDE_DELTS to 0.5,
                MuscleGroup.TRAPS to 0.7
            )
        ),
        ex(
            "close-grip-front-lat-pulldown",
            "Close-Grip Front Lat Pulldown",
            setOf(EquipmentTag.CABLE_MACHINE),
            setOf(MuscleGroup.LATS),
            setOf(MuscleGroup.BICEPS, MuscleGroup.FRONT_DELTS, MuscleGroup.UPPER_BACK),
            MovementPattern.VERTICAL_PULL,
            involvements = mapOf(
                MuscleGroup.BICEPS to 0.5,
                MuscleGroup.FRONT_DELTS to 0.5,
                MuscleGroup.LATS to 0.7,
                MuscleGroup.UPPER_BACK to 0.5
            )
        ),
        ex(
            "full-range-of-motion-lat-pulldown",
            "Full Range-Of-Motion Lat Pulldown",
            setOf(EquipmentTag.CABLE_MACHINE),
            setOf(MuscleGroup.LATS),
            setOf(
                MuscleGroup.BICEPS,
                MuscleGroup.FRONT_DELTS,
                MuscleGroup.SIDE_DELTS,
                MuscleGroup.UPPER_BACK
            ),
            MovementPattern.VERTICAL_PULL,
            involvements = mapOf(
                MuscleGroup.BICEPS to 0.5,
                MuscleGroup.FRONT_DELTS to 0.5,
                MuscleGroup.LATS to 0.7,
                MuscleGroup.SIDE_DELTS to 0.5,
                MuscleGroup.UPPER_BACK to 0.5
            )
        ),
        ex(
            "one-arm-lat-pulldown",
            "One Arm Lat Pulldown",
            setOf(EquipmentTag.CABLE_MACHINE),
            setOf(MuscleGroup.LATS),
            setOf(MuscleGroup.BICEPS, MuscleGroup.UPPER_BACK),
            MovementPattern.VERTICAL_PULL,
            isUnilateral = true,
            involvements = mapOf(
                MuscleGroup.BICEPS to 0.5,
                MuscleGroup.LATS to 0.7,
                MuscleGroup.UPPER_BACK to 0.5
            )
        ),
        ex(
            "underhand-cable-pulldowns",
            "Underhand Cable Pulldowns",
            setOf(EquipmentTag.CABLE_MACHINE),
            setOf(MuscleGroup.LATS),
            setOf(
                MuscleGroup.BICEPS,
                MuscleGroup.FRONT_DELTS,
                MuscleGroup.SIDE_DELTS,
                MuscleGroup.UPPER_BACK
            ),
            MovementPattern.VERTICAL_PULL,
            involvements = mapOf(
                MuscleGroup.BICEPS to 0.5,
                MuscleGroup.FRONT_DELTS to 0.5,
                MuscleGroup.LATS to 0.7,
                MuscleGroup.SIDE_DELTS to 0.5,
                MuscleGroup.UPPER_BACK to 0.5
            )
        ),
        ex(
            "v-bar-pulldown",
            "V-Bar Pulldown",
            setOf(EquipmentTag.CABLE_MACHINE),
            setOf(MuscleGroup.LATS),
            setOf(
                MuscleGroup.BICEPS,
                MuscleGroup.FRONT_DELTS,
                MuscleGroup.SIDE_DELTS,
                MuscleGroup.UPPER_BACK
            ),
            MovementPattern.VERTICAL_PULL,
            involvements = mapOf(
                MuscleGroup.BICEPS to 0.5,
                MuscleGroup.FRONT_DELTS to 0.5,
                MuscleGroup.LATS to 0.7,
                MuscleGroup.SIDE_DELTS to 0.5,
                MuscleGroup.UPPER_BACK to 0.5
            )
        ),
        ex(
            "v-bar-pullup",
            "V-Bar Pullup",
            setOf(EquipmentTag.BODYWEIGHT),
            setOf(MuscleGroup.LATS),
            setOf(
                MuscleGroup.BICEPS,
                MuscleGroup.FRONT_DELTS,
                MuscleGroup.SIDE_DELTS,
                MuscleGroup.UPPER_BACK
            ),
            MovementPattern.VERTICAL_PULL,
            involvements = mapOf(
                MuscleGroup.BICEPS to 0.5,
                MuscleGroup.FRONT_DELTS to 0.5,
                MuscleGroup.LATS to 0.7,
                MuscleGroup.SIDE_DELTS to 0.5,
                MuscleGroup.UPPER_BACK to 0.5
            )
        ),
        ex(
            "wide-grip-pulldown-behind-the-neck",
            "Wide-Grip Pulldown Behind The Neck",
            setOf(EquipmentTag.CABLE_MACHINE),
            setOf(MuscleGroup.LATS),
            setOf(
                MuscleGroup.BICEPS,
                MuscleGroup.FRONT_DELTS,
                MuscleGroup.SIDE_DELTS,
                MuscleGroup.UPPER_BACK
            ),
            MovementPattern.VERTICAL_PULL,
            involvements = mapOf(
                MuscleGroup.BICEPS to 0.5,
                MuscleGroup.FRONT_DELTS to 0.5,
                MuscleGroup.LATS to 0.7,
                MuscleGroup.SIDE_DELTS to 0.5,
                MuscleGroup.UPPER_BACK to 0.5
            )
        ),
        ex(
            "wide-grip-rear-pull-up",
            "Wide-Grip Rear Pull-Up",
            setOf(EquipmentTag.BODYWEIGHT),
            setOf(MuscleGroup.LATS),
            setOf(MuscleGroup.BICEPS, MuscleGroup.REAR_DELTS, MuscleGroup.UPPER_BACK),
            MovementPattern.VERTICAL_PULL,
            involvements = mapOf(
                MuscleGroup.BICEPS to 0.5,
                MuscleGroup.LATS to 0.7,
                MuscleGroup.REAR_DELTS to 0.5,
                MuscleGroup.UPPER_BACK to 0.5
            )
        ),
        ex(
            "box-squat",
            "Box Squat",
            setOf(EquipmentTag.BARBELL),
            setOf(MuscleGroup.QUADS),
            setOf(
                MuscleGroup.ADDUCTORS,
                MuscleGroup.CALVES,
                MuscleGroup.GLUTES,
                MuscleGroup.HAMSTRINGS,
                MuscleGroup.LOWER_BACK
            ),
            MovementPattern.SQUAT,
            involvements = mapOf(
                MuscleGroup.ADDUCTORS to 0.5,
                MuscleGroup.CALVES to 0.5,
                MuscleGroup.GLUTES to 0.5,
                MuscleGroup.HAMSTRINGS to 0.5,
                MuscleGroup.LOWER_BACK to 0.3,
                MuscleGroup.QUADS to 1.0
            )
        ),
        ex(
            "dumbbell-squat",
            "Dumbbell Squat",
            setOf(EquipmentTag.DUMBBELL),
            setOf(MuscleGroup.QUADS),
            setOf(
                MuscleGroup.CALVES,
                MuscleGroup.GLUTES,
                MuscleGroup.HAMSTRINGS,
                MuscleGroup.LOWER_BACK
            ),
            MovementPattern.SQUAT,
            involvements = mapOf(
                MuscleGroup.CALVES to 0.5,
                MuscleGroup.GLUTES to 0.5,
                MuscleGroup.HAMSTRINGS to 0.5,
                MuscleGroup.LOWER_BACK to 0.3,
                MuscleGroup.QUADS to 1.0
            )
        ),
        ex(
            "hack-squat",
            "Hack Squat",
            setOf(EquipmentTag.HACK_SQUAT_MACHINE),
            setOf(MuscleGroup.QUADS),
            setOf(MuscleGroup.CALVES, MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS),
            MovementPattern.SQUAT,
            involvements = mapOf(
                MuscleGroup.CALVES to 0.5,
                MuscleGroup.GLUTES to 0.5,
                MuscleGroup.HAMSTRINGS to 0.5,
                MuscleGroup.QUADS to 1.0
            )
        ),
        ex(
            "kneeling-squat",
            "Kneeling Squat",
            setOf(EquipmentTag.BARBELL),
            setOf(MuscleGroup.GLUTES),
            setOf(
                MuscleGroup.ABS,
                MuscleGroup.HAMSTRINGS,
                MuscleGroup.LOWER_BACK,
                MuscleGroup.QUADS
            ),
            MovementPattern.SQUAT,
            involvements = mapOf(
                MuscleGroup.ABS to 0.3,
                MuscleGroup.GLUTES to 1.0,
                MuscleGroup.HAMSTRINGS to 0.5,
                MuscleGroup.LOWER_BACK to 0.3,
                MuscleGroup.QUADS to 0.5
            )
        ),
        ex(
            "olympic-squat",
            "Olympic Squat",
            setOf(EquipmentTag.BARBELL),
            setOf(MuscleGroup.QUADS),
            setOf(MuscleGroup.CALVES, MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS),
            MovementPattern.SQUAT,
            involvements = mapOf(
                MuscleGroup.CALVES to 0.5,
                MuscleGroup.GLUTES to 0.5,
                MuscleGroup.HAMSTRINGS to 0.5,
                MuscleGroup.QUADS to 1.0
            )
        ),
        ex(
            "overhead-squat",
            "Overhead Squat",
            setOf(EquipmentTag.BARBELL),
            setOf(MuscleGroup.QUADS),
            setOf(
                MuscleGroup.ABS,
                MuscleGroup.CALVES,
                MuscleGroup.FRONT_DELTS,
                MuscleGroup.GLUTES,
                MuscleGroup.HAMSTRINGS,
                MuscleGroup.LOWER_BACK,
                MuscleGroup.SIDE_DELTS,
                MuscleGroup.TRICEPS
            ),
            MovementPattern.SQUAT,
            involvements = mapOf(
                MuscleGroup.ABS to 0.3,
                MuscleGroup.CALVES to 0.5,
                MuscleGroup.FRONT_DELTS to 0.5,
                MuscleGroup.GLUTES to 0.5,
                MuscleGroup.HAMSTRINGS to 0.5,
                MuscleGroup.LOWER_BACK to 0.3,
                MuscleGroup.QUADS to 1.0,
                MuscleGroup.SIDE_DELTS to 0.5,
                MuscleGroup.TRICEPS to 0.5
            )
        ),
        ex(
            "squat-jerk",
            "Squat Jerk",
            setOf(EquipmentTag.BARBELL),
            setOf(MuscleGroup.QUADS),
            setOf(
                MuscleGroup.CALVES,
                MuscleGroup.FRONT_DELTS,
                MuscleGroup.GLUTES,
                MuscleGroup.HAMSTRINGS,
                MuscleGroup.SIDE_DELTS,
                MuscleGroup.TRICEPS
            ),
            MovementPattern.SQUAT,
            involvements = mapOf(
                MuscleGroup.CALVES to 0.5,
                MuscleGroup.FRONT_DELTS to 0.5,
                MuscleGroup.GLUTES to 0.5,
                MuscleGroup.HAMSTRINGS to 0.5,
                MuscleGroup.QUADS to 1.0,
                MuscleGroup.SIDE_DELTS to 0.5,
                MuscleGroup.TRICEPS to 0.5
            )
        ),
        ex(
            "squat-with-bands",
            "Squat with Bands",
            setOf(EquipmentTag.BARBELL),
            setOf(MuscleGroup.QUADS),
            setOf(
                MuscleGroup.ADDUCTORS,
                MuscleGroup.CALVES,
                MuscleGroup.GLUTES,
                MuscleGroup.HAMSTRINGS,
                MuscleGroup.LOWER_BACK
            ),
            MovementPattern.SQUAT,
            involvements = mapOf(
                MuscleGroup.ADDUCTORS to 0.5,
                MuscleGroup.CALVES to 0.5,
                MuscleGroup.GLUTES to 0.5,
                MuscleGroup.HAMSTRINGS to 0.5,
                MuscleGroup.LOWER_BACK to 0.3,
                MuscleGroup.QUADS to 1.0
            )
        ),
        ex(
            "band-good-morning",
            "Band Good Morning",
            setOf(EquipmentTag.RESISTANCE_BAND),
            setOf(MuscleGroup.HAMSTRINGS),
            setOf(MuscleGroup.GLUTES, MuscleGroup.LOWER_BACK),
            MovementPattern.HINGE,
            involvements = mapOf(
                MuscleGroup.GLUTES to 0.5,
                MuscleGroup.HAMSTRINGS to 1.0,
                MuscleGroup.LOWER_BACK to 0.3
            )
        ),
        ex(
            "barbell-hip-thrust",
            "Barbell Hip Thrust",
            setOf(EquipmentTag.BARBELL),
            setOf(MuscleGroup.GLUTES),
            setOf(MuscleGroup.CALVES, MuscleGroup.HAMSTRINGS),
            MovementPattern.HINGE,
            involvements = mapOf(
                MuscleGroup.CALVES to 0.5,
                MuscleGroup.GLUTES to 1.0,
                MuscleGroup.HAMSTRINGS to 0.5
            )
        ),
        ex(
            "cable-deadlifts",
            "Cable Deadlifts",
            setOf(EquipmentTag.CABLE_MACHINE),
            setOf(MuscleGroup.QUADS),
            setOf(
                MuscleGroup.FOREARMS,
                MuscleGroup.GLUTES,
                MuscleGroup.HAMSTRINGS,
                MuscleGroup.LOWER_BACK
            ),
            MovementPattern.HINGE,
            involvements = mapOf(
                MuscleGroup.FOREARMS to 0.3,
                MuscleGroup.GLUTES to 0.5,
                MuscleGroup.HAMSTRINGS to 0.5,
                MuscleGroup.LOWER_BACK to 0.3,
                MuscleGroup.QUADS to 1.0
            )
        ),
        ex(
            "deadlift-with-bands",
            "Deadlift with Bands",
            setOf(EquipmentTag.BARBELL),
            setOf(MuscleGroup.LOWER_BACK),
            setOf(
                MuscleGroup.FOREARMS,
                MuscleGroup.GLUTES,
                MuscleGroup.HAMSTRINGS,
                MuscleGroup.QUADS,
                MuscleGroup.UPPER_BACK
            ),
            MovementPattern.HINGE,
            involvements = mapOf(
                MuscleGroup.FOREARMS to 0.3,
                MuscleGroup.GLUTES to 0.5,
                MuscleGroup.HAMSTRINGS to 0.5,
                MuscleGroup.LOWER_BACK to 1.0,
                MuscleGroup.QUADS to 0.5,
                MuscleGroup.UPPER_BACK to 0.5
            )
        ),
        ex(
            "deadlift-with-chains",
            "Deadlift with Chains",
            setOf(EquipmentTag.BARBELL),
            setOf(MuscleGroup.LOWER_BACK),
            setOf(
                MuscleGroup.FOREARMS,
                MuscleGroup.GLUTES,
                MuscleGroup.HAMSTRINGS,
                MuscleGroup.QUADS,
                MuscleGroup.UPPER_BACK
            ),
            MovementPattern.HINGE,
            involvements = mapOf(
                MuscleGroup.FOREARMS to 0.3,
                MuscleGroup.GLUTES to 0.5,
                MuscleGroup.HAMSTRINGS to 0.5,
                MuscleGroup.LOWER_BACK to 1.0,
                MuscleGroup.QUADS to 0.5,
                MuscleGroup.UPPER_BACK to 0.5
            )
        ),
        ex(
            "deficit-deadlift",
            "Deficit Deadlift",
            setOf(EquipmentTag.BARBELL),
            setOf(MuscleGroup.LOWER_BACK),
            setOf(
                MuscleGroup.FOREARMS,
                MuscleGroup.GLUTES,
                MuscleGroup.HAMSTRINGS,
                MuscleGroup.QUADS,
                MuscleGroup.UPPER_BACK
            ),
            MovementPattern.HINGE,
            involvements = mapOf(
                MuscleGroup.FOREARMS to 0.3,
                MuscleGroup.GLUTES to 0.5,
                MuscleGroup.HAMSTRINGS to 0.5,
                MuscleGroup.LOWER_BACK to 1.0,
                MuscleGroup.QUADS to 0.5,
                MuscleGroup.UPPER_BACK to 0.5
            )
        ),
        ex(
            "good-morning",
            "Good Morning",
            setOf(EquipmentTag.BARBELL),
            setOf(MuscleGroup.HAMSTRINGS),
            setOf(MuscleGroup.ABS, MuscleGroup.GLUTES, MuscleGroup.LOWER_BACK),
            MovementPattern.HINGE,
            involvements = mapOf(
                MuscleGroup.ABS to 0.3,
                MuscleGroup.GLUTES to 0.5,
                MuscleGroup.HAMSTRINGS to 1.0,
                MuscleGroup.LOWER_BACK to 0.3
            )
        ),
        ex(
            "sumo-deadlift",
            "Sumo Deadlift",
            setOf(EquipmentTag.BARBELL),
            setOf(MuscleGroup.HAMSTRINGS),
            setOf(
                MuscleGroup.ADDUCTORS,
                MuscleGroup.FOREARMS,
                MuscleGroup.GLUTES,
                MuscleGroup.LOWER_BACK,
                MuscleGroup.QUADS,
                MuscleGroup.UPPER_BACK
            ),
            MovementPattern.HINGE,
            involvements = mapOf(
                MuscleGroup.ADDUCTORS to 0.5,
                MuscleGroup.FOREARMS to 0.3,
                MuscleGroup.GLUTES to 0.5,
                MuscleGroup.HAMSTRINGS to 1.0,
                MuscleGroup.LOWER_BACK to 0.3,
                MuscleGroup.QUADS to 0.5,
                MuscleGroup.UPPER_BACK to 0.5
            )
        ),
        ex(
            "barbell-lunge",
            "Barbell Lunge",
            setOf(EquipmentTag.BARBELL),
            setOf(MuscleGroup.QUADS),
            setOf(MuscleGroup.CALVES, MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS),
            MovementPattern.LUNGE,
            isUnilateral = true,
            involvements = mapOf(
                MuscleGroup.CALVES to 0.5,
                MuscleGroup.GLUTES to 1.0,
                MuscleGroup.HAMSTRINGS to 0.5,
                MuscleGroup.QUADS to 1.0
            )
        ),
        ex(
            "barbell-step-ups",
            "Barbell Step Ups",
            setOf(EquipmentTag.BARBELL),
            setOf(MuscleGroup.QUADS),
            setOf(MuscleGroup.CALVES, MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS),
            MovementPattern.LUNGE,
            isUnilateral = true,
            involvements = mapOf(
                MuscleGroup.CALVES to 0.5,
                MuscleGroup.GLUTES to 1.0,
                MuscleGroup.HAMSTRINGS to 0.5,
                MuscleGroup.QUADS to 1.0
            )
        ),
        ex(
            "barbell-walking-lunge",
            "Barbell Walking Lunge",
            setOf(EquipmentTag.BARBELL),
            setOf(MuscleGroup.QUADS),
            setOf(MuscleGroup.CALVES, MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS),
            MovementPattern.LUNGE,
            isUnilateral = true,
            involvements = mapOf(
                MuscleGroup.CALVES to 0.5,
                MuscleGroup.GLUTES to 1.0,
                MuscleGroup.HAMSTRINGS to 0.5,
                MuscleGroup.QUADS to 1.0
            )
        ),
        ex(
            "dumbbell-rear-lunge",
            "Dumbbell Rear Lunge",
            setOf(EquipmentTag.DUMBBELL),
            setOf(MuscleGroup.QUADS),
            setOf(MuscleGroup.CALVES, MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS),
            MovementPattern.LUNGE,
            isUnilateral = true,
            involvements = mapOf(
                MuscleGroup.CALVES to 0.5,
                MuscleGroup.GLUTES to 1.0,
                MuscleGroup.HAMSTRINGS to 0.5,
                MuscleGroup.QUADS to 1.0
            )
        ),
        ex(
            "dumbbell-step-ups",
            "Dumbbell Step Ups",
            setOf(EquipmentTag.DUMBBELL),
            setOf(MuscleGroup.QUADS),
            setOf(MuscleGroup.CALVES, MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS),
            MovementPattern.LUNGE,
            isUnilateral = true,
            involvements = mapOf(
                MuscleGroup.CALVES to 0.5,
                MuscleGroup.GLUTES to 1.0,
                MuscleGroup.HAMSTRINGS to 0.5,
                MuscleGroup.QUADS to 1.0
            )
        ),
        ex(
            "elevated-back-lunge",
            "Elevated Back Lunge",
            setOf(EquipmentTag.BARBELL),
            setOf(MuscleGroup.QUADS),
            setOf(MuscleGroup.GLUTES, MuscleGroup.HAMSTRINGS),
            MovementPattern.LUNGE,
            isUnilateral = true,
            involvements = mapOf(
                MuscleGroup.GLUTES to 1.0,
                MuscleGroup.HAMSTRINGS to 0.5,
                MuscleGroup.QUADS to 1.0
            )
        ),
        ex(
            "lunge-pass-through",
            "Lunge Pass Through",
            setOf(EquipmentTag.KETTLEBELL),
            setOf(MuscleGroup.HAMSTRINGS),
            setOf(MuscleGroup.CALVES, MuscleGroup.GLUTES, MuscleGroup.QUADS),
            MovementPattern.LUNGE,
            isUnilateral = true,
            involvements = mapOf(
                MuscleGroup.CALVES to 0.5,
                MuscleGroup.GLUTES to 1.0,
                MuscleGroup.HAMSTRINGS to 1.0,
                MuscleGroup.QUADS to 0.5
            )
        ),
        ex(
            "step-up-with-knee-raise",
            "Step-up with Knee Raise",
            setOf(EquipmentTag.BODYWEIGHT),
            setOf(MuscleGroup.GLUTES),
            setOf(MuscleGroup.HAMSTRINGS, MuscleGroup.QUADS),
            MovementPattern.LUNGE,
            isUnilateral = true,
            involvements = mapOf(
                MuscleGroup.GLUTES to 1.0,
                MuscleGroup.HAMSTRINGS to 0.5,
                MuscleGroup.QUADS to 0.5
            )
        ),
        ex(
            "barbell-seated-calf-raise",
            "Barbell Seated Calf Raise",
            setOf(EquipmentTag.BARBELL),
            setOf(MuscleGroup.CALVES),
            emptySet(),
            MovementPattern.CALF_RAISE,
            involvements = mapOf(
                MuscleGroup.CALVES to 1.0
            )
        ),
        ex(
            "calf-press",
            "Calf Press",
            setOf(EquipmentTag.CALF_RAISE_MACHINE),
            setOf(MuscleGroup.CALVES),
            emptySet(),
            MovementPattern.CALF_RAISE,
            involvements = mapOf(
                MuscleGroup.CALVES to 1.0
            )
        ),
        ex(
            "calf-raise-on-a-dumbbell",
            "Calf Raise On A Dumbbell",
            setOf(EquipmentTag.DUMBBELL),
            setOf(MuscleGroup.CALVES),
            emptySet(),
            MovementPattern.CALF_RAISE,
            involvements = mapOf(
                MuscleGroup.CALVES to 1.0
            )
        ),
        ex(
            "calf-raises-with-bands",
            "Calf Raises - With Bands",
            setOf(EquipmentTag.RESISTANCE_BAND),
            setOf(MuscleGroup.CALVES),
            emptySet(),
            MovementPattern.CALF_RAISE,
            involvements = mapOf(
                MuscleGroup.CALVES to 1.0
            )
        ),
        ex(
            "seated-calf-raise",
            "Seated Calf Raise",
            setOf(EquipmentTag.CALF_RAISE_MACHINE),
            setOf(MuscleGroup.CALVES),
            emptySet(),
            MovementPattern.CALF_RAISE,
            involvements = mapOf(
                MuscleGroup.CALVES to 1.0
            )
        ),
        ex(
            "smith-machine-calf-raise",
            "Smith Machine Calf Raise",
            setOf(EquipmentTag.SMITH_MACHINE),
            setOf(MuscleGroup.CALVES),
            emptySet(),
            MovementPattern.CALF_RAISE,
            involvements = mapOf(
                MuscleGroup.CALVES to 1.0
            )
        ),
        ex(
            "standing-barbell-calf-raise",
            "Standing Barbell Calf Raise",
            setOf(EquipmentTag.BARBELL),
            setOf(MuscleGroup.CALVES),
            emptySet(),
            MovementPattern.CALF_RAISE,
            involvements = mapOf(
                MuscleGroup.CALVES to 1.0
            )
        ),
        ex(
            "standing-dumbbell-calf-raise",
            "Standing Dumbbell Calf Raise",
            setOf(EquipmentTag.DUMBBELL),
            setOf(MuscleGroup.CALVES),
            emptySet(),
            MovementPattern.CALF_RAISE,
            involvements = mapOf(
                MuscleGroup.CALVES to 1.0
            )
        ),
        ex(
            "cable-crossover",
            "Cable Crossover",
            setOf(EquipmentTag.CABLE_MACHINE),
            setOf(MuscleGroup.CHEST_LOWER, MuscleGroup.CHEST_UPPER),
            setOf(MuscleGroup.FRONT_DELTS, MuscleGroup.SIDE_DELTS),
            MovementPattern.CHEST_FLY,
            involvements = mapOf(
                MuscleGroup.CHEST_LOWER to 0.7,
                MuscleGroup.CHEST_UPPER to 0.7,
                MuscleGroup.FRONT_DELTS to 0.5,
                MuscleGroup.SIDE_DELTS to 0.5
            )
        ),
        ex(
            "cable-iron-cross",
            "Cable Iron Cross",
            setOf(EquipmentTag.CABLE_MACHINE),
            setOf(MuscleGroup.CHEST_LOWER, MuscleGroup.CHEST_UPPER),
            emptySet(),
            MovementPattern.CHEST_FLY,
            involvements = mapOf(
                MuscleGroup.CHEST_LOWER to 0.7,
                MuscleGroup.CHEST_UPPER to 0.7
            )
        ),
        ex(
            "decline-dumbbell-flyes",
            "Decline Dumbbell Flyes",
            setOf(EquipmentTag.DUMBBELL),
            setOf(MuscleGroup.CHEST_LOWER),
            emptySet(),
            MovementPattern.CHEST_FLY,
            involvements = mapOf(
                MuscleGroup.CHEST_LOWER to 0.7
            )
        ),
        ex(
            "dumbbell-flyes",
            "Dumbbell Flyes",
            setOf(EquipmentTag.DUMBBELL),
            setOf(MuscleGroup.CHEST_LOWER, MuscleGroup.CHEST_UPPER),
            emptySet(),
            MovementPattern.CHEST_FLY,
            involvements = mapOf(
                MuscleGroup.CHEST_LOWER to 0.7,
                MuscleGroup.CHEST_UPPER to 0.7
            )
        ),
        ex(
            "incline-cable-flye",
            "Incline Cable Flye",
            setOf(EquipmentTag.CABLE_MACHINE),
            setOf(MuscleGroup.CHEST_UPPER),
            setOf(MuscleGroup.FRONT_DELTS, MuscleGroup.SIDE_DELTS),
            MovementPattern.CHEST_FLY,
            involvements = mapOf(
                MuscleGroup.CHEST_UPPER to 0.7,
                MuscleGroup.FRONT_DELTS to 0.5,
                MuscleGroup.SIDE_DELTS to 0.5
            )
        ),
        ex(
            "incline-dumbbell-flyes",
            "Incline Dumbbell Flyes",
            setOf(EquipmentTag.DUMBBELL),
            setOf(MuscleGroup.CHEST_UPPER),
            setOf(MuscleGroup.FRONT_DELTS, MuscleGroup.SIDE_DELTS),
            MovementPattern.CHEST_FLY,
            involvements = mapOf(
                MuscleGroup.CHEST_UPPER to 0.7,
                MuscleGroup.FRONT_DELTS to 0.5,
                MuscleGroup.SIDE_DELTS to 0.5
            )
        ),
        ex(
            "incline-dumbbell-flyes-with-a-twist",
            "Incline Dumbbell Flyes - With A Twist",
            setOf(EquipmentTag.DUMBBELL),
            setOf(MuscleGroup.CHEST_UPPER),
            setOf(MuscleGroup.FRONT_DELTS, MuscleGroup.SIDE_DELTS),
            MovementPattern.CHEST_FLY,
            involvements = mapOf(
                MuscleGroup.CHEST_UPPER to 0.7,
                MuscleGroup.FRONT_DELTS to 0.5,
                MuscleGroup.SIDE_DELTS to 0.5
            )
        ),
        ex(
            "low-cable-crossover",
            "Low Cable Crossover",
            setOf(EquipmentTag.CABLE_MACHINE),
            setOf(MuscleGroup.CHEST_LOWER, MuscleGroup.CHEST_UPPER),
            setOf(MuscleGroup.FRONT_DELTS, MuscleGroup.SIDE_DELTS),
            MovementPattern.CHEST_FLY,
            involvements = mapOf(
                MuscleGroup.CHEST_LOWER to 0.7,
                MuscleGroup.CHEST_UPPER to 0.7,
                MuscleGroup.FRONT_DELTS to 0.5,
                MuscleGroup.SIDE_DELTS to 0.5
            )
        ),
        ex(
            "cable-preacher-curl",
            "Cable Preacher Curl",
            setOf(EquipmentTag.CABLE_MACHINE),
            setOf(MuscleGroup.BICEPS),
            setOf(MuscleGroup.FOREARMS),
            MovementPattern.BICEPS_ISOLATION,
            involvements = mapOf(
                MuscleGroup.BICEPS to 1.0,
                MuscleGroup.FOREARMS to 0.3
            )
        ),
        ex(
            "concentration-curls",
            "Concentration Curls",
            setOf(EquipmentTag.DUMBBELL),
            setOf(MuscleGroup.BICEPS),
            setOf(MuscleGroup.FOREARMS),
            MovementPattern.BICEPS_ISOLATION,
            involvements = mapOf(
                MuscleGroup.BICEPS to 1.0,
                MuscleGroup.FOREARMS to 0.3
            )
        ),
        ex(
            "ez-bar-curl",
            "EZ-Bar Curl",
            setOf(EquipmentTag.EZ_BAR),
            setOf(MuscleGroup.BICEPS),
            emptySet(),
            MovementPattern.BICEPS_ISOLATION,
            involvements = mapOf(
                MuscleGroup.BICEPS to 1.0
            )
        ),
        ex(
            "incline-hammer-curls",
            "Incline Hammer Curls",
            setOf(EquipmentTag.DUMBBELL),
            setOf(MuscleGroup.BICEPS),
            emptySet(),
            MovementPattern.BICEPS_ISOLATION,
            involvements = mapOf(
                MuscleGroup.BICEPS to 1.0
            )
        ),
        ex(
            "lying-cable-curl",
            "Lying Cable Curl",
            setOf(EquipmentTag.CABLE_MACHINE),
            setOf(MuscleGroup.BICEPS),
            emptySet(),
            MovementPattern.BICEPS_ISOLATION,
            involvements = mapOf(
                MuscleGroup.BICEPS to 1.0
            )
        ),
        ex(
            "overhead-cable-curl",
            "Overhead Cable Curl",
            setOf(EquipmentTag.CABLE_MACHINE),
            setOf(MuscleGroup.BICEPS),
            emptySet(),
            MovementPattern.BICEPS_ISOLATION,
            involvements = mapOf(
                MuscleGroup.BICEPS to 1.0
            )
        ),
        ex(
            "reverse-cable-curl",
            "Reverse Cable Curl",
            setOf(EquipmentTag.CABLE_MACHINE),
            setOf(MuscleGroup.BICEPS),
            setOf(MuscleGroup.FOREARMS),
            MovementPattern.BICEPS_ISOLATION,
            involvements = mapOf(
                MuscleGroup.BICEPS to 1.0,
                MuscleGroup.FOREARMS to 0.3
            )
        ),
        ex(
            "spider-curl",
            "Spider Curl",
            setOf(EquipmentTag.EZ_BAR),
            setOf(MuscleGroup.BICEPS),
            emptySet(),
            MovementPattern.BICEPS_ISOLATION,
            involvements = mapOf(
                MuscleGroup.BICEPS to 1.0
            )
        ),
        ex(
            "band-skull-crusher",
            "Band Skull Crusher",
            setOf(EquipmentTag.RESISTANCE_BAND),
            setOf(MuscleGroup.TRICEPS),
            emptySet(),
            MovementPattern.TRICEPS_ISOLATION,
            involvements = mapOf(
                MuscleGroup.TRICEPS to 1.0
            )
        ),
        ex(
            "cable-lying-triceps-extension",
            "Cable Lying Triceps Extension",
            setOf(EquipmentTag.CABLE_MACHINE),
            setOf(MuscleGroup.TRICEPS),
            emptySet(),
            MovementPattern.TRICEPS_ISOLATION,
            involvements = mapOf(
                MuscleGroup.TRICEPS to 1.0
            )
        ),
        ex(
            "cable-one-arm-tricep-extension",
            "Cable One Arm Tricep Extension",
            setOf(EquipmentTag.CABLE_MACHINE),
            setOf(MuscleGroup.TRICEPS),
            emptySet(),
            MovementPattern.TRICEPS_ISOLATION,
            isUnilateral = true,
            involvements = mapOf(
                MuscleGroup.TRICEPS to 1.0
            )
        ),
        ex(
            "ez-bar-skullcrusher",
            "EZ-Bar Skullcrusher",
            setOf(EquipmentTag.EZ_BAR),
            setOf(MuscleGroup.TRICEPS),
            setOf(MuscleGroup.FOREARMS),
            MovementPattern.TRICEPS_ISOLATION,
            involvements = mapOf(
                MuscleGroup.FOREARMS to 0.3,
                MuscleGroup.TRICEPS to 1.0
            )
        ),
        ex(
            "low-cable-triceps-extension",
            "Low Cable Triceps Extension",
            setOf(EquipmentTag.CABLE_MACHINE),
            setOf(MuscleGroup.TRICEPS),
            emptySet(),
            MovementPattern.TRICEPS_ISOLATION,
            involvements = mapOf(
                MuscleGroup.TRICEPS to 1.0
            )
        ),
        ex(
            "lying-triceps-press",
            "Lying Triceps Press",
            setOf(EquipmentTag.EZ_BAR),
            setOf(MuscleGroup.TRICEPS),
            emptySet(),
            MovementPattern.TRICEPS_ISOLATION,
            involvements = mapOf(
                MuscleGroup.TRICEPS to 1.0
            )
        ),
        ex(
            "seated-triceps-press",
            "Seated Triceps Press",
            setOf(EquipmentTag.DUMBBELL),
            setOf(MuscleGroup.TRICEPS),
            emptySet(),
            MovementPattern.TRICEPS_ISOLATION,
            involvements = mapOf(
                MuscleGroup.TRICEPS to 1.0
            )
        ),
        ex(
            "tricep-dumbbell-kickback",
            "Tricep Dumbbell Kickback",
            setOf(EquipmentTag.DUMBBELL),
            setOf(MuscleGroup.TRICEPS),
            emptySet(),
            MovementPattern.TRICEPS_ISOLATION,
            involvements = mapOf(
                MuscleGroup.TRICEPS to 1.0
            )
        ),
        ex(
            "cable-rear-delt-fly",
            "Cable Rear Delt Fly",
            setOf(EquipmentTag.CABLE_MACHINE),
            setOf(MuscleGroup.REAR_DELTS),
            emptySet(),
            MovementPattern.SHOULDER_ISOLATION,
            involvements = mapOf(
                MuscleGroup.REAR_DELTS to 1.0
            )
        ),
        ex(
            "front-cable-raise",
            "Front Cable Raise",
            setOf(EquipmentTag.CABLE_MACHINE),
            setOf(MuscleGroup.FRONT_DELTS),
            emptySet(),
            MovementPattern.SHOULDER_ISOLATION,
            involvements = mapOf(
                MuscleGroup.FRONT_DELTS to 1.0
            )
        ),
        ex(
            "front-dumbbell-raise",
            "Front Dumbbell Raise",
            setOf(EquipmentTag.DUMBBELL),
            setOf(MuscleGroup.FRONT_DELTS),
            emptySet(),
            MovementPattern.SHOULDER_ISOLATION,
            involvements = mapOf(
                MuscleGroup.FRONT_DELTS to 1.0
            )
        ),
        ex(
            "front-two-dumbbell-raise",
            "Front Two-Dumbbell Raise",
            setOf(EquipmentTag.DUMBBELL),
            setOf(MuscleGroup.FRONT_DELTS),
            emptySet(),
            MovementPattern.SHOULDER_ISOLATION,
            involvements = mapOf(
                MuscleGroup.FRONT_DELTS to 1.0
            )
        ),
        ex(
            "lying-rear-delt-raise",
            "Lying Rear Delt Raise",
            setOf(EquipmentTag.DUMBBELL),
            setOf(MuscleGroup.REAR_DELTS),
            emptySet(),
            MovementPattern.SHOULDER_ISOLATION,
            involvements = mapOf(
                MuscleGroup.REAR_DELTS to 1.0
            )
        ),
        ex(
            "one-arm-side-laterals",
            "One-Arm Side Laterals",
            setOf(EquipmentTag.DUMBBELL),
            setOf(MuscleGroup.SIDE_DELTS),
            emptySet(),
            MovementPattern.SHOULDER_ISOLATION,
            isUnilateral = true,
            involvements = mapOf(
                MuscleGroup.SIDE_DELTS to 1.0
            )
        ),
        ex(
            "reverse-flyes",
            "Reverse Flyes",
            setOf(EquipmentTag.DUMBBELL),
            setOf(MuscleGroup.REAR_DELTS),
            emptySet(),
            MovementPattern.SHOULDER_ISOLATION,
            involvements = mapOf(
                MuscleGroup.REAR_DELTS to 1.0
            )
        ),
        ex(
            "side-lateral-raise",
            "Side Lateral Raise",
            setOf(EquipmentTag.DUMBBELL),
            setOf(MuscleGroup.SIDE_DELTS),
            emptySet(),
            MovementPattern.SHOULDER_ISOLATION,
            involvements = mapOf(
                MuscleGroup.SIDE_DELTS to 1.0
            )
        ),
        ex(
            "band-hip-adductions",
            "Band Hip Adductions",
            setOf(EquipmentTag.RESISTANCE_BAND),
            setOf(MuscleGroup.ADDUCTORS),
            emptySet(),
            MovementPattern.LEG_ISOLATION,
            involvements = mapOf(
                MuscleGroup.ADDUCTORS to 1.0
            )
        ),
        ex(
            "butt-lift-bridge",
            "Butt Lift (Bridge)",
            setOf(EquipmentTag.BODYWEIGHT),
            setOf(MuscleGroup.GLUTES),
            setOf(MuscleGroup.HAMSTRINGS),
            MovementPattern.LEG_ISOLATION,
            involvements = mapOf(
                MuscleGroup.GLUTES to 1.0,
                MuscleGroup.HAMSTRINGS to 0.5
            )
        ),
        ex(
            "cable-hip-adduction",
            "Cable Hip Adduction",
            setOf(EquipmentTag.CABLE_MACHINE),
            setOf(MuscleGroup.ADDUCTORS),
            emptySet(),
            MovementPattern.LEG_ISOLATION,
            involvements = mapOf(
                MuscleGroup.ADDUCTORS to 1.0
            )
        ),
        ex(
            "leg-lift",
            "Leg Lift",
            setOf(EquipmentTag.BODYWEIGHT),
            setOf(MuscleGroup.GLUTES),
            setOf(MuscleGroup.HAMSTRINGS),
            MovementPattern.LEG_ISOLATION,
            involvements = mapOf(
                MuscleGroup.GLUTES to 1.0,
                MuscleGroup.HAMSTRINGS to 0.5
            )
        ),
        ex(
            "lying-leg-curls",
            "Lying Leg Curls",
            setOf(EquipmentTag.LEG_CURL_MACHINE),
            setOf(MuscleGroup.HAMSTRINGS),
            emptySet(),
            MovementPattern.LEG_ISOLATION,
            involvements = mapOf(
                MuscleGroup.HAMSTRINGS to 1.0
            )
        ),
        ex(
            "seated-leg-curl",
            "Seated Leg Curl",
            setOf(EquipmentTag.LEG_CURL_MACHINE),
            setOf(MuscleGroup.HAMSTRINGS),
            emptySet(),
            MovementPattern.LEG_ISOLATION,
            involvements = mapOf(
                MuscleGroup.HAMSTRINGS to 1.0
            )
        ),
        ex(
            "single-leg-glute-bridge",
            "Single Leg Glute Bridge",
            setOf(EquipmentTag.BODYWEIGHT),
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
            "standing-leg-curl",
            "Standing Leg Curl",
            setOf(EquipmentTag.LEG_CURL_MACHINE),
            setOf(MuscleGroup.HAMSTRINGS),
            emptySet(),
            MovementPattern.LEG_ISOLATION,
            involvements = mapOf(
                MuscleGroup.HAMSTRINGS to 1.0
            )
        ),
        ex(
            "crunches",
            "Crunches",
            setOf(EquipmentTag.BODYWEIGHT),
            setOf(MuscleGroup.ABS),
            emptySet(),
            MovementPattern.CORE,
            involvements = mapOf(
                MuscleGroup.ABS to 1.0
            )
        ),
        ex(
            "decline-crunch",
            "Decline Crunch",
            setOf(EquipmentTag.BODYWEIGHT),
            setOf(MuscleGroup.ABS),
            emptySet(),
            MovementPattern.CORE,
            involvements = mapOf(
                MuscleGroup.ABS to 1.0
            )
        ),
        ex(
            "reverse-crunch",
            "Reverse Crunch",
            setOf(EquipmentTag.BODYWEIGHT),
            setOf(MuscleGroup.ABS),
            emptySet(),
            MovementPattern.CORE,
            involvements = mapOf(
                MuscleGroup.ABS to 1.0
            )
        ),
        ex(
            "rope-crunch",
            "Rope Crunch",
            setOf(EquipmentTag.CABLE_MACHINE),
            setOf(MuscleGroup.ABS),
            emptySet(),
            MovementPattern.CORE,
            involvements = mapOf(
                MuscleGroup.ABS to 1.0
            )
        ),
        ex(
            "scissor-kick",
            "Scissor Kick",
            setOf(EquipmentTag.BODYWEIGHT),
            setOf(MuscleGroup.ABS),
            emptySet(),
            MovementPattern.CORE,
            involvements = mapOf(
                MuscleGroup.ABS to 1.0
            )
        ),
        ex(
            "sit-up",
            "Sit-Up",
            setOf(EquipmentTag.BODYWEIGHT),
            setOf(MuscleGroup.ABS),
            emptySet(),
            MovementPattern.CORE,
            involvements = mapOf(
                MuscleGroup.ABS to 1.0
            )
        ),
        ex(
            "stomach-vacuum",
            "Stomach Vacuum",
            setOf(EquipmentTag.BODYWEIGHT),
            setOf(MuscleGroup.ABS),
            emptySet(),
            MovementPattern.CORE,
            involvements = mapOf(
                MuscleGroup.ABS to 1.0
            )
        ),
        ex(
            "toe-touchers",
            "Toe Touchers",
            setOf(EquipmentTag.BODYWEIGHT),
            setOf(MuscleGroup.ABS),
            emptySet(),
            MovementPattern.CORE,
            involvements = mapOf(
                MuscleGroup.ABS to 1.0
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
        involvements: Map<MuscleGroup, Double> = emptyMap()
    ) = Exercise(
        id = id,
        name = name,
        requiredEquipment = equipment,
        primaryMuscles = primary,
        secondaryMuscles = secondary,
        movementPattern = pattern,
        isUnilateral = isUnilateral,
        involvements = involvements
    )
}
