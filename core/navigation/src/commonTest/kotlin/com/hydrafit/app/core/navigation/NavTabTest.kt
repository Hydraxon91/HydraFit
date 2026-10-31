package com.hydrafit.app.core.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NavTabTest {
    private val tabRoutes = mapOf(
        NavTab.TRAIN to listOf("log", "plan", "cardio"),
        NavTab.PROGRESS to listOf("progress", "progress/bodyweight", "fatigue"),
        NavTab.LIBRARY to listOf("routines", "equipment")
    )

    @Test
    fun aTabIsSelectedFromAnyRouteInItsGroup() {
        assertEquals(NavTab.TRAIN, selectedNavTab(listOf("log", "log/home"), tabRoutes))
        assertEquals(NavTab.TRAIN, selectedNavTab(listOf("plan"), tabRoutes))
        assertEquals(NavTab.PROGRESS, selectedNavTab(listOf("progress"), tabRoutes))
        assertEquals(NavTab.LIBRARY, selectedNavTab(listOf("equipment"), tabRoutes))
    }

    @Test
    fun aNestedSubGraphKeepsItsGroupSelected() {
        assertEquals(
            NavTab.TRAIN,
            selectedNavTab(listOf("log", "log/history", "log/history/detail"), tabRoutes)
        )
    }

    @Test
    fun settingsAndUnknownRoutesSelectNoTab() {
        assertNull(selectedNavTab(listOf("settings", "settings/home"), tabRoutes))
        assertNull(selectedNavTab(emptyList(), tabRoutes))
    }
}
