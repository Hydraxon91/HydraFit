package com.hydrafit.app.feature.routines

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.hydrafit.app.core.navigation.AppTab
import com.hydrafit.app.core.navigation.FeatureDestination
import com.hydrafit.app.core.navigation.LocalAppTabNavigator
import hydrafit.feature.routines.generated.resources.Res
import hydrafit.feature.routines.generated.resources.nav_label

val routinesRoute: String = "routines"

val routinesDestination: FeatureDestination = FeatureDestination(
    route = routinesRoute,
    label = Res.string.nav_label,
    graph = { { routinesGraph() } }
)

fun NavGraphBuilder.routinesGraph() {
    composable(routinesRoute) {
        // Equipment is its own feature and route; the shell's tab navigator opens it, so this
        // feature never depends on another feature's route.
        val openTab = LocalAppTabNavigator.current
        RoutinesRoute(onOpenEquipment = { openTab(AppTab.EQUIPMENT) })
    }
}
