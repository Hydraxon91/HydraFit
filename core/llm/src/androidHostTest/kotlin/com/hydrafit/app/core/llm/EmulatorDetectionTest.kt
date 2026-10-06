package com.hydrafit.app.core.llm

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EmulatorDetectionTest {

    @Test
    fun detectsEmulatorMarkers() {
        assertTrue(
            looksLikeEmulator(
                fingerprint = "generic/sdk_gphone64_arm64/gphone64:14/UP1A/generic",
                hardware = "ranchu",
                product = "sdk_gphone64_arm64",
                model = "sdk_gphone64_arm64"
            )
        )
        assertTrue(
            looksLikeEmulator(
                fingerprint = "google/sdk_gphone/emulator:10",
                hardware = "goldfish",
                product = "sdk",
                model = "Android SDK built for x86"
            )
        )
        assertTrue(
            looksLikeEmulator(
                fingerprint = "unknown/unknown/unknown",
                hardware = "unknown",
                product = "unknown",
                model = "Emulator"
            )
        )
    }

    @Test
    fun treatsRealDevicesAsNotEmulator() {
        assertFalse(
            looksLikeEmulator(
                fingerprint = "samsung/beyond1ltexx/beyond1:11/RP1A/beyond1",
                hardware = "qcom",
                product = "beyond1ltexx",
                model = "SM-G973F"
            )
        )
        assertFalse(
            looksLikeEmulator(
                fingerprint = "google/panther/panther:14/AP1A/panther",
                hardware = "tensor",
                product = "panther",
                model = "Pixel 7"
            )
        )
    }
}
