package com.hydrafit.app.feature.fatigueheatmap

import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BundledArtworkTest {
    private val resources = File("src/commonMain/composeResources/files/bodymap")

    @Test
    fun pinnedResourcesKeepAllExtractedPathsAndTheirExactBytes() {
        val expected = mapOf(
            "MaleFront" to
                (123 to "cdd7b86b3de0a2c4c78751feeeb8af07e7279c95fb7eb8bbb407d23c24019383"),
            "MaleBack" to
                (78 to "9d8129459bfa97343f60e52a0f88b8f24514a9cd4972abbefc1c9a9b21037909"),
            "FemaleFront" to
                (124 to "6697f3e6a7565e01f71633f3c52b116bc942455fa958ec47cee57ae826da416d"),
            "FemaleBack" to
                (78 to "b80efd953320b62ffdf8600f19a56b544f71868f7641ff1c8d0a4128bd5a63a8")
        )
        for (model in BodyModel.entries) {
            for (view in FatigueMapView.entries) {
                val filename = artworkResource(model, view).substringAfterLast('/')
                val file = File(resources, filename)
                val (count, digest) = expected.getValue(filename.removeSuffix(".paths"))
                assertEquals(
                    digest,
                    MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") {
                        "%02x".format(it)
                    }
                )
                val paths = parseArtworkSource(file.readText())
                assertEquals(count, paths.size)
                val muscles = paths.mapNotNull { artworkMuscle(it.slug) }.toSet()
                anatomicalBlendPairs(view).forEach { (a, b) ->
                    assertTrue(a in muscles && b in muscles, "$filename lacks a blend neighbour")
                }
            }
        }
    }

    @Test
    fun bothCopyrightNoticesShipAlongsideTheArtwork() {
        val notices = File(resources, "LICENSES.txt").readText()
        assertTrue(notices.contains("Copyright (c) 2026 Melih Colpan"))
        assertTrue(notices.contains("Copyright (c) 2022 ELABBASSI Hicham"))
        assertEquals(2, Regex("MIT License").findAll(notices).count())
    }
}
