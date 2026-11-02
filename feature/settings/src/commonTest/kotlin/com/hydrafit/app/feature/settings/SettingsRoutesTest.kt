package com.hydrafit.app.feature.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The shell decides which bottom group is selected from the destination hierarchy, so a category
 * page must stay inside the `settings` graph instead of becoming a top-level sibling. These checks
 * cover that navigation invariant; they do not prove the rendered hub or page layout.
 */
class SettingsRoutesTest {

    private val categoryRoutes = listOf(
        SettingsRoutes.WORKOUT_PREFERENCES,
        SettingsRoutes.NOTIFICATIONS,
        SettingsRoutes.PLANNER_ENGINE,
        SettingsRoutes.UNITS,
        SettingsRoutes.BACKUP_AND_DATA,
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
