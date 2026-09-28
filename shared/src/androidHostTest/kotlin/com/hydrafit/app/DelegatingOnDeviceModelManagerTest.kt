package com.hydrafit.app

import com.hydrafit.app.core.llm.InsufficientStorageException
import com.hydrafit.app.core.llm.ModelSourceUnreadableException
import com.hydrafit.app.core.userdata.llm.ModelUpdateResult
import kotlin.test.Test
import kotlin.test.assertEquals

class DelegatingOnDeviceModelManagerTest {

    @Test
    fun reportsInstalledStateFromTheDelegate() {
        assertEquals(true, manager(installed = true).isInstalled())
        assertEquals(false, manager(installed = false).isInstalled())
    }

    @Test
    fun forwardsTheInstallSourceAndReportsSuccess() {
        var received: String? = null
        val manager = DelegatingOnDeviceModelManager(
            installedCheck = { false },
            onInstall = { source -> received = source },
            onRemove = {}
        )

        assertEquals(ModelUpdateResult.SUCCESS, manager.installFrom("content://model.litertlm"))
        assertEquals("content://model.litertlm", received)
    }

    @Test
    fun mapsInsufficientStorageFailures() {
        val manager = DelegatingOnDeviceModelManager(
            installedCheck = { false },
            onInstall = { throw InsufficientStorageException("full") },
            onRemove = {}
        )

        assertEquals(ModelUpdateResult.INSUFFICIENT_STORAGE, manager.installFrom("content://m"))
    }

    @Test
    fun mapsUnreadableSourceFailures() {
        val manager = DelegatingOnDeviceModelManager(
            installedCheck = { false },
            onInstall = { throw ModelSourceUnreadableException("gone") },
            onRemove = {}
        )

        assertEquals(ModelUpdateResult.UNREADABLE_SOURCE, manager.installFrom("content://m"))
    }

    @Test
    fun mapsUnknownFailures() {
        val manager = DelegatingOnDeviceModelManager(
            installedCheck = { true },
            onInstall = {},
            onRemove = { error("delete failed") }
        )

        assertEquals(ModelUpdateResult.FAILED, manager.remove())
    }

    private fun manager(installed: Boolean) = DelegatingOnDeviceModelManager(
        installedCheck = { installed },
        onInstall = {},
        onRemove = {}
    )
}
