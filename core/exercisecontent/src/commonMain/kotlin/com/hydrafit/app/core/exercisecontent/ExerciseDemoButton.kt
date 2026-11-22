package com.hydrafit.app.core.exercisecontent

import androidx.compose.ui.platform.UriHandler

internal fun UriHandler.openDemo(url: String) {
    // A device with no browser or YouTube app cannot open the link; the screen simply stays put
    // instead of crashing.
    try {
        openUri(url)
    } catch (_: IllegalArgumentException) {
        // Nothing can handle the link.
    }
}
