package com.hydrafit.app.core.navigation

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Opens the setup journey from anywhere in the app. The app shell (which owns the nav controller and
 * the setup routes) provides this around the nav host, so Settings can offer the entry without
 * depending on the `:feature:onboarding` routes.
 */
val LocalSetupNavigator = staticCompositionLocalOf<() -> Unit> {
    error("LocalSetupNavigator was not provided by the app shell")
}
