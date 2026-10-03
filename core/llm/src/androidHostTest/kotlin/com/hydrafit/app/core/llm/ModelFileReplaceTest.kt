package com.hydrafit.app.core.llm

import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ModelFileReplaceTest {

    private val dir = File(
        System.getProperty("java.io.tmpdir"),
        "hydrafit-replace-${System.nanoTime()}"
    ).apply { mkdirs() }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun replacesTheTargetWithTheSource() {
        val source = file("source", "new")
        val target = file("target", "old")

        assertTrue(replaceFileKeepingOld(source, target))
        assertEquals("new", target.readText())
    }

    @Test
    fun restoresTheOldTargetWhenTheSecondRenameFails() {
        val source = file("source", "new")
        val target = file("target", "old")
        // Moving the new file into place always fails; moving the old file aside/back works.
        val rename: (File, File) -> Boolean = { from, to ->
            if (from.name == "source") false else from.renameTo(to)
        }

        assertFalse(replaceFileKeepingOld(source, target, rename))
        assertEquals("old", target.readText())
    }

    @Test
    fun keepsTheOldTargetWhenItCannotBeMovedAside() {
        val source = file("source", "new")
        val target = file("target", "old")

        assertFalse(replaceFileKeepingOld(source, target) { _, _ -> false })
        assertEquals("old", target.readText())
    }

    private fun file(name: String, content: String): File =
        File(dir, name).apply { writeText(content) }
}
