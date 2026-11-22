package com.hydrafit.app.core.domain.engine

/**
 * Planner targets for different training goals. Since 0.15.4 each goal has a rep range for compound
 * and isolation work; a generated slot starts at the bottom of its range and climbs by double
 * progression (see [RepProgression]). Accessory slots (isolation/pump work) default to fewer sets
 * than compound slots, but the amount stays user-adjustable through the SplitBuilder picker.
 *
 * The ranges are common coaching ranges for each goal, not validated per-person optima.
 */
enum class TrainingGoal(
    val defaultSets: Int,
    val accessorySets: Int,
    val compoundRepRange: IntRange,
    val isolationRepRange: IntRange
) {
    BALANCED(
        defaultSets = 3,
        accessorySets = 2,
        compoundRepRange = 6..8,
        isolationRepRange = 10..12
    ),
    STRENGTH(
        defaultSets = 4,
        accessorySets = 3,
        compoundRepRange = 4..6,
        isolationRepRange = 6..10
    ),
    HYPERTROPHY(
        defaultSets = 3,
        accessorySets = 2,
        compoundRepRange = 8..12,
        isolationRepRange = 10..15
    ),
    ENDURANCE(
        defaultSets = 2,
        accessorySets = 2,
        compoundRepRange = 12..15,
        isolationRepRange = 15..20
    );

    /** The starting compound rep count: the bottom of [compoundRepRange]. */
    val compoundReps: Int get() = compoundRepRange.first

    /** The starting isolation rep count: the bottom of [isolationRepRange]. */
    val isolationReps: Int get() = isolationRepRange.first

    fun repRange(isCompound: Boolean): IntRange =
        if (isCompound) compoundRepRange else isolationRepRange
}
