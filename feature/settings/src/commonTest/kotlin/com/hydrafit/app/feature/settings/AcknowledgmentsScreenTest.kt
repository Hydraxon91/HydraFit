package com.hydrafit.app.feature.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AcknowledgmentsScreenTest {

    @Test
    fun canonicalViewBoxScalesUniformlyWithoutOffsets() {
        val geometry = markGeometry(74f, 68f)
        assertEquals(1f, geometry.scale, 0.0001f)
        assertEquals(0f, geometry.offsetX, 0.0001f)
        assertEquals(0f, geometry.offsetY, 0.0001f)
    }

    @Test
    fun squareTileUsesOneUniformScaleAndCentersVertically() {
        val geometry = markGeometry(44f, 44f)
        val expectedScale = 44f / 74f
        assertEquals(expectedScale, geometry.scale, 0.0001f)
        assertEquals(0f, geometry.offsetX, 0.0001f)
        assertEquals((44f - 68f * expectedScale) / 2f, geometry.offsetY, 0.0001f)
        assertTrue(geometry.offsetY > 0f, "the shorter mark is centered, never stretched")
    }

    @Test
    fun wideCanvasKeepsTheCanonicalRatioAndCentersHorizontally() {
        val geometry = markGeometry(100f, 44f)
        val expectedScale = 44f / 68f
        assertEquals(expectedScale, geometry.scale, 0.0001f)
        assertEquals((100f - 74f * expectedScale) / 2f, geometry.offsetX, 0.0001f)
        assertEquals((44f - 68f * expectedScale) / 2f, geometry.offsetY, 0.0001f)
        assertEquals(
            74f / 68f,
            (74f * geometry.scale) / (68f * geometry.scale),
            0.0001f
        )
    }

    @Test
    fun tallCanvasKeepsTheCanonicalRatioAndCentersVertically() {
        val geometry = markGeometry(44f, 100f)
        val expectedScale = 44f / 74f
        assertEquals(expectedScale, geometry.scale, 0.0001f)
        assertEquals(0f, geometry.offsetX, 0.0001f)
        assertEquals((100f - 68f * expectedScale) / 2f, geometry.offsetY, 0.0001f)
    }

    @Test
    fun everyCanvasUsesTheSameScaleOnBothAxes() {
        listOf(
            44f to 44f,
            100f to 44f,
            44f to 100f,
            74f to 68f
        ).forEach { (width, height) ->
            val geometry = markGeometry(width, height)
            assertTrue(
                kotlin.math.abs(geometry.scale - minOf(width / 74f, height / 68f)) < 0.0001f
            )
        }
    }
}
