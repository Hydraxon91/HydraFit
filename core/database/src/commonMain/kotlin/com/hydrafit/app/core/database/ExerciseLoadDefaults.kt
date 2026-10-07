package com.hydrafit.app.core.database

import com.hydrafit.app.core.domain.equipment.ExerciseLoadCapability

/**
 * Curated, explicit load capability for the seeded catalog. This is a product default for the
 * cataloged variant, not a claim that a movement is physically uninhabitable: any built-in can be
 * overridden in the editor, and an equipment tag never determines capability (Ab Roll and the
 * calf-raise-on-a-dumbbell both use apparatus without carrying its weight).
 *
 * Everything not listed is [ExerciseLoadCapability.EXTERNAL].
 */
internal object ExerciseLoadDefaults {

    /** Bodyweight only; no external load is recorded. */
    val bodyweightOnly: Set<String> = setOf(
        "incline-push-up",
        "bodyweight-squat",
        "bodyweight-calf-raise",
        "plank",
        "ab-roll",
        "handstand-push-ups",
        "incline-push-up-medium",
        "incline-push-up-wide",
        "push-up-to-side-plank",
        "single-arm-push-up",
        "calf-raise-on-a-dumbbell",
        "reverse-crunch",
        "scissor-kick",
        "stomach-vacuum",
        "toe-touchers"
    )

    /** Bodyweight work that may optionally carry added kilograms. */
    val bodyweightAddable: Set<String> = setOf(
        "push-up",
        "dips",
        "pull-up",
        "chin-up",
        "glute-bridge",
        "hanging-leg-raise",
        "push-up-wide",
        "v-bar-pullup",
        "wide-grip-rear-pull-up",
        "step-up-with-knee-raise",
        "butt-lift-bridge",
        "leg-lift",
        "single-leg-glute-bridge",
        "crunches",
        "decline-crunch",
        "sit-up"
    )

    fun capabilityFor(id: String): ExerciseLoadCapability = when (id) {
        in bodyweightOnly -> ExerciseLoadCapability.BODYWEIGHT_ONLY
        in bodyweightAddable -> ExerciseLoadCapability.BODYWEIGHT_ADDABLE
        else -> ExerciseLoadCapability.EXTERNAL
    }
}
