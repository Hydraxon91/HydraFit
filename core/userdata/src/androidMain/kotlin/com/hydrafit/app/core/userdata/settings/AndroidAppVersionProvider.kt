package com.hydrafit.app.core.userdata.settings

/**
 * Carries the Android build's `BuildConfig.VERSION_NAME`, injected at Koin startup because
 * `androidApp`'s `BuildConfig` is not reachable from `:shared` or `:core`.
 */
class AndroidAppVersionProvider(override val versionName: String) : AppVersionProvider
