package com.hydrafit.app.core.userdata.llm

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NpuDeviceDetectorTest {

    @Test
    fun detectsSnapdragonEightEliteBySocModel() {
        assertTrue(NpuDeviceDetector.isNpuCapable("SM8750", null, null))
    }

    @Test
    fun detectsByHardwareOrBoardStrings() {
        assertTrue(NpuDeviceDetector.isNpuCapable(null, "sun", "sm8750"))
        assertTrue(NpuDeviceDetector.isNpuCapable(null, "Snapdragon 8 Elite", null))
    }

    @Test
    fun rejectsUnrelatedDevices() {
        assertFalse(NpuDeviceDetector.isNpuCapable("Tensor G4", "husky", "ripcurrent"))
        assertFalse(NpuDeviceDetector.isNpuCapable(null, null, null))
    }
}
