package com.hydrafit.app.feature.logger

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
 * The post-finish summary. It is a real destination so system Back returns to the Logger, but it
 * carries no argument: the workout it describes is held by the shared Logger ViewModel, exactly
 * like the open history entry.
 */
internal val summaryRoute: String = "log/summary"

/**
 * The one Logger ViewModel shared by the Logger screen and the history screens.
 *
 * The owner is resolved once per composition. It is abandoned the moment the entry behind it is
 * destroyed: a bottom-bar tab switch pops the whole Log graph with `saveState`, so Navigation
 * recomposes the exiting destination after its entry is gone while the entry stays resolvable by
 * route, and using that entry as a ViewModelStoreOwner throws. Callers read a null as "this
 * destination is on its way out" and compose nothing.
 */
@Composable
internal fun sharedLoggerViewModel(navController: NavController): WorkoutLoggerViewModel? {
    val owner = remember { navController.getBackStackEntry(loggerRoute) }
    if (!canOwnViewModels(owner.lifecycle.currentState)) return null
    return koinViewModel(viewModelStoreOwner = owner)
}

/** The history browser's ViewModel, scoped to the history sub-graph so its filters survive details. */
@Composable
private fun navigationHistoryViewModel(navController: NavController): WorkoutHistoryViewModel? {
    val owner = remember { navController.getBackStackEntry(historyGraphRoute) }
    if (!canOwnViewModels(owner.lifecycle.currentState)) return null
    return koinViewModel(viewModelStoreOwner = owner)
}

/**
 * Whether a destination may still own ViewModels. A saved entry stays on the back stack after its
 * graph is popped, so it is still resolvable by route while its ViewModel store already refuses
 * access; only a destroyed entry is refused here.
 */
internal fun canOwnViewModels(state: Lifecycle.State): Boolean = state != Lifecycle.State.DESTROYED

internal fun NavGraphBuilder.historyDestinations(navController: NavController) {
    navigation(startDestination = historyListRoute, route = historyGraphRoute) {
        composable(historyListRoute) {
            val historyViewModel = navigationHistoryViewModel(navController) ?: return@composable
            val loggerViewModel = sharedLoggerViewModel(navController) ?: return@composable
            val loggerState by loggerViewModel.state.collectAsStateWithLifecycle()
            // A summary that named one session opens it directly; the browser is already the entry.
            LaunchedEffect(loggerState.pendingHistoryKey) {
                loggerState.pendingHistoryKey?.let { key ->
                    loggerViewModel.onPendingHistoryKeyConsumed()
                    historyViewModel.onEntrySelected(key)
                    navController.navigate(historyDetailRoute)
                }
            }
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
            val historyViewModel = navigationHistoryViewModel(navController) ?: return@composable
            val loggerViewModel = sharedLoggerViewModel(navController) ?: return@composable
            WorkoutHistoryDetailRoute(
                viewModel = historyViewModel,
                loggerViewModel = loggerViewModel,
                onBack = {
                    historyViewModel.onEntryClosed()
                    navController.popBackStack()
                }
            )
        }
    }
}
