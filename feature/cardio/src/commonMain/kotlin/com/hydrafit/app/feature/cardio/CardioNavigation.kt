package com.hydrafit.app.feature.cardio

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hydrafit.app.core.navigation.FeatureDestination
import hydrafit.feature.cardio.generated.resources.Res
import hydrafit.feature.cardio.generated.resources.cardio_nav_label

/**
 * Cardio is a standalone, pushed destination registered by the app shell. It is not a bottom-bar
 * tab: entering it from the Logger pushes a new destination onto the Log graph, so an in-progress
 * draft stays owned by the Logger ViewModel and returning pops back to it.
 */
val cardioRoute: String = "cardio"

val cardioDestination: FeatureDestination = FeatureDestination(
    route = cardioRoute,
    label = Res.string.cardio_nav_label,
    graph = { navController -> { cardioGraph(navController) } }
)

fun NavGraphBuilder.cardioGraph(navController: NavController) {
    composable(cardioRoute) {
        CardioRoute(onBack = { navController.popBackStack() })
    }
}
