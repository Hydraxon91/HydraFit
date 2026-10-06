package com.hydrafit.app.core.domain.fatigue

import kotlin.test.Test
import kotlin.test.assertTrue

class FatigueConfigTest {

    /**
     * Guards the muscle-model extension: a new [MuscleGroup] without a configured half-life would
     * silently fall back to [FatigueConfig.fallbackHalfLife], which is easy to miss. Every group must
     * have an explicit value so recovery stays intentional.
     */
    @Test
    fun everyMuscleGroupHasAnExplicitHalfLife() {
        val missing = MuscleGroup.entries.filterNot { it in FatigueConfig.DEFAULT_HALF_LIVES }

        assertTrue(missing.isEmpty(), "Missing explicit half-lives for: $missing")
    }
}
