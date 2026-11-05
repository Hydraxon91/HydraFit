package com.hydrafit.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.LayoutDirection
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
import com.hydrafit.app.core.navigation.routeBelongsToGroup
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
import hydrafit.shared.generated.resources.nav_settings
import hydrafit.shared.generated.resources.nav_train
import hydrafit.shared.generated.resources.startup_failure_body
import hydrafit.shared.generated.resources.startup_failure_retry
import hydrafit.shared.generated.resources.startup_failure_title
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject

private const val STARTUP_INDICATOR_DELAY_MILLIS = 150L

@Composable
@Preview
fun App(requestedTabName: String? = null, onTabRequestConsumed: () -> Unit = {}) {
    MaterialTheme(
        colorScheme = appColorScheme(isSystemInDarkTheme()),
        typography = withFontFamily(appTypography, appFontFamily()),
        shapes = appShapes
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            val readiness: StartupReadiness = koinInject()
            val isReady by readiness.isReady.collectAsState()
            val hasFailed by readiness.hasFailed.collectAsState()
            when {
                isReady -> {
                    // The first-run decision is read once the staged restore has finished, so a
                    // pending setup can be entered before any tab is shown.
                    OnboardingStartupGate {
                        AppContent(
                            requestedTabName = requestedTabName,
                            onTabRequestConsumed = onTabRequestConsumed
                        )
                    }
                }
                hasFailed -> StartupFailureGate(onRetry = readiness::retry)
                else -> StartupLoadingGate()
            }
        }
    }
}

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
    NavTab.LIBRARY to listOf(routinesRoute, equipmentRoute),
    NavTab.SETTINGS to listOf(settingsRoute)
)

/** Parent graphs whose children keep the bottom bar or rail on screen. */
private val chromeRoutes: Set<String> =
    NAV_TAB_ROUTES.values.flatten().toSet()

/** The route a tab returns to when it is selected from the bar. */
private fun NavTab.startRoute(): String = when (this) {
    NavTab.TRAIN -> loggerRoute
    NavTab.PROGRESS -> progressRoute
    NavTab.LIBRARY -> routinesRoute
    NavTab.SETTINGS -> settingsRoute
}

private fun NavTab.labelResource(): StringResource = when (this) {
    NavTab.TRAIN -> Res.string.nav_train
    NavTab.PROGRESS -> Res.string.nav_progress
    NavTab.LIBRARY -> Res.string.nav_library
    NavTab.SETTINGS -> Res.string.nav_settings
}

/** Stable UI-automation identities for the four tab controls, not their accessible names. */
private fun NavTab.testTag(): String = "app_tab_" + name.lowercase()

/**
 * The compact bar keeps four full labels if the measured labels plus their minimum item padding fit
 * the available width; otherwise it uses two rows of two. No label is shortened, hidden or scaled.
 */
internal fun navigationRequiresTwoRows(
    availableWidthDp: Float,
    labelWidthsDp: List<Float>,
    interItemSpacingDp: Float = 8f,
    minimumItemWidthDp: Float = 48f
): Boolean {
    if (labelWidthsDp.isEmpty()) return false
    val itemCount = labelWidthsDp.size
    val perItemWidth = (availableWidthDp - interItemSpacingDp * (itemCount - 1)) / itemCount
    return perItemWidth < minimumItemWidthDp || labelWidthsDp.any { it > perItemWidth }
}

/**
 * One standard bottom-bar item. Both the one-row and two-row compact bars use it, so semantics,
 * selection, icons, labels, test tags and the group-aware navigation callback stay identical.
 */
@Composable
private fun RowScope.BottomTabItem(tab: NavTab, selected: Boolean, onClick: () -> Unit) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { AppNavigationIcon(tab) },
        label = { Text(stringResource(tab.labelResource())) },
        modifier = Modifier.testTag(tab.testTag())
    )
}

/**
 * Scaffold content padding when the IME is visible. The bottom bar already supplies its height in
 * [innerPadding]; imePadding separately reserves the keyboard. Keeping both would double-count the
 * bar and leave a blank band above the keyboard. Top/start/end are preserved; the bottom inset is
 * dropped only while the IME is actually visible, and the caller still applies imePadding once.
 */
