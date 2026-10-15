package com.hydrafit.app.core.navigation

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Opens the standalone Exercise Progress destination from anywhere in the app. The app shell (which
 * owns the nav controller and the Progress route) provides this around the nav host, so the Logger
 * can offer the entry without depending on the `:feature:progress` routes or reading that feature's
 * state. Progress is a pushed, non-tab destination: entering and leaving it never replaces the
 * Logger graph, so an in-progress draft stays owned by the Logger ViewModel.
 */
val LocalProgressNavigator = staticCompositionLocalOf<() -> Unit> {
    error("LocalProgressNavigator was not provided by the app shell")
}
