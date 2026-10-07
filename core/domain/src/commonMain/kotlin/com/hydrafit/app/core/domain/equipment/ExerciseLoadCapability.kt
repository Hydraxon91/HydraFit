package com.hydrafit.app.core.domain.equipment

/**
 * What external load an exercise can meaningfully carry. Capability belongs to the exercise, not to
 * its equipment: a bodyweight movement may use an apparatus without accepting load (Ab Roll), and
 * an equipment tag may be a support surface rather than carried resistance (calf raise on a
 * dumbbell). Never infer this from equipment presence.
 */
enum class ExerciseLoadCapability {
    /** Kilograms are external resistance; automatic weight suggestions are eligible. */
    EXTERNAL,

    /** Bodyweight only; no external load is recorded. */
    BODYWEIGHT_ONLY,

    /** Bodyweight work that may optionally carry added kilograms (pull-up, dips, …). */
    BODYWEIGHT_ADDABLE,

    /**
     * Legacy/unknown capability: a custom exercise saved before this contract existed, or a frozen
     * snapshot that predates it. It authorizes no automatic numeric generation and is never derived
     * from equipment.
     */
    UNSPECIFIED
}
