package com.hydrafit.app.feature.logger

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A bottom-bar tab switch pops the whole Log graph with `saveState`, and Navigation recomposes the
 * exiting destination afterwards. Its entry is then still resolvable by route while it may no longer
 * own ViewModels, which used to crash the app when the Log tab was re-selected from the history
 * browser. The projection resolved for every destination goes through [canOwnViewModels].
 */
class WorkoutHistoryNavigationTest {

    @Test
    fun aDestroyedDestinationIsRefusedAsAViewModelStoreOwner() {
        assertFalse(canOwnViewModels(Lifecycle.State.DESTROYED))
    }

    @Test
    fun aDestinationThatIsStillComposingKeepsItsViewModels() {
        listOf(
            Lifecycle.State.INITIALIZED,
            Lifecycle.State.CREATED,
            Lifecycle.State.STARTED,
            Lifecycle.State.RESUMED
        ).forEach { state ->
            assertTrue(canOwnViewModels(state), "expected $state to remain usable")
        }
    }

    @Test
    fun manualEntryAndItsPickerAreDistinctChildrenOfTheLoggerGraph() {
        assertEquals("log", loggerRoute)
        assertEquals("log/manual", manualLogRoute)
        assertEquals("log/manual/exercises", exercisePickerRoute)
        assertEquals(
            3,
            setOf(loggerRoute, manualLogRoute, exercisePickerRoute).size
        )
        assertTrue(manualLogRoute.startsWith("$loggerRoute/"))
        assertTrue(exercisePickerRoute.startsWith("$manualLogRoute/"))
    }

    @Test
    fun eachPresentationSelectsADistinctContentMode() {
        assertEquals(
            LoggerPresentation.entries.size,
            LoggerPresentation.entries.toSet().size
        )
        assertTrue(LoggerPresentation.entries.size >= 3)
    }

    @Test
    fun oneGraphObserverSurvivesChildNavigationAndPausesOnlyOnRealPause() {
        val owner = TestLifecycleOwner()
        val events = mutableListOf<String>()
        val observer = loggerGraphLifecycleObserver(
            owner = owner,
            configurationChange = { false },
            onResume = { events += "resume" },
            onPause = { events += "pause" }
        )
        owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
        owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        assertEquals(listOf("resume"), events, "one resume per graph resume")

        // Manual/picker/history compositions do not add observers or dispatch resume again.
        owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
        owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        assertEquals(listOf("resume", "pause", "resume"), events)

        owner.lifecycle.removeObserver(observer)
        owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        assertEquals(listOf("resume", "pause", "resume"), events, "detach stops callbacks")
    }

    @Test
    fun configurationChangeUsesTheExistingPausePolicy() {
        val owner = TestLifecycleOwner()
        var pauses = 0
        loggerGraphLifecycleObserver(
            owner = owner,
            configurationChange = { true },
            onResume = {},
            onPause = { pauses++ }
        )
        owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
        owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        owner.lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        assertEquals(0, pauses)
    }

    private class TestLifecycleOwner : LifecycleOwner {
        override val lifecycle: LifecycleRegistry = LifecycleRegistry.createUnsafe(this)
    }
}
