package com.hydrafit.app.core.domain.equipment

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExerciseExclusionTest {

    @Test
    fun indefiniteExclusionIsAlwaysActive() {
        val exclusion = ExerciseExclusion("back-squat", expiresAtMillis = null)

        assertTrue(exclusion.isActive(nowMillis = 0L))
        assertTrue(exclusion.isActive(nowMillis = Long.MAX_VALUE))
    }

    @Test
    fun datedExclusionIsActiveOnlyBeforeItsExpiry() {
        val exclusion = ExerciseExclusion("back-squat", expiresAtMillis = 1_000L)

        assertTrue(exclusion.isActive(nowMillis = 999L))
        // The instant it expires, it stops filtering.
        assertFalse(exclusion.isActive(nowMillis = 1_000L))
        assertFalse(exclusion.isActive(nowMillis = 1_001L))
    }

    @Test
    fun defaultExpiryIsTwelveWeeksAfterTheGivenInstant() {
        val now = 1_000_000L

        assertEquals(
            now + ExerciseExclusion.DEFAULT_WINDOW_DAYS * ExerciseExclusion.MILLIS_PER_DAY,
            ExerciseExclusion.defaultExpiryFrom(now)
        )
    }
}
