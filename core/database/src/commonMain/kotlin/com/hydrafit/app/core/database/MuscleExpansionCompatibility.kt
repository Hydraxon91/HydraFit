package com.hydrafit.app.core.database

/** Exact pre-expansion canonical profiles, captured at b5af19fb; never derive these from new seeds. */
internal object MuscleExpansionCompatibility {
    val previousProfiles: Map<String, String> = mapOf(
        "push-up" to (
            "|HORIZONTAL_PUSH|false|BODYWEIGHT_ADDABLE|" +
                "ABS:0.5,CHEST_LOWER:1.0,CHEST_UPPER:1.0,FRONT_DELTS:0.5,TRICEPS:0.5"
            ),
        "incline-push-up" to
            "|HORIZONTAL_PUSH|false|BODYWEIGHT_ONLY|CHEST_UPPER:1.0,TRICEPS:0.5",
        "incline-push-up-medium" to (
            "BODYWEIGHT|HORIZONTAL_PUSH|false|BODYWEIGHT_ONLY|" +
                "ABS:0.3,CHEST_UPPER:0.7,FRONT_DELTS:0.5,SIDE_DELTS:0.5,TRICEPS:0.5"
            ),
        "incline-push-up-wide" to (
            "BODYWEIGHT|HORIZONTAL_PUSH|false|BODYWEIGHT_ONLY|" +
                "ABS:0.3,CHEST_UPPER:0.7,FRONT_DELTS:0.5,SIDE_DELTS:0.5,TRICEPS:0.5"
            ),
        "push-up-to-side-plank" to (
            "BODYWEIGHT|HORIZONTAL_PUSH|false|BODYWEIGHT_ONLY|" +
                "ABS:0.3,CHEST_LOWER:0.7,CHEST_UPPER:0.7,SIDE_DELTS:0.5,TRICEPS:0.5"
            ),
        "push-up-wide" to (
            "BODYWEIGHT|HORIZONTAL_PUSH|false|BODYWEIGHT_ADDABLE|" +
                "ABS:0.3,CHEST_LOWER:0.7,CHEST_UPPER:0.7,FRONT_DELTS:0.5," +
                "SIDE_DELTS:0.5,TRICEPS:0.5"
            ),
        "single-arm-push-up" to (
            "BODYWEIGHT|HORIZONTAL_PUSH|true|BODYWEIGHT_ONLY|" +
                "CHEST_LOWER:0.7,CHEST_UPPER:0.7,FRONT_DELTS:0.5,SIDE_DELTS:0.5,TRICEPS:0.5"
            ),
        "hanging-leg-raise" to
            "BODYWEIGHT,PULL_UP_BAR|CORE|false|BODYWEIGHT_ADDABLE|ABS:1.0,OBLIQUES:0.5",
        "scissor-kick" to "BODYWEIGHT|CORE|false|BODYWEIGHT_ONLY|ABS:1.0",
        "sit-up" to "BODYWEIGHT|CORE|false|BODYWEIGHT_ADDABLE|ABS:1.0",
        "bicycle-crunch" to "BODYWEIGHT|CORE|false|BODYWEIGHT_ONLY|ABS:1.0,OBLIQUES:0.5",
        "step-up-with-knee-raise" to
            "BODYWEIGHT|LUNGE|true|BODYWEIGHT_ADDABLE|GLUTES:1.0,HAMSTRINGS:0.5,QUADS:0.5"
    )
}
