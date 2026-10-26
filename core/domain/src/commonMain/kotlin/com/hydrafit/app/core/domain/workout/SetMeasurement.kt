package com.hydrafit.app.core.domain.workout

/**
 * How a set is measured: repetitions, elapsed whole seconds, distance in whole metres, or measured
 * force in newtons; exactly one. The cases are exclusive values rather than nullable sentinels, so a
 * stored number is never reinterpreted from one metric into another.
 */
sealed interface SetMeasurement {
    /** A rep-counted set. [count] is always at least 1. */
    data class Repetitions(val count: Int) : SetMeasurement {
        init {
            require(count >= 1) { "Repetitions must be positive, was $count" }
        }
    }

    /** A timed set. [seconds] is always whole seconds and at least 1. */
    data class Duration(val seconds: Int) : SetMeasurement {
        init {
            require(seconds >= 1) { "Duration must be positive, was $seconds" }
        }
    }

    /** A distance set. [meters] is always whole metres and at least 1. */
    data class Distance(val meters: Int) : SetMeasurement {
        init {
            require(meters >= 1) { "Distance must be positive, was $meters" }
        }
    }

    /**
     * A measured-force set. [newtons] is the device's reading converted to canonical newtons, never
     * a gripper's stated resistance rating, which is device identity rather than measured force.
     */
    data class Force(val newtons: Double) : SetMeasurement {
        init {
            require(newtons.isFinite() && newtons > 0.0) {
                "Force must be finite and positive, was $newtons"
            }
        }
    }
}
