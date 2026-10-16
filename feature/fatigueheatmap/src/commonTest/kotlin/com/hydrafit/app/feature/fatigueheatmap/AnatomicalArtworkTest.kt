package com.hydrafit.app.feature.fatigueheatmap

import androidx.compose.ui.geometry.Rect
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AnatomicalArtworkTest {
    @Test
    fun sourceParsingKeepsEveryPathAndRejectsMalformedBundledData() {
        val paths = parseArtworkSource("chest\tM1 2L3 4Z\nchest\tm2 3l4 5z\n\n")
        assertEquals(listOf("chest", "chest"), paths.map { it.slug })
        assertEquals("m2 3l4 5z", paths[1].svg)
        listOf("", "chest", "\tM0 0Z", "chest\tL0 0Z").forEach { malformed ->
            assertFailsWith<IllegalArgumentException> { parseArtworkSource(malformed) }
        }
    }

    @Test
    fun visualSlugsCoverExactlyTheExistingMuscleSet() {
        val slugs = listOf(
            "upperChest", "lowerChest", "frontDeltoid", "deltoids", "rearDeltoid", "upperBack",
            "rhomboids", "rotatorCuff", "trapezius", "lowerBack", "biceps", "triceps", "forearm",
            "abs", "obliques", "quadriceps", "hamstring", "gluteal", "calves", "adductors",
            "hipAbductors", "neck"
        )
        assertEquals(MuscleGroup.entries.toSet(), slugs.mapNotNull { artworkMuscle(it) }.toSet())
        assertEquals(MuscleGroup.LATS, artworkMuscle("upperBack"))
        assertEquals(MuscleGroup.UPPER_BACK, artworkMuscle("rhomboids"))
        assertEquals(MuscleGroup.UPPER_BACK, artworkMuscle("rotatorCuff"))
        listOf("hair", "head", "hands", "feet", "chest", "serratus", "hipFlexors").forEach {
            assertNull(artworkMuscle(it))
        }
    }

    @Test
    fun shoulderAndBackPartitionAlwaysSelectTheOuterSideSymmetrically() {
        for (fraction in listOf(0.35f, 0.55f)) {
            val left = outerPartition(Rect(10f, 20f, 110f, 80f), 200f, fraction)
            val right = outerPartition(Rect(290f, 20f, 390f, 80f), 200f, fraction)
            assertEquals(10f, left.left)
            assertEquals(390f, right.right)
            assertEquals(100f * fraction, left.width, 0.001f)
            assertEquals(left.width, right.width, 0.001f)
            assertEquals(left.top, right.top)
            assertEquals(left.bottom, right.bottom)
        }
        assertFailsWith<IllegalArgumentException> { outerPartition(Rect.Zero, 0f, 1.1f) }
    }

    @Test
    fun eachModelAndViewHasItsOwnOfflineResourceAndViewport() {
        val resources = BodyModel.entries.flatMap { model ->
            FatigueMapView.entries.map { view ->
                val viewport = artworkViewport(model, view)
                assertTrue(viewport.width > 0f && viewport.height > 0f)
                assertTrue(artworkResource(model, view).startsWith("files/bodymap/"))
                artworkResource(model, view)
            }
        }
        assertEquals(4, resources.toSet().size)
        assertEquals(718f, artworkViewport(BodyModel.MALE, FatigueMapView.BACK).x)
        assertEquals(823f, artworkViewport(BodyModel.FEMALE, FatigueMapView.BACK).x)
    }

    @Test
    fun blendingIsNarrowSymmetricAndLimitedToApprovedNeighbours() {
        val weights = (1..8).map { boundaryBlendWeight(it) }
        assertEquals(0.5f, weights.first())
        assertTrue(weights.all { it > 0f && it <= 0.5f })
        assertTrue(weights.zipWithNext().all { (a, b) -> a > b })
        assertEquals(
            listOf(MuscleGroup.FRONT_DELTS to MuscleGroup.SIDE_DELTS),
            anatomicalBlendPairs(FatigueMapView.FRONT)
        )
        assertEquals(
            listOf(
                MuscleGroup.SIDE_DELTS to MuscleGroup.REAR_DELTS,
                MuscleGroup.TRAPS to MuscleGroup.UPPER_BACK
            ),
            anatomicalBlendPairs(FatigueMapView.BACK)
        )
        assertFailsWith<IllegalArgumentException> { boundaryBlendWeight(0) }
    }
}
