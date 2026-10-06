package com.hydrafit.app.core.domain.startup

import kotlinx.coroutines.flow.StateFlow

/**
 * Reports whether one-time startup maintenance has finished, so the app shell can gate the first
 * screen until it is safe to read the database.
 */
interface StartupReadiness {
    /** `false` while startup maintenance is running, then `true` for the process lifetime. */
    val isReady: StateFlow<Boolean>
}
