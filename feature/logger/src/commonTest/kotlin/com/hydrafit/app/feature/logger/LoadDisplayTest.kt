package com.hydrafit.app.feature.logger

import com.hydrafit.app.core.domain.workout.LoadKind
import kotlin.test.Test
import kotlin.test.assertEquals

class LoadDisplayTest {

    @Test
    fun externalWeightReadsAsExternal() {
        assertEquals(LoadDisplay.External(100.0), loadDisplayFor(LoadKind.EXTERNAL, 100.0))
    }

    @Test
    fun externalWithoutANumberReadsAsNone() {
        assertEquals(LoadDisplay.None, loadDisplayFor(LoadKind.EXTERNAL, null))
    }

    @Test
    fun addedWeightReadsAsAddedNotExternal() {
        assertEquals(LoadDisplay.Added(10.0), loadDisplayFor(LoadKind.ADDED, 10.0))
        assertEquals(LoadDisplay.Added(null), loadDisplayFor(LoadKind.ADDED, null))
    }

    @Test
    fun bodyweightReadsAsBodyweight() {
        assertEquals(LoadDisplay.Bodyweight, loadDisplayFor(LoadKind.BODYWEIGHT, null))
    }

    @Test
    fun legacyNumberReadsAsUnconfirmed() {
        assertEquals(
            LoadDisplay.LegacyUnconfirmed(32.5),
            loadDisplayFor(LoadKind.LEGACY_UNSPECIFIED, 32.5)
        )
    }

    @Test
    fun legacyWithoutANumberReadsAsNone() {
        assertEquals(LoadDisplay.None, loadDisplayFor(LoadKind.LEGACY_UNSPECIFIED, null))
    }
}
