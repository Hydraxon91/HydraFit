package com.hydrafit.app.feature.routines

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hydrafit.app.core.navigation.FeatureDestination
import hydrafit.feature.routines.generated.resources.Res
import hydrafit.feature.routines.generated.resources.nav_label

val routinesRoute: String = "routines"

val routinesDestination: FeatureDestination = FeatureDestination(
    route = routinesRoute,
    label = Res.string.nav_label,
    graph = { routinesGraph() }
)

fun NavGraphBuilder.routinesGraph() {
    composable(routinesRoute) { RoutinesRoute() }
}
