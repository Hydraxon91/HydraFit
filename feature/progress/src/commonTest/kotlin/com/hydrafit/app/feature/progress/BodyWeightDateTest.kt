package com.hydrafit.app.feature.progress

import com.hydrafit.app.core.domain.time.daysFromCivil
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BodyWeightDateTest {

    @Test
    fun theUnixEpochIsDayZero() {
        assertEquals(0L, parseBodyWeightDate("1970-01-01"))
    }

    @Test
    fun aLeapDayIsAcceptedInALeapYear() {
        assertEquals(daysFromCivil(2024, 2, 29), parseBodyWeightDate("2024-02-29"))
    }

    @Test
    fun monthAndYearBoundariesAreContiguous() {
        assertEquals(
            parseBodyWeightDate("2024-12-31")!! + 1L,
            parseBodyWeightDate("2025-01-01")
        )
        assertEquals(daysFromCivil(2024, 2, 29), parseBodyWeightDate("2024-02-29"))
        assertEquals(daysFromCivil(2024, 3, 1), parseBodyWeightDate("2024-03-01"))
    }

    @Test
    fun anImpossibleDayIsRejectedRatherThanNormalizedForward() {
        assertNull(parseBodyWeightDate("2023-02-29"))
        assertNull(parseBodyWeightDate("2024-02-30"))
        assertNull(parseBodyWeightDate("2024-04-31"))
        assertNull(parseBodyWeightDate("2024-00-10"))
        assertNull(parseBodyWeightDate("2024-13-01"))
        assertNull(parseBodyWeightDate("2024-01-32"))
    }

    @Test
    fun malformedTextIsRejected() {
        assertNull(parseBodyWeightDate(""))
        assertNull(parseBodyWeightDate("not-a-date"))
        assertNull(parseBodyWeightDate("2024-1-1"))
        assertNull(parseBodyWeightDate("2024/01/01"))
        assertNull(parseBodyWeightDate("2024-01-01 "))
        assertNull(parseBodyWeightDate("24-01-01"))
    }
}
