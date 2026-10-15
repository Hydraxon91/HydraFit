package com.hydrafit.app.feature.settings

/** Which Settings opt-in a rest notification permission request belongs to. */
internal enum class RestNotificationPermissionTarget {
    ALERTS,
    COUNTDOWN
}

/**
 * Tracks the in-flight rest notification permission request so its result enables only the opt-in
 * that asked for it. A second request while one is pending is ignored rather than reinterpreting the
 * first result, and a result with no pending target — such as one delivered after recreation —
 * enables nothing.
 */
internal class RestNotificationPermissionRequests {
    var pending: RestNotificationPermissionTarget? = null
        private set

    /** Records a request for [target], returning false when another request is still pending. */
    fun begin(target: RestNotificationPermissionTarget): Boolean {
        if (pending != null) return false
        pending = target
        return true
    }

    /** Consumes the pending target for a result, or null when no request was in flight. */
    fun takeResult(): RestNotificationPermissionTarget? {
        val target = pending
        pending = null
        return target
    }
}
