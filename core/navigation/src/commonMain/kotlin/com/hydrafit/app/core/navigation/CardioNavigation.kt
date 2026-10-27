package com.hydrafit.app.core.navigation

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Opens the standalone Cardio destination from anywhere in the app. The app shell (which owns the nav
 * controller and the Cardio route) provides this around the nav host, so the Logger can offer the
 * entry without depending on the `:feature:cardio` routes or reading that feature's state. Cardio is
 * a pushed, non-tab destination: entering and leaving it never replaces the Logger graph, so an
 * in-progress workout draft stays owned by the Logger ViewModel.
 */
val LocalCardioNavigator = staticCompositionLocalOf<() -> Unit> {
    error("LocalCardioNavigator was not provided by the app shell")
}
