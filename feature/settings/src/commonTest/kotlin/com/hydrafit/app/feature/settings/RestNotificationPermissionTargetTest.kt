package com.hydrafit.app.feature.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RestNotificationPermissionTargetTest {
    @Test
    fun resultIsRoutedToTheTargetThatRequestedIt() {
        val requests = RestNotificationPermissionRequests()

        assertTrue(requests.begin(RestNotificationPermissionTarget.ALERTS))
        assertEquals(RestNotificationPermissionTarget.ALERTS, requests.pending)
        assertEquals(RestNotificationPermissionTarget.ALERTS, requests.takeResult())
        assertNull(requests.pending)
    }

    @Test
    fun secondRequestWhileOneIsPendingIsIgnoredAndKeepsTheFirstTarget() {
        val requests = RestNotificationPermissionRequests()

        assertTrue(requests.begin(RestNotificationPermissionTarget.ALERTS))
        assertFalse(requests.begin(RestNotificationPermissionTarget.COUNTDOWN))

        // The answer still belongs to the first request, not the ignored second one.
        assertEquals(RestNotificationPermissionTarget.ALERTS, requests.takeResult())
        assertNull(requests.takeResult())
    }

    @Test
    fun aResultWithoutAPendingRequestEnablesNothing() {
        val requests = RestNotificationPermissionRequests()

        // Such as an answer delivered after recreation cleared the in-memory request.
        assertNull(requests.takeResult())
        assertNull(requests.pending)
    }

    @Test
    fun laterRequestsRouteIndependentlyAfterTheFirstCompletes() {
        val requests = RestNotificationPermissionRequests()

        assertTrue(requests.begin(RestNotificationPermissionTarget.COUNTDOWN))
        assertEquals(RestNotificationPermissionTarget.COUNTDOWN, requests.takeResult())

        assertTrue(requests.begin(RestNotificationPermissionTarget.ALERTS))
        assertEquals(RestNotificationPermissionTarget.ALERTS, requests.takeResult())
    }
}
