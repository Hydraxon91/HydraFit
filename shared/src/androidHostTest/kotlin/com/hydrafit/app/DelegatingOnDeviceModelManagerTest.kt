package com.hydrafit.app

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DelegatingOnDeviceModelManagerTest {

    @Test
    fun reportsInstalledStateFromTheDelegate() {
        assertTrue(manager(installed = true).isInstalled())
        assertFalse(manager(installed = false).isInstalled())
    }

    @Test
    fun forwardsTheInstallSourceAndReportsSuccess() {
        var received: String? = null
        val manager = DelegatingOnDeviceModelManager(
            installedCheck = { false },
            onInstall = { source -> received = source },
            onRemove = {}
        )

        assertTrue(manager.installFrom("content://model.litertlm"))
        assertTrue(received == "content://model.litertlm")
    }

    @Test
    fun reportsInstallFailureInsteadOfCrashing() {
        val manager = DelegatingOnDeviceModelManager(
            installedCheck = { false },
            onInstall = { error("copy failed") },
            onRemove = {}
        )

        assertFalse(manager.installFrom("content://model.litertlm"))
    }

    @Test
    fun reportsRemoveFailureInsteadOfCrashing() {
        val manager = DelegatingOnDeviceModelManager(
            installedCheck = { true },
            onInstall = {},
            onRemove = { error("delete failed") }
        )

        assertFalse(manager.remove())
    }

    private fun manager(installed: Boolean) = DelegatingOnDeviceModelManager(
        installedCheck = { installed },
        onInstall = {},
        onRemove = {}
    )
}
