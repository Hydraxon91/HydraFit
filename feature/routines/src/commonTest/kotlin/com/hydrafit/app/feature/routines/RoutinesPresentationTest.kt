package com.hydrafit.app.feature.routines

import com.hydrafit.app.core.domain.routine.RoutineTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The saved-routines filter: the screen shows one archive group at a time, in the stored order.
 */
class RoutinesPresentationTest {

    @Test
    fun defaultViewHidesArchivedRoutines() {
        val routines = listOf(
            template(id = 1, name = "Upper"),
            template(id = 2, name = "Retired upper", archived = true)
        )

        val visible = visibleRoutines(routines, showArchived = false)

        assertEquals(listOf("Upper"), visible.map { it.name })
    }

    @Test
    fun archivedFilterShowsOnlyArchivedRoutines() {
        val routines = listOf(
            template(id = 1, name = "Upper"),
            template(id = 2, name = "Retired upper", archived = true),
            template(id = 3, name = "Retired lower", archived = true)
        )

        val visible = visibleRoutines(routines, showArchived = true)

        assertEquals(listOf("Retired upper", "Retired lower"), visible.map { it.name })
    }

    @Test
    fun filterPreservesTemplateOrder() {
        val routines = listOf(
            template(id = 3, name = "Third"),
            template(id = 1, name = "First", archived = true),
            template(id = 2, name = "Second")
        )

        assertEquals(
            listOf("Third", "Second"),
            visibleRoutines(routines, showArchived = false).map { it.name }
        )
        assertEquals(
            listOf("First"),
            visibleRoutines(routines, showArchived = true).map { it.name }
        )
    }

    @Test
    fun emptyInputYieldsEmptyOutput() {
        assertTrue(visibleRoutines(emptyList(), showArchived = false).isEmpty())
        assertTrue(visibleRoutines(emptyList(), showArchived = true).isEmpty())
    }

    private fun template(id: Long, name: String, archived: Boolean = false) = RoutineTemplate(
        id = id,
        name = name,
        archivedAtMillis = if (archived) 1L else null
    )
}
