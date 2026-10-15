package com.hydrafit.app.feature.progress

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable

/**
 * Exercise Progress is a standalone, pushed destination registered by the app shell. It is not a
 * bottom-bar tab: entering it from the Logger pushes a new destination onto the Log graph, so an
 * in-progress draft stays owned by the Logger ViewModel and returning pops back to it.
 */
val progressRoute: String = "progress"

/** The pushed Body weight destination, nested under Exercise Progress rather than a new tab. */
val bodyWeightRoute: String = "progress/bodyweight"

fun NavGraphBuilder.progressGraph(navController: NavController, onClose: () -> Unit) {
    composable(progressRoute) {
        ProgressRoute(
            onBack = onClose,
            onOpenBodyWeight = { navController.navigate(bodyWeightRoute) }
        )
    }
    composable(bodyWeightRoute) {
        BodyWeightRoute(onBack = { navController.popBackStack() })
    }
}
