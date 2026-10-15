package com.hydrafit.app.feature.fatigueheatmap

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FatigueMapPaletteTest {

    @Test
    fun intensityIsClampedToTheUnitRange() {
        assertEquals(0f, fatigueIntensity(-1.0), 1e-6f)
        assertEquals(0f, fatigueIntensity(0.0), 1e-6f)
        assertEquals(0.5f, fatigueIntensity(0.5), 1e-6f)
        assertEquals(1f, fatigueIntensity(1.0), 1e-6f)
        assertEquals(1f, fatigueIntensity(2.0), 1e-6f)
    }

    @Test
    fun intensityNeverDecreasesAsScoreRises() {
        val scores = listOf(0.0, 0.05, 0.1, 0.25, 0.5, 0.75, 0.9, 1.0, 1.5)
        var previous = -1f
        scores.forEach { score ->
            val intensity = fatigueIntensity(score)
            assertTrue(intensity >= previous, "intensity dropped at score $score")
            previous = intensity
        }
    }
}
