package com.hydrafit.app.core.domain.workout

/**
 * The meaning of the weight recorded on a prescription or a performed set. Capability says what an
 * exercise may do; [LoadKind] says what a particular number means. An absent ([weightKg] null),
 * explicit zero and positive value stay distinct within each kind.
 */
enum class LoadKind {
    /** External resistance; kilograms are the resistance. */
    EXTERNAL,

    /** Bodyweight / no added load; the weight must be null. */
    BODYWEIGHT,

    /** Kilograms added to bodyweight; not total effective resistance. */
    ADDED,

    /**
     * A recorded historical number whose meaning was never established. Preserved as-is; never
     * reinterpreted into another kind and never used to generate a new numeric prescription.
     */
    LEGACY_UNSPECIFIED
}
