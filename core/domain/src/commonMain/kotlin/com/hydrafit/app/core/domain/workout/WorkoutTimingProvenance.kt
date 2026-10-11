package com.hydrafit.app.core.domain.workout

/** Whether a performed set's timing came from a current live action or historical entry. */
enum class WorkoutTimingProvenance {
    UNKNOWN,
    LIVE,
    CATCH_UP
}
