package com.hydrafit.app.feature.fatigueheatmap

import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Builds the real bundled artwork and inspects the finished geometry: every muscle a model/view
 * contributes must survive the omission, partition and hip-patch steps with a non-empty region, and
 * all 21 groups must be covered across the four figures.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class FinalArtworkCoverageTest {
    private val resources = File("src/commonMain/composeResources/files/bodymap")

    @Test
    fun everyMuscleKeepsANonEmptyRegionInEveryModelAndView() {
        val covered = mutableSetOf<MuscleGroup>()
        for (model in BodyModel.entries) {
            for (view in FatigueMapView.entries) {
                val filename = artworkResource(model, view).substringAfterLast('/')
                val source = parseArtworkSource(File(resources, filename).readText())
                val artwork = buildAnatomicalArtwork(source, model, view)
                // Only the schematic hip patch is generated; every other group comes from the file.
                val sourced = source.mapNotNull { artworkMuscle(it.slug) }.toSet()
                assertEquals(sourced + MuscleGroup.HIP_ABDUCTORS, artwork.muscles.keys, filename)
                artwork.muscles.forEach { (muscle, path) ->
                    val bounds = path.getBounds()
                    assertTrue(
                        bounds.width > 0f && bounds.height > 0f,
                        "$filename: $muscle lost its region"
                    )
                }
                anatomicalBlendPairs(view).forEach { (a, b) ->
                    assertTrue(
                        artwork.muscles.containsKey(a) && artwork.muscles.containsKey(b),
                        "$filename: a blended neighbour is missing"
                    )
                }
                covered += artwork.muscles.keys
            }
        }
        assertEquals(MuscleGroup.entries.toSet(), covered)
    }

    @Test
    fun omittingDecorativeSlugsCanNeverDropAScoredRegion() {
        artworkDecorativeSlugs.forEach { slug ->
            assertNull(artworkMuscle(slug), "$slug is expected to be decorative-only")
        }
    }
}
