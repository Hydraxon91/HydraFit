package com.hydrafit.app.core.domain.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SlotProgressionPolicyTest {

    @Test
    fun aPolicyRoundTripsThroughItsStrictEncoding() {
        val policy = SlotProgressionPolicy(
            minReps = 5,
            maxReps = 8,
            incrementKg = 2.5,
            equipmentId = "olympic-bar"
        )

        val decoded = SlotProgressionPolicyJson.decode(SlotProgressionPolicyJson.encode(policy))

        assertEquals(policy, decoded)
    }

    @Test
    fun aPolicyWithoutAnEquipmentIdentityRoundTrips() {
        val policy = SlotProgressionPolicy(minReps = 8, maxReps = 12, incrementKg = 1.25)

        val decoded = SlotProgressionPolicyJson.decode(SlotProgressionPolicyJson.encode(policy))

        assertEquals(policy, decoded)
        assertNull(decoded.equipmentId)
    }

    @Test
    fun aSingleRepRangeIsAllowed() {
        SlotProgressionPolicy(minReps = 5, maxReps = 5, incrementKg = 2.5).requireValid()
    }

    @Test
    fun outOfRangeOrInvertedBoundsAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            SlotProgressionPolicy(minReps = 0, maxReps = 5, incrementKg = 2.5).requireValid()
        }
        assertFailsWith<IllegalArgumentException> {
            SlotProgressionPolicy(minReps = 8, maxReps = 5, incrementKg = 2.5).requireValid()
        }
        assertFailsWith<IllegalArgumentException> {
            SlotProgressionPolicy(minReps = 5, maxReps = 101, incrementKg = 2.5).requireValid()
        }
    }

    @Test
    fun aNonPositiveOrNonFiniteIncrementIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            SlotProgressionPolicy(minReps = 5, maxReps = 8, incrementKg = 0.0).requireValid()
        }
        assertFailsWith<IllegalArgumentException> {
            SlotProgressionPolicy(minReps = 5, maxReps = 8, incrementKg = -2.5).requireValid()
        }
        assertFailsWith<IllegalArgumentException> {
            SlotProgressionPolicy(minReps = 5, maxReps = 8, incrementKg = Double.NaN).requireValid()
        }
    }

    @Test
    fun aBlankOrUntrimmedEquipmentIdentityIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            SlotProgressionPolicy(
                minReps = 5,
                maxReps = 8,
                incrementKg = 2.5,
                equipmentId = " "
            ).requireValid()
        }
        assertFailsWith<IllegalArgumentException> {
            SlotProgressionPolicy(
                minReps = 5,
                maxReps = 8,
                incrementKg = 2.5,
                equipmentId = " bar"
            ).requireValid()
        }
    }

    @Test
    fun anUnsupportedVersionIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            SlotProgressionPolicy(
                version = 2,
                minReps = 5,
                maxReps = 8,
                incrementKg = 2.5
            ).requireValid()
        }
    }

    @Test
    fun corruptStoredTextFailsClosedInsteadOfBecomingAnApprovedRule() {
        assertFailsWith<Exception> {
            SlotProgressionPolicyJson.decode(
                """{"version":1,"minReps":8,"maxReps":5,"incrementKg":2.5}"""
            )
        }
        assertFailsWith<Exception> { SlotProgressionPolicyJson.decode("not json") }
    }
}
