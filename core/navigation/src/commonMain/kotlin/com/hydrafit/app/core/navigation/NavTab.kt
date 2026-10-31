package com.hydrafit.app.core.navigation

/**
 * The bottom bar's three destinations.
 *
 * Each one groups existing feature screens. The features keep their own routes, Koin modules and
 * graphs; grouping is a navigation decision made by the shell, so no feature depends on another.
 *
 * Settings sits outside these groups. It is reached from the shell toolbar and keeps the Library
 * group selected, so the bar does not disappear while the user is on it.
 */
enum class NavTab { TRAIN, PROGRESS, LIBRARY }

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
    val routes = tabRoutes[tab].orEmpty()
    hierarchyRoutes.any { it in routes }
}
