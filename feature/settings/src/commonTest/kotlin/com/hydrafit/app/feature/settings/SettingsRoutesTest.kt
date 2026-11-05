package com.hydrafit.app.feature.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * These checks hold the category routes under the `settings` path prefix instead of a top-level
 * sibling path. They do not prove the navigation graph nesting, the bottom bar's selection or the
 * rendered hub/page layout; `NavTabTest` covers the selection outcome.
 */
class SettingsRoutesTest {

    private val categoryRoutes = listOf(
        SettingsRoutes.WORKOUT_PREFERENCES,
        SettingsRoutes.NOTIFICATIONS,
        SettingsRoutes.PLANNER_ENGINE,
        SettingsRoutes.UNITS,
        SettingsRoutes.BACKUP_AND_DATA,
        SettingsRoutes.LICENSES,
        acknowledgmentsRoute
    )

    @Test
    fun everyCategoryPageIsNestedUnderTheSettingsGraph() {
        categoryRoutes.forEach { route ->
            assertTrue(route.startsWith("$settingsRoute/"), "expected $route inside $settingsRoute")
        }
    }

    @Test
    fun theHubAndCategoryPagesHaveDistinctRoutes() {
        val routes = categoryRoutes + SettingsRoutes.HOME
        assertEquals(routes.size, routes.toSet().size)
        assertFalse(settingsRoute in routes)
    }
}
