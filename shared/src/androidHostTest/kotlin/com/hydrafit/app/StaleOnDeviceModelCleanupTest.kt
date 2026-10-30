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
        val temporary = File(dir, "on_device_llm.litertlm.tmp").apply { writeText("partial") }

        val removed = StaleOnDeviceModelCleanup(dir).run()

        assertEquals(
            listOf(
                "on_device_llm.litertlm",
                "on_device_llm.target",
                "on_device_llm.litertlm.tmp"
            ),
            removed
        )
        assertFalse(model.exists())
        assertFalse(target.exists())
        assertFalse(temporary.exists())
    }

    @Test
    fun leavesASimilarlyNamedTemporaryFileAlone() {
        val other = File(dir, "on_device_llm.litertlm.tmp.bak").apply { writeText("x") }

        assertTrue(StaleOnDeviceModelCleanup(dir).run().isEmpty())
        assertTrue(other.exists())
    }

    @Test
    fun leavesUnrelatedFilesAndIsIdempotent() {
        val other = File(dir, "profileInstalled").apply { writeText("x") }

        assertTrue(StaleOnDeviceModelCleanup(dir).run().isEmpty())
        assertTrue(StaleOnDeviceModelCleanup(dir).run().isEmpty())
        assertTrue(other.exists())
    }
}
