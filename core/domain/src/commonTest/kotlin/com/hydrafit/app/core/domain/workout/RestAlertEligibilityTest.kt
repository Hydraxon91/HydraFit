package com.hydrafit.app.core.domain.workout

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RestAlertEligibilityTest {
    private val alarm = PersistedRestCountdown(120_000L, 120_000L, "boot", "session", 1L, "squat")

    @Test
    fun acceptsDueAndDelayedAlertsOnlyForTheCurrentLiveContext() {
        assertTrue(eligible())
        assertTrue(eligible(now = 240_000L))
        assertFalse(eligible(now = 119_999L))
        assertFalse(eligible(stored = null))
        assertFalse(eligible(stored = alarm.copy(deadlineElapsedMillis = 180_000L)))
        assertFalse(eligible(boot = null))
        assertFalse(eligible(boot = "new-boot"))
        assertFalse(eligible(session = null))
        assertFalse(eligible(session = "new-session"))
        assertFalse(eligible(occurrence = null))
        assertFalse(eligible(occurrence = 2L))
        assertFalse(eligible(enabled = false))
        assertFalse(eligible(guided = false))
    }

    private fun eligible(
        stored: PersistedRestCountdown? = alarm,
        now: Long = 120_000L,
        boot: String? = "boot",
        session: String? = "session",
        occurrence: Long? = 1L,
        enabled: Boolean = true,
        guided: Boolean = true
    ) = isRestAlertEligible(stored, alarm, boot, now, enabled, guided, session, occurrence)
}
