package com.hydrafit.app.feature.progress

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hydrafit.app.core.navigation.AppTab
import com.hydrafit.app.core.navigation.LocalAppTabNavigator

/**
 * Exercise Progress is the Progress bottom-bar group's start destination. Body weight is pushed
 * under it; the shell's group-aware reselection returns to this root from Body weight, while
 * Fatigue map is a separate shell destination in the same group.
 */
val progressRoute: String = "progress"

/** The pushed Body weight destination, nested under Exercise Progress rather than a new tab. */
val bodyWeightRoute: String = "progress/bodyweight"

fun NavGraphBuilder.progressGraph(navController: NavController, onClose: () -> Unit) {
    composable(progressRoute) {
        // Fatigue is its own feature with its own route; the shell's tab navigator opens it without
        // this feature depending on it.
        val openTab = LocalAppTabNavigator.current
        ProgressRoute(
            onBack = onClose,
            onOpenBodyWeight = { navController.navigate(bodyWeightRoute) },
            onOpenFatigue = { openTab(AppTab.FATIGUE) }
        )
    }
    composable(bodyWeightRoute) {
        BodyWeightRoute(onBack = { navController.popBackStack() })
    }
}
