package com.hydrafit.app.feature.logger

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import org.koin.compose.viewmodel.koinViewModel

/**
 * The history browser is an internal sub-graph of the Logger. Keeping it nested under [loggerRoute]
 * means the Log tab stays selected on every history screen, and no shell or feature-registration
 * change is needed.
 */
internal val historyGraphRoute: String = "log/history"

internal val historyListRoute: String = "log/history/list"

/**
 * The open entry's identity is held by the history ViewModel rather than a route argument, so the
 * entry is always addressed by the exact [com.hydrafit.app.core.domain.workout.WorkoutHistoryKey]
 * the user picked and never reconstructed from the route.
 */
internal val historyDetailRoute: String = "log/history/detail"

/**
 * The one Logger ViewModel shared by the Logger screen and the history screens.
 *
 * The owner is resolved once per composition: [NavController.getBackStackEntry] throws while a
 * destination is being popped, and Navigation recomposes the exiting destination during that
 * transition, so the lookup must not be repeated on every recomposition.
 */
@Composable
internal fun sharedLoggerViewModel(navController: NavController): WorkoutLoggerViewModel {
    val owner = remember { navController.getBackStackEntry(loggerRoute) }
    return koinViewModel(viewModelStoreOwner = owner)
}

/** The history browser's ViewModel, scoped to the history sub-graph so its filters survive details. */
@Composable
private fun navigationHistoryViewModel(navController: NavController): WorkoutHistoryViewModel {
    val owner = remember { navController.getBackStackEntry(historyGraphRoute) }
    return koinViewModel(viewModelStoreOwner = owner)
}

internal fun NavGraphBuilder.historyDestinations(navController: NavController) {
    navigation(startDestination = historyListRoute, route = historyGraphRoute) {
        composable(historyListRoute) {
            val historyViewModel = navigationHistoryViewModel(navController)
            WorkoutHistoryRoute(
                viewModel = historyViewModel,
                onEntrySelected = { key ->
                    historyViewModel.onEntrySelected(key)
                    navController.navigate(historyDetailRoute)
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(historyDetailRoute) {
            val historyViewModel = navigationHistoryViewModel(navController)
            WorkoutHistoryDetailRoute(
                viewModel = historyViewModel,
                loggerViewModel = sharedLoggerViewModel(navController),
                onBack = {
                    historyViewModel.onEntryClosed()
                    navController.popBackStack()
                }
            )
        }
    }
}
