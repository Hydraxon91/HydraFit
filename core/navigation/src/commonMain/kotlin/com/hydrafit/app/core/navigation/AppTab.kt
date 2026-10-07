package com.hydrafit.app.core.navigation

import androidx.compose.runtime.staticCompositionLocalOf

/** The app's top-level bottom-navigation tabs. */
enum class AppTab { EQUIPMENT, FATIGUE, PLAN, LOG, ROUTINES, SETTINGS }

/**
 * Opens a top-level tab. The app shell (which owns the nav controller and the tab routes) provides
 * this around the nav host, so a feature can request a tab without depending on another feature's
 * route.
 */
val LocalAppTabNavigator = staticCompositionLocalOf<(AppTab) -> Unit> {
    error("LocalAppTabNavigator was not provided by the app shell")
}
