package com.hydrafit.app.feature.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import hydrafit.feature.onboarding.generated.resources.Res
import hydrafit.feature.onboarding.generated.resources.onboarding_load_error
import hydrafit.feature.onboarding.generated.resources.onboarding_retry
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/** First-run setup, forced once after readiness when the saved disposition is still PENDING. */
val onboardingFirstRunRoute: String = "setup/first-run"

/** User-initiated setup revisit from Settings; always starts at the Welcome step. */
val onboardingRevisitRoute: String = "setup/revisit"

/**
 * Registers the setup entry routes. They are top-level destinations, not a seventh tab: the app
 * shell hides its bottom bar whenever the current destination is outside the six registered tabs.
 */
fun NavGraphBuilder.onboardingGraph(navController: NavController, onClose: () -> Unit) {
    composable(onboardingFirstRunRoute) { entry ->
        OnboardingEntryRoute(
            revisit = false,
            onClose = rememberOnboardingExit(entry, navController, onClose)
        )
    }
    composable(onboardingRevisitRoute) { entry ->
        OnboardingEntryRoute(
            revisit = true,
            onClose = rememberOnboardingExit(entry, navController, onClose)
        )
    }
}

/**
 * Binds the shared exit callback to this destination instance.
 *
 * Close and the finished effect both call it, so a repeat or late call must not pop whatever route
 * happens to be current by then: the captured back-stack entry has to still be current, and the
 * callback fires at most once per destination instance.
 */
@Composable
private fun rememberOnboardingExit(
    entry: NavBackStackEntry,
    navController: NavController,
    onClose: () -> Unit
): () -> Unit {
    val latestOnClose by rememberUpdatedState(onClose)
    val guard = remember(entry, navController) {
        OnboardingExitGuard(
            entry = entry,
            currentEntry = { navController.currentBackStackEntry },
            onExit = { latestOnClose() }
        )
    }
    return guard::exit
}

/**
 * Calls [onExit] only while [entry] is still the current back-stack entry, and only once.
 *
 * A route string is not an identity: two visits to the same route are different entries, so the
 * captured entry itself decides whether an exit still belongs to it.
 */
internal class OnboardingExitGuard<T : Any>(
    private val entry: T,
    private val currentEntry: () -> T?,
    private val onExit: () -> Unit
) {
    private var exited = false

    fun exit() {
        if (exited || currentEntry() !== entry) return
        exited = true
        onExit()
    }
}

/**
 * Gates the app content on the one-time setup decision. The ViewModel is resolved from the root
 * ViewModel store, so the same instance survives recomposition and activity recreation.
 */
@Composable
fun OnboardingStartupGate(
    viewModel: OnboardingEntryViewModel = koinViewModel(),
    content: @Composable () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (state) {
        OnboardingEntryState.Loading -> StartupGateLoading()
        OnboardingEntryState.Failed -> StartupGateFailure(onRetry = viewModel::retry)
        is OnboardingEntryState.Ready -> content()
    }
}

/**
 * Forces the first-run route when the saved disposition is PENDING, after readiness and restore.
 *
 * It is composed outside the nav host's destination scopes, so `koinViewModel` resolves the same
 * root-owned instance the gate read. Entering saves nothing; the claim is consumed once per process
 * and a route already restored to first-run is left alone.
 */
@Composable
fun OnboardingStartupEntry(
    navController: NavController,
    viewModel: OnboardingEntryViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state) {
        val ready = state as? OnboardingEntryState.Ready ?: return@LaunchedEffect
        if (!ready.showSetup) return@LaunchedEffect
        if (!viewModel.claimAutomaticEntry()) return@LaunchedEffect
        if (navController.currentDestination?.route == onboardingFirstRunRoute) {
            return@LaunchedEffect
        }
        navController.navigate(onboardingFirstRunRoute) {
            // Equipment stays the root, and a restored revisit route cannot remain under it.
            popUpTo(navController.graph.startDestinationId) { inclusive = false }
            launchSingleTop = true
            restoreState = false
        }
    }
}

@Composable
private fun StartupGateLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun StartupGateFailure(onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(Res.string.onboarding_load_error),
                style = MaterialTheme.typography.bodyMedium
            )
            Button(onClick = onRetry) {
                Text(stringResource(Res.string.onboarding_retry))
            }
        }
    }
}
