package com.hydrafit.app.core.database

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Cross-checks the translated-name table against the seed without a production module dependency: a
 * name is translated only while the stored name equals its canonical English name, so the table must
 * hold every seeded id with exactly the seeded name. `ExerciseNameResourcesTest` then checks that the
 * English resources equal the table.
 */
class ExerciseNameSeedCoverageTest {

    private val sourcePath = "core/exercisecontent/src/commonMain/kotlin/com/hydrafit/app/" +
        "core/exercisecontent/ExerciseNames.kt"
    private val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
        .first { File(it, sourcePath).isFile }

    @Test
    fun theCanonicalNamesAreExactlyTheSeededNames() {
        val canonical = Regex("\"([a-z0-9-]+)\" to \"([^\"]+)\"")
            .findAll(File(root, sourcePath).readText())
            .associate { it.groupValues[1] to it.groupValues[2] }
        val seeded = DefaultExercises.all.associate { it.id to it.name }
        assertEquals(seeded, canonical)
    }

    /**
     * Accepted plans, scheduled copies, backups, exports and the Groq prompt are written by the core
     * modules and by view models, so translated names stay out of stored data while neither can reach
     * them: no core module depends on `:core:exercisecontent`, and no view model calls the display lookup.
     */
    @Test
    fun translatedNamesCannotReachStoredData() {
        val coreModules = listOf("domain", "database", "network", "userdata")
        val coreDependents = coreModules.filter {
            File(root, "core/$it/build.gradle.kts").readText().contains(":core:exercisecontent")
        }
        assertEquals(emptyList(), coreDependents)

        val displayCalls = Regex("""\.display\(|exerciseDisplayName|LocalExerciseNames""")
        val viewModels = File(root, "feature").walkTopDown()
            .filter { it.isFile && it.name.endsWith("ViewModel.kt") && "commonMain" in it.path }
            .toList()
        assertTrue(viewModels.size > 5, "expected to scan the feature view models")
        val viewModelsThatTranslate = viewModels
            .filter { displayCalls.containsMatchIn(it.readText()) }
            .map { it.name }
        assertEquals(emptyList(), viewModelsThatTranslate)
    }
}
