package com.hydrafit.app.core.userdata.settings

// The iOS app does not inject a build version into Kotlin/Native yet.
class IosAppVersionProvider : AppVersionProvider {
    override val versionName: String = "dev"
}
