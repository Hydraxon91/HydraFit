package com.hydrafit.app.core.domain.engine

/**
 * Planner targets for different training goals. The stored plan contains one concrete target per
 * exercise, so the selected rep counts are fixed representatives of common training ranges.
 */
enum class TrainingGoal(val defaultSets: Int, val compoundReps: Int, val isolationReps: Int) {
    BALANCED(defaultSets = 3, compoundReps = 6, isolationReps = 12),
    STRENGTH(defaultSets = 4, compoundReps = 5, isolationReps = 8),
    HYPERTROPHY(defaultSets = 3, compoundReps = 8, isolationReps = 12),
    ENDURANCE(defaultSets = 2, compoundReps = 15, isolationReps = 15)
}
