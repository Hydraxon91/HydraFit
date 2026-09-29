package com.hydrafit.app.core.domain.time

import kotlin.test.Test
import kotlin.test.assertEquals

class IsoDateTest {

    @Test
    fun formatsTheEpoch() {
        assertEquals("1970-01-01", isoDateUtc(0L))
    }

    @Test
    fun formatsALaterDate() {
        // 2024-01-01T00:00:00Z
        assertEquals("2024-01-01", isoDateUtc(1_704_067_200_000L))
    }

    @Test
    fun formatsADateLateInAYear() {
        // 1999-12-31T00:00:00Z
        assertEquals("1999-12-31", isoDateUtc(946_598_400_000L))
    }
}
