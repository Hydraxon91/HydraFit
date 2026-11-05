package com.hydrafit.app.feature.routines

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import com.hydrafit.app.core.navigation.AppTab
import com.hydrafit.app.core.navigation.FeatureDestination
import com.hydrafit.app.core.navigation.LocalAppTabNavigator
import hydrafit.feature.routines.generated.resources.Res
import hydrafit.feature.routines.generated.resources.nav_label
import org.koin.compose.viewmodel.koinViewModel

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
            // Scope the editor to the retained Library graph, not the list route: tab reselect/Back
            // then returns to the list with the unsaved routine draft intact.
            val viewModel = libraryRoutinesViewModel(navController) ?: return@composable
            RoutinesRoute(
                viewModel = viewModel,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

@Composable
private fun libraryRoutinesViewModel(navController: NavController): RoutinesViewModel? {
    val owner = remember { navController.getBackStackEntry(routinesRoute) }
    if (owner.lifecycle.currentState == Lifecycle.State.DESTROYED) return null
    return koinViewModel(viewModelStoreOwner = owner)
}
