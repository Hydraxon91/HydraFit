package com.hydrafit.app.core.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExternalTabRequestTest {
    @Test
    fun resolvesOnlyKnownTabNames() {
        assertEquals(AppTab.LOG, appTabForRequest("LOG"))
        assertNull(appTabForRequest(null))
        assertNull(appTabForRequest(""))
        assertNull(appTabForRequest("logger"))
        assertNull(appTabForRequest("SETTINGS "))
    }

    @Test
    fun aRequestForTheVisibleTabNeedsNoNavigation() {
        assertFalse(externalRequestNeedsTabChange(listOf("log", "log/history"), "log"))
        assertTrue(externalRequestNeedsTabChange(listOf("settings"), "log"))
        assertTrue(externalRequestNeedsTabChange(emptyList(), "log"))
    }
}
