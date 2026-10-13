package com.hydrafit.app.core.database

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Cross-check bundled content against real seed identities without a production module dependency. */
class ExerciseInstructionSeedCoverageTest {

    @Test
    fun everyAuthoredInstructionTargetsARealSeedAndHasBothSections() {
        val resourcePath = "core/exercisecontent/src/commonMain/composeResources/values/strings.xml"
        val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .first { File(it, resourcePath).isFile }
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
        val strings = factory.newDocumentBuilder().parse(File(root, resourcePath))
            .getElementsByTagName("string")
        val sectionsById = mutableMapOf<String, MutableSet<String>>()
        val sectionKey = Regex("exercise_instruction_(.+)_(setup|execution)")
        for (index in 0 until strings.length) {
            val element = strings.item(index)
            val key = element.attributes.getNamedItem("name").nodeValue
            val match = sectionKey.matchEntire(key) ?: continue
            val id = match.groupValues[1].replace('_', '-')
            val section = match.groupValues[2]
            assertTrue(element.textContent.isNotBlank(), "$key has blank text")
            assertTrue(
                sectionsById.getOrPut(id) { mutableSetOf() }.add(section),
                "$key is duplicated"
            )
        }
        assertEquals(
            17,
            sectionsById.size,
            "approved batch includes the separate machine calf raise"
        )
        val seedIds = DefaultExercises.all.map { it.id }.toSet()
        assertTrue(
            seedIds.containsAll(sectionsById.keys),
            "instruction IDs missing from the real seed"
        )
        sectionsById.forEach { (id, sections) ->
            assertEquals(setOf("setup", "execution"), sections, "$id has incomplete instructions")
        }
    }
}
