package com.hydrafit.app.core.domain.workout

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class ForceMeasurementTest {

    @Test
    fun forceIsItsOwnExclusiveMeasurement() {
        requireValidSetMeasurement(null, null, null, 250.0)
        assertFailsWith<IllegalArgumentException> {
            requireValidSetMeasurement(8, null, null, 250.0)
        }
        assertFailsWith<IllegalArgumentException> {
            requireValidSetMeasurement(null, 30, null, 250.0)
        }
        assertFailsWith<IllegalArgumentException> {
            requireValidSetMeasurement(null, null, 400, 250.0)
        }
        assertFailsWith<IllegalArgumentException> {
            requireValidSetMeasurement(null, null, null, null)
        }
    }

    @Test
    fun forceMustBeFiniteAndPositive() {
        assertFailsWith<IllegalArgumentException> {
            requireValidSetMeasurement(null, null, null, 0.0)
        }
        assertFailsWith<IllegalArgumentException> {
            requireValidSetMeasurement(null, null, null, -5.0)
        }
        assertFailsWith<IllegalArgumentException> {
            requireValidSetMeasurement(null, null, null, Double.NaN)
        }
        assertFailsWith<IllegalArgumentException> {
            requireValidSetMeasurement(null, null, null, Double.POSITIVE_INFINITY)
        }
    }

    @Test
    fun aForceSetCannotAlsoRecordALoad() {
        requireForceIsLoadFree(forceNewtons = null, weightKg = 80.0)
        requireForceIsLoadFree(forceNewtons = 250.0, weightKg = null)
        assertFailsWith<IllegalArgumentException> {
            requireForceIsLoadFree(forceNewtons = 250.0, weightKg = 80.0)
        }
        // An explicit zero load is still a recorded load.
        assertFailsWith<IllegalArgumentException> {
            requireForceIsLoadFree(forceNewtons = 250.0, weightKg = 0.0)
        }
    }

    @Test
    fun onlyAForceSetCarriesAMeasuringDevice() {
        val device = DeviceAttribution(name = "Grip Trainer", ratingKg = 60.0)
        requireValidDevice(forceNewtons = 250.0, device = device)
        requireValidDevice(forceNewtons = 250.0, device = null)
        assertFailsWith<IllegalArgumentException> {
            requireValidDevice(forceNewtons = null, device = device)
        }
        assertFailsWith<IllegalArgumentException> {
            requireValidDevice(forceNewtons = null, device = DeviceAttribution("Grip Trainer"))
        }
    }

    @Test
    fun aDeviceRatingIsDescriptiveAndNeverConvertedToForce() {
        val unrated = DeviceAttribution("Hand Dynamometer")
        assertNull(unrated.ratingKg)
        val rated = DeviceAttribution("Hand Dynamometer", ratingKg = 90.0)
        assertEquals(90.0, rated.ratingKg)
        assertFailsWith<IllegalArgumentException> { DeviceAttribution(" ") }
        assertFailsWith<IllegalArgumentException> { DeviceAttribution("Device", ratingKg = 0.0) }
        assertFailsWith<IllegalArgumentException> {
            DeviceAttribution("Device", ratingKg = Double.NaN)
        }
    }
}
