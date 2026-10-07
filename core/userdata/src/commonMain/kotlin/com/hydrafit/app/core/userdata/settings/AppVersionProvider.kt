package com.hydrafit.app.core.userdata.settings

/** The running app's version string, surfaced in Settings → Acknowledgments. */
interface AppVersionProvider {
    val versionName: String
}
