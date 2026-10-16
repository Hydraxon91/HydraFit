package com.hydrafit.app.feature.fatigueheatmap

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.compose.ui.graphics.asAndroidPath
import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Builds the real bundled artwork and inspects the finished geometry: every muscle a model/view
 * contributes must survive the omission, partition and hip-patch steps with a non-empty region, and
 * all 24 groups must be covered across the four figures.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class FinalArtworkCoverageTest {
    private val resources = File("src/commonMain/composeResources/files/bodymap")

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun expandedFrontRegionsRemainVisibleAfterPaintOrderWithoutColouringOtherRegions() {
        val expanded = setOf(
            MuscleGroup.TIBIALIS_ANTERIOR,
            MuscleGroup.SERRATUS_ANTERIOR,
            MuscleGroup.HIP_FLEXORS
        )
        for (model in BodyModel.entries) {
            fun artwork(view: FatigueMapView): AnatomicalArtwork {
                val filename = artworkResource(model, view).substringAfterLast('/')
                return buildAnatomicalArtwork(
                    parseArtworkSource(File(resources, filename).readText()),
                    model,
                    view
                )
            }
            val front = artwork(FatigueMapView.FRONT)
            assertTrue(front.muscles.keys.containsAll(expanded))
            assertTrue(artwork(FatigueMapView.BACK).muscles.keys.intersect(expanded).isEmpty())
            // These groups do not use boundary feathers. Rasterize the renderer's piece order.
            fun render(active: MuscleGroup?): Bitmap {
                val bitmap = Bitmap.createBitmap(750, 1500, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                val paint = Paint()
                front.pieces.forEach { piece ->
                    paint.color = if (active != null && artworkMuscle(piece.slug) == active) {
                        Color.RED
                    } else {
                        Color.GRAY
                    }
                    canvas.drawPath(piece.path.asAndroidPath(), paint)
                }
                return bitmap
            }
            val baseline = render(null)
            expanded.forEach { muscle ->
                val image = render(muscle)
                val mask = Bitmap.createBitmap(750, 1500, Bitmap.Config.ARGB_8888)
                // Use the original pieces: boolean unions can rasterize their edges differently.
                val maskCanvas = Canvas(mask)
                maskCanvas.drawColor(Color.BLACK)
                val maskPaint = Paint().apply { color = Color.RED }
                front.pieces.filter { artworkMuscle(it.slug) == muscle }.forEach {
                    maskCanvas.drawPath(it.path.asAndroidPath(), maskPaint)
                }
                var visible = 0
                for (y in 0 until image.height) {
                    for (x in 0 until image.width) {
                        val pixel = image.getPixel(x, y)
                        if (pixel == Color.RED) visible++
                        if (Color.red(mask.getPixel(x, y)) == 0) {
                            val neutral = baseline.getPixel(x, y)
                            // Sub-byte edge coverage can round to zero in the black mask but
                            // change an already painted channel by one during compositing.
                            val difference = maxOf(
                                abs(Color.red(neutral) - Color.red(pixel)),
                                abs(Color.green(neutral) - Color.green(pixel)),
                                abs(Color.blue(neutral) - Color.blue(pixel))
                            )
                            assertTrue(difference <= 1, "$model/$muscle/$x/$y: $difference")
                        }
                    }
                }
                assertTrue(visible > 0, "$model/$muscle is fully occluded")
                mask.recycle()
                image.recycle()
            }
            baseline.recycle()
        }
    }

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
