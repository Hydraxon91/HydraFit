package com.hydrafit.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hydrafit.app.core.domain.startup.StartupReadiness
import com.hydrafit.app.core.navigation.AppTab
import com.hydrafit.app.core.navigation.LocalAppTabNavigator
import com.hydrafit.app.core.navigation.LocalSetupNavigator
import com.hydrafit.app.core.navigation.appTabForRequest
import com.hydrafit.app.core.navigation.externalRequestNeedsTabChange
import com.hydrafit.app.feature.equipment.equipmentDestination
import com.hydrafit.app.feature.equipment.equipmentRoute
import com.hydrafit.app.feature.fatigueheatmap.fatigueHeatmapDestination
import com.hydrafit.app.feature.logger.RestCompletionPresenter
import com.hydrafit.app.feature.logger.loggerDestination
import com.hydrafit.app.feature.onboarding.OnboardingStartupEntry
import com.hydrafit.app.feature.onboarding.OnboardingStartupGate
import com.hydrafit.app.feature.onboarding.onboardingGraph
import com.hydrafit.app.feature.onboarding.onboardingRevisitRoute
import com.hydrafit.app.feature.routines.routinesDestination
import com.hydrafit.app.feature.settings.settingsDestination
import com.hydrafit.app.feature.splitbuilder.splitBuilderDestination
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private const val STARTUP_INDICATOR_DELAY_MILLIS = 150L

@Composable
@Preview
fun App(requestedTabName: String? = null, onTabRequestConsumed: () -> Unit = {}) {
    MaterialTheme(colorScheme = appColorScheme(isSystemInDarkTheme())) {
        Surface(modifier = Modifier.fillMaxSize()) {
            val readiness: StartupReadiness = koinInject()
            val isReady by readiness.isReady.collectAsState()
            if (isReady) {
                // The first-run decision is read once the staged restore has finished, so a pending
                // setup can be entered before any tab is shown.
                OnboardingStartupGate {
                    AppContent(
                        requestedTabName = requestedTabName,
                        onTabRequestConsumed = onTabRequestConsumed
                    )
                }
            } else {
                StartupLoadingGate()
            }
        }
    }
}

internal fun appColorScheme(darkTheme: Boolean): ColorScheme =
    if (darkTheme) darkColorScheme() else lightColorScheme()

@Composable
private fun AppContent(requestedTabName: String?, onTabRequestConsumed: () -> Unit) {
    val navController = rememberNavController()
    val destinations = listOf(
        equipmentDestination,
        fatigueHeatmapDestination,
        splitBuilderDestination,
        loggerDestination,
        routinesDestination,
        settingsDestination
    )
    val tabRoutes: Map<AppTab, String> = mapOf(
        AppTab.EQUIPMENT to equipmentDestination.route,
        AppTab.FATIGUE to fatigueHeatmapDestination.route,
        AppTab.PLAN to splitBuilderDestination.route,
        AppTab.LOG to loggerDestination.route,
        AppTab.ROUTINES to routinesDestination.route,
        AppTab.SETTINGS to settingsDestination.route
    )
    // Lets a feature (e.g. SplitBuilder after "Save as routine") open a tab without importing it.
    val openTab: (AppTab) -> Unit = { tab ->
        tabRoutes[tab]?.let { route -> navigateToAppTab(navController, route) }
    }
    // The shell registers the setup routes and navigates to them; the feature owns the journey.
    val openSetup: () -> Unit = {
        navController.navigate(onboardingRevisitRoute) {
            launchSingleTop = true
            restoreState = false
        }
    }
    // Leaving the setup entry returns to the tab beneath it, or Equipment if there is none.
    val closeSetup: () -> Unit = {
        if (!navController.popBackStack()) openTab(AppTab.EQUIPMENT)
    }
    // A notification tap names one tab and nothing else; the shell resolves it once the startup gate
    // has run, and a request for the tab already on screen changes nothing.
    val consumeTabRequest by rememberUpdatedState(onTabRequestConsumed)
    LaunchedEffect(requestedTabName) {
        val tab = appTabForRequest(requestedTabName) ?: return@LaunchedEffect
        val currentRoutes = navController.currentDestination
            ?.hierarchy
            ?.mapNotNull { it.route }
            ?.toList()
            .orEmpty()
        if (externalRequestNeedsTabChange(currentRoutes, tabRoutes.getValue(tab))) {
            openTab(tab)
        }
        consumeTabRequest()
    }

    Scaffold(
        snackbarHost = { RestCompletionPresenter() },
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = backStackEntry?.destination
            // Shell chrome rule: the bar belongs to the six tabs, never to a feature route.
            val onAppTab = currentDestination == null ||
                currentDestination.hierarchy.any { destination ->
                    destinations.any { it.route == destination.route }
                }
            if (onAppTab) {
                Column {
                    val rows = destinations.chunked(3)
                    rows.forEachIndexed { rowIndex, rowDestinations ->
                        NavigationBar(
                            windowInsets = if (rowIndex == rows.lastIndex) {
                                NavigationBarDefaults.windowInsets
                            } else {
                                NavigationBarDefaults.windowInsets.only(
                                    WindowInsetsSides.Horizontal
                                )
                            }
                        ) {
                            rowDestinations.forEach { destination ->
                                NavigationBarItem(
                                    selected =
                                    currentDestination?.hierarchy?.any {
                                        it.route == destination.route
                                    } == true,
                                    onClick = {
                                        navigateToAppTab(navController, destination.route)
                                    },
                                    icon = {},
                                    label = { Text(stringResource(destination.label)) }
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        CompositionLocalProvider(
            LocalAppTabNavigator provides openTab,
            LocalSetupNavigator provides openSetup
        ) {
            NavHost(
                navController = navController,
                startDestination = equipmentRoute,
                modifier = Modifier.padding(innerPadding)
            ) {
                destinations.forEach { destination -> destination.graph(navController)(this) }
                // Setup registers its own routes; it is not a seventh tab entry.
                onboardingGraph(navController = navController, onClose = closeSetup)
            }
            // Composed outside the nav host so its ViewModel owner matches the startup gate's.
            OnboardingStartupEntry(navController = navController)
        }
    }
}

/**
 * Moves the bottom bar to a tab.
 *
 * Selecting a different tab saves the one being left so its nested screens survive a return.
 * Re-selecting the tab that is already on screen instead returns it to its own root: the old
 * save-and-restore re-shows the nested screen it was on, and restoring that screen recomposes a
 * destination whose back stack entry the switch has already destroyed.
 */
private fun navigateToAppTab(navController: NavController, route: String) {
    val current = navController.currentDestination
    val isCurrentTab = current?.hierarchy?.any { it.route == route } == true
    val tabStartRoute = (navController.graph.findNode(route) as? NavGraph)?.startDestinationRoute
    when {
        !isCurrentTab -> navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
        tabStartRoute != null && current?.route != tabStartRoute ->
            navController.popBackStack(tabStartRoute, inclusive = false, saveState = false)
        else -> Unit
    }
}

/**
 * Shown while startup maintenance runs. The indicator only appears after a short delay so a fast
 * (usually single-digit-millisecond) maintenance pass never flashes a spinner.
 */
@Composable
private fun StartupLoadingGate() {
    var showIndicator by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(STARTUP_INDICATOR_DELAY_MILLIS)
        showIndicator = true
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (showIndicator) {
            CircularProgressIndicator()
        }
    }
}
