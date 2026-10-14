package com.hydrafit.app.core.database

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Cross-check bundled cautions against real seed identities without a production module dependency. */
class ExerciseCautionSeedCoverageTest {

    @Test
    fun everyAuthoredCautionTargetsARealSeed() {
        val resourcePath = "core/exercisecontent/src/commonMain/composeResources/values/strings.xml"
        val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .first { File(it, resourcePath).isFile }
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val strings = factory.newDocumentBuilder().parse(File(root, resourcePath))
            .getElementsByTagName("string")
        val cautionIds = mutableSetOf<String>()
        val cautionKey = Regex("exercise_caution_(.+)")
        for (index in 0 until strings.length) {
            val element = strings.item(index)
            val key = element.attributes.getNamedItem("name").nodeValue
            val match = cautionKey.matchEntire(key) ?: continue
            assertTrue(element.textContent.isNotBlank(), "$key has blank text")
            assertTrue(
                cautionIds.add(match.groupValues[1].replace('_', '-')),
                "$key is duplicated"
            )
        }
        assertEquals(3, cautionIds.size, "approved 0.7.5 caution batch")
        val seedIds = DefaultExercises.all.map { it.id }.toSet()
        assertTrue(
            seedIds.containsAll(cautionIds),
            "caution IDs missing from the real seed"
        )
    }
}
