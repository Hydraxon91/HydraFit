package com.hydrafit.app.core.domain.startup

import kotlinx.coroutines.flow.StateFlow

/**
 * Reports whether one-time startup maintenance has finished, so the app shell can gate the first
 * screen until it is safe to read the database.
 */
interface StartupReadiness {
    /** `false` while startup maintenance is running, then `true` for the process lifetime. */
    val isReady: StateFlow<Boolean>

    /**
     * `true` when the latest startup attempt failed and no later attempt has succeeded. The shell
     * shows a retryable failure while this is set instead of waiting on [isReady] forever, and the
     * app keeps the first screen closed because the database is still not safe to read.
     */
    val hasFailed: StateFlow<Boolean>

    /**
     * Requests another maintenance attempt after a failure. A call made while nothing has failed is
     * ignored, so a repeated tap or a late callback cannot start maintenance twice.
     */
    fun retry()
}
