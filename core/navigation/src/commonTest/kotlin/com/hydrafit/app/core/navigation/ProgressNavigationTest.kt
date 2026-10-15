package com.hydrafit.app.core.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Progress is a standalone, pushed destination, not a seventh tab. These checks guard the shell
 * contract the feature relies on: the bottom-bar tab set stays exactly the six existing tabs.
 */
class ProgressNavigationTest {

    @Test
    fun theAppTabSetStaysSixTabsWithoutAProgressTab() {
        assertEquals(6, AppTab.entries.size)
        assertTrue(AppTab.entries.none { it.name.contains("PROGRESS") })
        assertEquals(
            listOf("EQUIPMENT", "FATIGUE", "PLAN", "LOG", "ROUTINES", "SETTINGS"),
            AppTab.entries.map { it.name }
        )
    }
}
