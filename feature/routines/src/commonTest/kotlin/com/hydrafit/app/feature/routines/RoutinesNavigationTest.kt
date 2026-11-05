package com.hydrafit.app.feature.routines

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class RoutinesNavigationTest {
    @Test
    fun libraryHomeAndRoutineListHaveDistinctChildRoutes() {
        val children = listOf(LibraryRoutes.HOME, LibraryRoutes.ROUTINES)
        assertEquals(children.size, children.toSet().size)
        children.forEach { route ->
            assertTrue(route.startsWith("$routinesRoute/"))
            assertNotEquals(routinesRoute, route)
        }
    }
}
