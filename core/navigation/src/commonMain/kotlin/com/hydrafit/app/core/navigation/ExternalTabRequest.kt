package com.hydrafit.app.core.navigation

/**
 * The launch-intent extra a notification tap sets to name the top-level tab it should open.
 *
 * A tap carries a tab name only, so it can never select a workout, block or occurrence: the shell
 * resolves the name and the feature still decides what its tab shows.
 */
const val OPEN_TAB_EXTRA: String = "com.hydrafit.app.extra.OPEN_TAB"

/** The tab named by an external request, or null when nothing was requested or the name is unknown. */
fun appTabForRequest(tabName: String?): AppTab? =
    tabName?.let { requested -> AppTab.entries.firstOrNull { it.name == requested } }

/**
 * True when an external request should move the shell.
 *
 * A request for the tab already on screen is consumed without navigating, so a repeated tap leaves
 * the current screen, its saved state and any open draft alone.
 */
fun externalRequestNeedsTabChange(
    currentHierarchyRoutes: Collection<String>,
    requestedRoute: String
): Boolean = requestedRoute !in currentHierarchyRoutes
