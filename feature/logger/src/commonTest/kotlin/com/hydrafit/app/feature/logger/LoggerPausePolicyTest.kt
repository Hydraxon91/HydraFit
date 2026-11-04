package com.hydrafit.app.feature.logger

import kotlin.test.Test
import kotlin.test.assertEquals

class LoggerPausePolicyTest {
    @Test
    fun configurationChangeDoesNotCancelTheTimer() {
        var pauses = 0
        handleLoggerPause(configurationChange = { true }, onPause = { pauses++ })
        assertEquals(0, pauses)
    }

    @Test
    fun genuineBackgroundPauseStillCancelsExactlyOnce() {
        var pauses = 0
        handleLoggerPause(configurationChange = { false }, onPause = { pauses++ })
        assertEquals(1, pauses)
    }

    @Test
    fun configurationStatusIsReadAtTheEventNotCapturedAtComposition() {
        var changingConfiguration = false
        val check = { changingConfiguration }
        var pauses = 0
        changingConfiguration = true
        handleLoggerPause(check) { pauses++ }
        assertEquals(0, pauses)
        changingConfiguration = false
        handleLoggerPause(check) { pauses++ }
        assertEquals(1, pauses)
    }
}
