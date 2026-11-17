package com.hydrafit.app.feature.fatigueheatmap

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BundledArtworkTest {
    private val resources = File("src/commonMain/composeResources/files/bodymap")

    @Test
    fun bothCopyrightNoticesShipAlongsideTheArtwork() {
        val notices = File(resources, "LICENSES.txt").readText()
        assertTrue(notices.contains("Copyright (c) 2026 Melih Colpan"))
        assertTrue(notices.contains("Copyright (c) 2022 ELABBASSI Hicham"))
        assertEquals(2, Regex("MIT License").findAll(notices).count())
    }
}
