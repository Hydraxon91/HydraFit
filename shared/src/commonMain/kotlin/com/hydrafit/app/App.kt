package com.hydrafit.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
import com.hydrafit.app.core.navigation.LocalCardioNavigator
import com.hydrafit.app.core.navigation.LocalProgressNavigator
import com.hydrafit.app.core.navigation.LocalSetupNavigator
import com.hydrafit.app.core.navigation.NavTab
import com.hydrafit.app.core.navigation.appTabForRequest
import com.hydrafit.app.core.navigation.externalRequestNeedsTabChange
import com.hydrafit.app.core.navigation.selectedNavTab
import com.hydrafit.app.feature.cardio.cardioGraph
import com.hydrafit.app.feature.cardio.cardioRoute
import com.hydrafit.app.feature.equipment.equipmentDestination
import com.hydrafit.app.feature.equipment.equipmentRoute
import com.hydrafit.app.feature.fatigueheatmap.fatigueHeatmapDestination
import com.hydrafit.app.feature.fatigueheatmap.fatigueHeatmapRoute
import com.hydrafit.app.feature.logger.RestCompletionPresenter
import com.hydrafit.app.feature.logger.loggerDestination
import com.hydrafit.app.feature.logger.loggerRoute
import com.hydrafit.app.feature.onboarding.OnboardingStartupEntry
import com.hydrafit.app.feature.onboarding.OnboardingStartupGate
import com.hydrafit.app.feature.onboarding.onboardingGraph
import com.hydrafit.app.feature.onboarding.onboardingRevisitRoute
import com.hydrafit.app.feature.progress.bodyWeightRoute
import com.hydrafit.app.feature.progress.progressGraph
import com.hydrafit.app.feature.progress.progressRoute
import com.hydrafit.app.feature.routines.routinesDestination
import com.hydrafit.app.feature.routines.routinesRoute
import com.hydrafit.app.feature.settings.settingsDestination
import com.hydrafit.app.feature.settings.settingsRoute
import com.hydrafit.app.feature.splitbuilder.splitBuilderDestination
import com.hydrafit.app.feature.splitbuilder.splitBuilderRoute
import hydrafit.shared.generated.resources.Res
import hydrafit.shared.generated.resources.nav_library
import hydrafit.shared.generated.resources.nav_progress
import hydrafit.shared.generated.resources.nav_train
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
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

/**
 * Which existing feature routes belong to each bottom-bar group. The groups are a shell navigation
 * decision: every feature keeps its own graph, bindings and route, and none of them knows which tab
 * it appears under.
 *
 * History stays inside the Logger graph (it shares the Logger ViewModel), so it selects Train.
 */
private val NAV_TAB_ROUTES: Map<NavTab, List<String>> = mapOf(
    NavTab.TRAIN to listOf(loggerRoute, splitBuilderRoute, cardioRoute),
    NavTab.PROGRESS to listOf(progressRoute, bodyWeightRoute, fatigueHeatmapRoute),
    NavTab.LIBRARY to listOf(routinesRoute, equipmentRoute)
)

/** Top-level destinations that keep the shell toolbar and bottom bar on screen. */
private val chromeRoutes: Set<String> =
    NAV_TAB_ROUTES.values.flatten().toSet() + settingsRoute

/** The route a tab returns to when it is selected from the bar. */
private fun NavTab.startRoute(): String = when (this) {
    NavTab.TRAIN -> loggerRoute
    NavTab.PROGRESS -> progressRoute
    NavTab.LIBRARY -> routinesRoute
}

private fun NavTab.labelResource(): StringResource = when (this) {
    NavTab.TRAIN -> Res.string.nav_train
    NavTab.PROGRESS -> Res.string.nav_progress
    NavTab.LIBRARY -> Res.string.nav_library
}

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
    // Leaving the setup entry returns to the tab beneath it, or Train if there is none.
    val closeSetup: () -> Unit = {
        if (!navController.popBackStack()) openTab(AppTab.LOG)
    }
    // Progress is a standalone, pushed destination: the shell owns its route, so the Logger offers
    // the entry without importing the feature. Entering pushes over the Log graph (drafts survive);
    // leaving pops back to it.
    val openProgress: () -> Unit = {
        navController.navigate(progressRoute) {
            launchSingleTop = true
            restoreState = false
        }
    }
    val closeProgress: () -> Unit = {
        if (!navController.popBackStack()) openTab(AppTab.LOG)
    }
    // Cardio is the same shape: a shell-owned pushed destination so the Logger offers the entry
    // without importing the feature. The graph pops its own back stack entry.
    val openCardio: () -> Unit = {
        navController.navigate(cardioRoute) {
            launchSingleTop = true
            restoreState = false
        }
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

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val hierarchyRoutes = currentDestination?.hierarchy?.mapNotNull { it.route }?.toList()
        .orEmpty()
    // Shell chrome rule: the toolbar and bar belong to the top-level destinations, never to a
    // feature's pushed route (history, cardio, onboarding, ...).
    val onAppTab = currentDestination == null ||
        hierarchyRoutes.any { route -> route in chromeRoutes }
    val selectedTab = selectedNavTab(hierarchyRoutes, NAV_TAB_ROUTES)
    // The toolbar entry is only useful when Settings is not already the current screen; Settings
    // itself carries the way back.
    val onSettings = settingsRoute in hierarchyRoutes

    Scaffold(
        snackbarHost = { RestCompletionPresenter() },
        topBar = {
            if (onAppTab && !onSettings) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            // Settings is a toolbar destination, not a tab: pushing it keeps the
                            // screen it was opened from underneath, so system Back returns there.
                            navController.navigate(settingsDestination.route) {
                                launchSingleTop = true
                                restoreState = false
                            }
                        }
                    ) {
                        Text(stringResource(settingsDestination.label))
                    }
                }
            }
        },
        bottomBar = {
            if (onAppTab) {
                NavigationBar(windowInsets = NavigationBarDefaults.windowInsets) {
                    NavTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { navigateToAppTab(navController, tab.startRoute()) },
                            icon = {},
                            // Text-only tabs: the default pill would render an empty indicator
                            // around the missing icon, so selection is carried by the label colour.
                            colors = NavigationBarItemDefaults.colors(
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = Color.Transparent
                            ),
                            label = { Text(stringResource(tab.labelResource())) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        CompositionLocalProvider(
            LocalAppTabNavigator provides openTab,
            LocalSetupNavigator provides openSetup,
            LocalProgressNavigator provides openProgress,
            LocalCardioNavigator provides openCardio
        ) {
            NavHost(
                navController = navController,
                startDestination = loggerRoute,
                modifier = Modifier.padding(innerPadding)
            ) {
                destinations.forEach { destination -> destination.graph(navController)(this) }
                // Setup registers its own routes; it is not a seventh tab entry.
                onboardingGraph(navController = navController, onClose = closeSetup)
                // Progress is a standalone, pushed destination; it is not a seventh tab entry.
                progressGraph(navController = navController, onClose = closeProgress)
                // Cardio is the same standalone pushed destination.
                cardioGraph(navController = navController)
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
