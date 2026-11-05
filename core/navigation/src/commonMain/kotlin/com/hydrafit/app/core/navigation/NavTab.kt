package com.hydrafit.app.core.navigation

/**
 * The bottom bar and adaptive rail's four destinations.
 *
 * Each one groups existing feature screens. The features keep their own routes, Koin modules and
 * graphs; grouping is a navigation decision made by the shell, so no feature depends on another.
 *
 * Nested category pages belong to their parent group, including Settings.
 */
enum class NavTab { TRAIN, PROGRESS, LIBRARY, SETTINGS }

/**
 * The tab that owns [hierarchyRoutes], or null when the current screen is not part of any group.
 *
 * A destination's hierarchy includes its parent graphs, so a nested screen keeps its group selected.
 * Groups must not share a route; the first match wins if they do.
 */
fun selectedNavTab(
    hierarchyRoutes: Collection<String>,
    tabRoutes: Map<NavTab, Collection<String>>
): NavTab? = NavTab.entries.firstOrNull { tab ->
    routeBelongsToGroup(hierarchyRoutes, tabRoutes[tab].orEmpty())
}

/** True when any route in the current hierarchy belongs to the tab's own group mapping. */
fun routeBelongsToGroup(
    hierarchyRoutes: Collection<String>,
    groupRoutes: Collection<String>
): Boolean = hierarchyRoutes.any { it in groupRoutes }

/**
 * The pushed shell overlay to close before a bottom-bar switch, or null when none is open.
 *
 * An overlay is pushed above a tab. The switch saves the screen it leaves, so an overlay left in
 * that saved stack would reappear the next time the tab is selected. The caller closes the returned
 * route without saving, before the switch runs; unsaved edits on the overlay are discarded with it.
 */
fun overlayRouteToClose(hierarchyRoutes: Collection<String>, overlayRoute: String): String? =
    overlayRoute.takeIf { route -> route in hierarchyRoutes }
