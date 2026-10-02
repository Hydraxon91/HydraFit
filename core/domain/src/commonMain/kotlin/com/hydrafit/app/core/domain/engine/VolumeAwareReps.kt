package com.hydrafit.app.core.domain.engine

/**
 * Resolves the goal's prescribed reps for a slot. Under Option C the explicit set count is the
 * volume knob, so reps stay inside the goal's compound/isolation band instead of trading off
 * against it: an endurance plan keeps its high reps however many sets the user chooses.
 */
class VolumeAwareReps {

    fun repsFor(goal: TrainingGoal, isCompound: Boolean, sets: Int): Int {
        require(sets >= 1) { "sets must be at least 1" }
        return if (isCompound) goal.compoundReps else goal.isolationReps
    }
}
