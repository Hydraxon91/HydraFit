package com.hydrafit.app.feature.logger

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RestAlarmSchedulingTest {
    @Test
    fun grantedAccessUsesExactSchedulingOnce() {
        val calls = mutableListOf<String>()
        assertTrue(scheduleRestAlarm(true, { calls.add("exact") }, { calls.add("inexact") }))
        assertEquals(listOf("exact"), calls)
    }

    @Test
    fun deniedAccessUsesOnlyTheInexactFallback() {
        val calls = mutableListOf<String>()
        assertFalse(scheduleRestAlarm(false, { calls.add("exact") }, { calls.add("inexact") }))
        assertEquals(listOf("inexact"), calls)
    }

    @Test
    fun revocationBetweenCheckAndScheduleFallsBackOnce() {
        val calls = mutableListOf<String>()
        assertFalse(
            scheduleRestAlarm(true, {
                calls.add("exact")
                throw SecurityException("access revoked")
            }, { calls.add("inexact") })
        )
        assertEquals(listOf("exact", "inexact"), calls)
    }

    @Test
    fun unrelatedSchedulingFailuresAreNotHidden() {
        var fallbackCalls = 0
        assertFailsWith<IllegalStateException> {
            scheduleRestAlarm(true, { error("unrelated failure") }, { fallbackCalls++ })
        }
        assertEquals(0, fallbackCalls)
    }
}
