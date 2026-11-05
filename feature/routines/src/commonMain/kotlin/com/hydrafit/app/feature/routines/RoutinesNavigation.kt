package com.hydrafit.app.feature.routines

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.hydrafit.app.core.navigation.AppTab
import com.hydrafit.app.core.navigation.FeatureDestination
import com.hydrafit.app.core.navigation.LocalAppTabNavigator
import hydrafit.feature.routines.generated.resources.Res
import hydrafit.feature.routines.generated.resources.nav_label

val routinesRoute: String = "routines"

internal object LibraryRoutes {
    const val HOME = "routines/home"
    const val ROUTINES = "routines/list"
}

val routinesDestination: FeatureDestination = FeatureDestination(
    route = routinesRoute,
    label = Res.string.nav_label,
    graph = { navController -> { routinesGraph(navController) } }
)

fun NavGraphBuilder.routinesGraph(navController: NavController) {
    navigation(startDestination = LibraryRoutes.HOME, route = routinesRoute) {
        composable(LibraryRoutes.HOME) {
            val openTab = LocalAppTabNavigator.current
            LibraryHomeScreen(
                onOpenRoutines = { navController.navigate(LibraryRoutes.ROUTINES) },
                onOpenEquipment = { openTab(AppTab.EQUIPMENT) }
            )
        }
        composable(LibraryRoutes.ROUTINES) {
            RoutinesRoute(onBack = { navController.popBackStack() })
        }
    }
}
