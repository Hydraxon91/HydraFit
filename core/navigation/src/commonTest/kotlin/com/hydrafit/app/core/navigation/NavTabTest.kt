package com.hydrafit.app.core.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NavTabTest {
    private val tabRoutes = mapOf(
        NavTab.TRAIN to listOf("log", "plan", "cardio"),
        NavTab.PROGRESS to listOf("progress", "progress/bodyweight", "fatigue"),
        NavTab.LIBRARY to listOf("routines", "equipment"),
        NavTab.SETTINGS to listOf("settings")
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
    fun settingsAndItsCategoryPagesSelectSettings() {
        assertEquals(
            NavTab.SETTINGS,
            selectedNavTab(listOf("settings", "settings/home"), tabRoutes)
        )
        assertEquals(
            NavTab.SETTINGS,
            selectedNavTab(listOf("settings", "settings/units"), tabRoutes)
        )
    }

    @Test
    fun unknownRoutesSelectNoTab() {
        assertNull(selectedNavTab(emptyList(), tabRoutes))
        assertNull(selectedNavTab(listOf("unknown"), tabRoutes))
    }

    @Test
    fun reselectingLibraryRecognisesEveryRouteInItsGroup() {
        assertTrue(routeBelongsToGroup(listOf("equipment"), tabRoutes.getValue(NavTab.LIBRARY)))
        assertTrue(
            routeBelongsToGroup(
                listOf("equipment", "equipment/home"),
                tabRoutes.getValue(NavTab.LIBRARY)
            )
        )
        assertFalse(routeBelongsToGroup(listOf("plan"), tabRoutes.getValue(NavTab.LIBRARY)))
        assertEquals(NavTab.LIBRARY, selectedNavTab(listOf("equipment"), tabRoutes))
    }

    @Test
    fun anOpenOverlayIsClosedBeforeATabSwitch() {
        assertEquals("settings", overlayRouteToClose(listOf("routines", "settings"), "settings"))
        assertEquals(
            "settings",
            overlayRouteToClose(listOf("settings", "settings/acknowledgments"), "settings")
        )
    }

    @Test
    fun noOverlayIsClosedOnAPlainTab() {
        assertNull(overlayRouteToClose(listOf("log"), "settings"))
        assertNull(overlayRouteToClose(emptyList(), "settings"))
    }

    @Test
    fun anUnrelatedRouteIsNotMistakenForTheOverlay() {
        assertNull(
            overlayRouteToClose(
                listOf("log", "log/history", "progress", "routines", "equipment"),
                "settings"
            )
        )
    }
}
