package com.hydrafit.app

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StaleOnDeviceModelCleanupTest {

    private val dir = File.createTempFile("hf-model-cleanup", null).let { file ->
        file.delete()
        file.mkdirs()
        file
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun removesBothRetiredModelFiles() {
        val model = File(dir, "on_device_llm.litertlm").apply { writeText("weights") }
        val target = File(dir, "on_device_llm.target").apply { writeText("CPU_GPU") }

        val removed = StaleOnDeviceModelCleanup(dir).run()

        assertEquals(listOf("on_device_llm.litertlm", "on_device_llm.target"), removed)
        assertFalse(model.exists())
        assertFalse(target.exists())
    }

    @Test
    fun leavesUnrelatedFilesAndIsIdempotent() {
        val other = File(dir, "profileInstalled").apply { writeText("x") }

        assertTrue(StaleOnDeviceModelCleanup(dir).run().isEmpty())
        assertTrue(StaleOnDeviceModelCleanup(dir).run().isEmpty())
        assertTrue(other.exists())
    }
}
