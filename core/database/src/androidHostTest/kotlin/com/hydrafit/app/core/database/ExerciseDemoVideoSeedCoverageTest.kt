package com.hydrafit.app.core.database

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** Cross-checks the curated demo videos against real seed IDs without a production module dependency. */
class ExerciseDemoVideoSeedCoverageTest {

    @Test
    fun everyCuratedVideoTargetsARealSeededExercise() {
        val sourcePath = "core/exercisecontent/src/commonMain/kotlin/com/hydrafit/app/" +
            "core/exercisecontent/ExerciseDemoVideos.kt"
        val root = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .first { File(it, sourcePath).isFile }
        val ids = Regex("\"([a-z0-9-]+)\" to \"")
            .findAll(File(root, sourcePath).readText())
            .map { it.groupValues[1] }
            .toList()
        assertTrue(ids.isNotEmpty(), "no curated videos found in the bundled source")
        assertTrue(ids.size == ids.toSet().size, "a curated exercise ID is listed twice")
        val seedIds = DefaultExercises.all.map { it.id }.toSet()
        val unknown = ids.filterNot { it in seedIds }
        assertTrue(unknown.isEmpty(), "curated videos for unknown seed IDs: $unknown")
    }
}