internal fun contentPaddingWithIme(
    innerPadding: PaddingValues,
    imeVisible: Boolean,
    layoutDirection: LayoutDirection
): PaddingValues = if (imeVisible) {
    PaddingValues(
        start = innerPadding.calculateStartPadding(layoutDirection),
        top = innerPadding.calculateTopPadding(),
        end = innerPadding.calculateEndPadding(layoutDirection)
    )
} else {
    innerPadding
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
        tabRoutes[tab]?.let { route ->
            if (tab == AppTab.EQUIPMENT) {
                // Equipment is a Library section, also reachable from plan review. Push over the
                // requesting screen so Back returns there instead of resetting its saved tab.
                navController.navigate(route) { launchSingleTop = true }
            } else {
                navigateToAppTab(navController, route)
            }
        }
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
    // Nested feature pages inherit their graph's tab; standalone pushed routes retain their
    // existing shell eligibility.
    val onAppTab = currentDestination == null ||
        hierarchyRoutes.any { route -> route in chromeRoutes }
    val selectedTab = selectedNavTab(hierarchyRoutes, NAV_TAB_ROUTES)

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = usesNavigationRail(maxWidth)
        val tabLabels = NavTab.entries.map { stringResource(it.labelResource()) }
        val labelStyle = MaterialTheme.typography.labelMedium
        val density = LocalDensity.current
        val textMeasurer = rememberTextMeasurer()
        val labelWidths = remember(tabLabels, labelStyle, density) {
            tabLabels.map { label ->
                textMeasurer.measure(AnnotatedString(label), style = labelStyle)
                    .size.width / density.density
            }
        }
        val twoRowBottomBar = navigationRequiresTwoRows(maxWidth.value, labelWidths)
        val selectTab: (NavTab) -> Unit = { tab ->
            navigateToAppTab(
                navController,
                tab.startRoute(),
                NAV_TAB_ROUTES[tab].orEmpty()
            )
        }
        Scaffold(
            snackbarHost = { RestCompletionPresenter() },
            bottomBar = {
                if (onAppTab && !useRail) {
                    if (twoRowBottomBar) {
                        Column {
                            NavigationBar(windowInsets = WindowInsets(0, 0, 0, 0)) {
                                NavTab.entries.take(2).forEach { tab ->
                                    BottomTabItem(
                                        tab = tab,
                                        selected = selectedTab == tab,
                                        onClick = { selectTab(tab) }
                                    )
                                }
                            }
                            NavigationBar(
                                windowInsets = NavigationBarDefaults.windowInsets
                            ) {
                                NavTab.entries.drop(2).forEach { tab ->
                                    BottomTabItem(
                                        tab = tab,
                                        selected = selectedTab == tab,
                                        onClick = { selectTab(tab) }
                                    )
                                }
                            }
                        }
                    } else {
                        NavigationBar(
                            windowInsets = NavigationBarDefaults.windowInsets
                        ) {
                            NavTab.entries.forEach { tab ->
                                BottomTabItem(
                                    tab = tab,
                                    selected = selectedTab == tab,
                                    onClick = { selectTab(tab) }
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            val density = LocalDensity.current
            val imeVisible = WindowInsets.ime.getBottom(density) > 0
            val contentPadding = contentPaddingWithIme(
                innerPadding = innerPadding,
                imeVisible = imeVisible,
                layoutDirection = LocalLayoutDirection.current
            )
            Row(modifier = Modifier.fillMaxSize().padding(contentPadding).imePadding()) {
                if (onAppTab && useRail) {
                    NavigationRail(
                        modifier = Modifier.width(120.dp).fillMaxHeight()
                            .verticalScroll(rememberScrollState())
                    ) {
                        NavTab.entries.forEach { tab ->
                            NavigationRailItem(
                                selected = selectedTab == tab,
                                onClick = {
                                    navigateToAppTab(
                                        navController,
                                        tab.startRoute(),
                                        NAV_TAB_ROUTES[tab].orEmpty()
                                    )
                                },
                                icon = { AppNavigationIcon(tab) },
                                label = { Text(stringResource(tab.labelResource())) },
                                modifier = Modifier.testTag(tab.testTag())
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    CompositionLocalProvider(
                        LocalAppTabNavigator provides openTab,
                        LocalSetupNavigator provides openSetup,
                        LocalProgressNavigator provides openProgress,
                        LocalCardioNavigator provides openCardio
                    ) {
                        NavHost(
                            navController = navController,
                            startDestination = loggerRoute,
                            modifier = Modifier.widthIn(max = 1200.dp).fillMaxSize()
                        ) {
                            destinations.forEach { destination ->
                                destination.graph(navController)(this)
                            }
                            // Setup registers its own routes; it is not a seventh tab entry.
                            onboardingGraph(navController = navController, onClose = closeSetup)
                            // Progress owns its pushed destinations outside the shell tabs.
                            progressGraph(navController = navController, onClose = closeProgress)
                            // Cardio is the same standalone pushed destination.
                            cardioGraph(navController = navController)
                        }
                        // Outside the nav host: share the startup gate's ViewModel owner.
                        OnboardingStartupEntry(navController = navController)
                    }
                }
            }
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
internal enum class TabReselectionAction { NONE, POP_TO_ROOT, NAVIGATE_TO_ROOT }

/** Chooses the reselect action from the current route and the tab's start destination. */
internal fun tabReselectionAction(
    isCurrentTab: Boolean,
    currentRoute: String?,
    tabRootRoute: String
): TabReselectionAction = when {
    !isCurrentTab -> TabReselectionAction.NAVIGATE_TO_ROOT
    currentRoute == tabRootRoute -> TabReselectionAction.NONE
    else -> TabReselectionAction.POP_TO_ROOT
}

/**
 * When no existing tab root remains under the child (for example Equipment pushed from plan
 * review), a failed pop must still land on the tab's own root. The fallback does not restore the
 * child that was just left.
 */
internal fun tabReselectionAfterFailedPop(popSucceeded: Boolean): TabReselectionAction =
    if (popSucceeded) TabReselectionAction.NONE else TabReselectionAction.NAVIGATE_TO_ROOT

private fun navigateToAppTab(
    navController: NavController,
    route: String,
    groupRoutes: Collection<String> = listOf(route)
) {
    val current = navController.currentDestination
    val isCurrentTab = routeBelongsToGroup(
        current?.hierarchy?.mapNotNull { it.route }.orEmpty().toList(),
        groupRoutes
    )
    val tabRootRoute = (navController.graph.findNode(route) as? NavGraph)
        ?.startDestinationRoute ?: route
    when (tabReselectionAction(isCurrentTab, current?.route, tabRootRoute)) {
        TabReselectionAction.NAVIGATE_TO_ROOT -> navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = !isCurrentTab }
            launchSingleTop = true
            restoreState = !isCurrentTab
        }

        TabReselectionAction.POP_TO_ROOT -> {
            val popped = navController.popBackStack(
                tabRootRoute,
                inclusive = false,
                saveState = false
            )
            if (tabReselectionAfterFailedPop(popped) == TabReselectionAction.NAVIGATE_TO_ROOT) {
                // Equipment was pushed without a Library root beneath it. Land on the Library home
                // without restoring the child that was just left.
                navController.navigate(route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = false }
                    launchSingleTop = true
                    restoreState = false
                }
            }
        }

        TabReselectionAction.NONE -> Unit
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

/**
 * Shown when startup maintenance could not open the database. The app stays out of every feature, so
 * no screen reads a half-prepared database, and Retry is the only action: nothing is reset, replaced
 * or repaired automatically.
 */
@Composable
private fun StartupFailureGate(onRetry: () -> Unit) {
    // Centered when it fits, scrollable when a large font scale or short viewport would otherwise
    // clip the only recovery action.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = maxHeight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(Res.string.startup_failure_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() }
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = stringResource(Res.string.startup_failure_body),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onRetry) {
                Text(text = stringResource(Res.string.startup_failure_retry))
            }
        }
    }
}
