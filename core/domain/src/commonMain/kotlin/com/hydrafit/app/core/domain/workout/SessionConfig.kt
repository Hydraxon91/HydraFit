package com.hydrafit.app.core.domain.workout

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/**
 * Session-lifecycle policy for the logger.
 *
 * [sessionInactivityWindow] is the generous gap after a session's last set that still counts as the
 * same session; the next set logged beyond it closes the open session and auto-starts a new one.
 * It is evaluated only when a set is logged or the logger opens, so there is no background timer.
 */
data class SessionConfig(val sessionInactivityWindow: Duration = DEFAULT_INACTIVITY_WINDOW) {
    init {
        require(
            sessionInactivityWindow.isFinite() && sessionInactivityWindow.inWholeMilliseconds > 0L
        ) {
            "sessionInactivityWindow must be positive and finite"
        }
    }

    companion object {
        val DEFAULT_INACTIVITY_WINDOW: Duration = 4.hours
    }
}
