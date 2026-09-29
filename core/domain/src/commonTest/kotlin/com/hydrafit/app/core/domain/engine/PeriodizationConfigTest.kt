package com.hydrafit.app.core.domain.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PeriodizationConfigTest {

    private val config = PeriodizationConfig()

    @Test
    fun marksOnlyTheDeloadWeekAsDeload() {
        assertFalse(config.isDeload(1))
        assertFalse(config.isDeload(2))
        assertFalse(config.isDeload(3))
        assertTrue(config.isDeload(4))
    }

    @Test
    fun advancesWeekWithinTheCycleAndWraps() {
        assertEquals(2, config.nextWeek(1))
        assertEquals(3, config.nextWeek(2))
        assertEquals(4, config.nextWeek(3))
        assertEquals(1, config.nextWeek(4))
    }

    @Test
    fun rejectsInvalidConfiguration() {
        assertFailsWith<IllegalArgumentException> { PeriodizationConfig(cycleLength = 0) }
        assertFailsWith<IllegalArgumentException> {
            PeriodizationConfig(cycleLength = 4, deloadWeek = 5)
        }
        assertFailsWith<IllegalArgumentException> { PeriodizationConfig(deloadVolumeScale = 0.0) }
        assertFailsWith<IllegalArgumentException> {
            PeriodizationConfig(deloadIntensityScale = 1.5)
        }
    }
}
