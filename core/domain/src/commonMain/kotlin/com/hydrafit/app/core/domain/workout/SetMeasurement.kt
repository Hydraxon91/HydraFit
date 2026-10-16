package com.hydrafit.app.core.domain.workout

/**
 * How a set is measured: either by repetitions or by elapsed whole seconds, never both.
 *
 * The two cases are exclusive values rather than fields that null each other out, so a set can
 * never carry a duration and a rep count at once and there is no sentinel (`0`) that means
 * "not applicable". Existing rows that store repetitions stay repetitions; a stored number is
 * never reinterpreted as seconds.
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
}
