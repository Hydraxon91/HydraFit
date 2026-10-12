package com.hydrafit.app.feature.logger

import androidx.lifecycle.Lifecycle
import kotlin.test.Test
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
}
