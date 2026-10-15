package com.hydrafit.app.feature.fatigueheatmap

import com.hydrafit.app.core.domain.fatigue.MuscleGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FatigueMapGeometryTest {

    private val frontMuscles = setOf(
        MuscleGroup.CHEST_UPPER,
        MuscleGroup.CHEST_LOWER,
        MuscleGroup.FRONT_DELTS,
        MuscleGroup.SIDE_DELTS,
        MuscleGroup.BICEPS,
        MuscleGroup.FOREARMS,
        MuscleGroup.ABS,
        MuscleGroup.OBLIQUES,
        MuscleGroup.QUADS,
        MuscleGroup.ADDUCTORS,
        MuscleGroup.HIP_ABDUCTORS,
        MuscleGroup.CALVES,
        MuscleGroup.NECK
    )

    private val backMuscles = setOf(
        MuscleGroup.TRAPS,
        MuscleGroup.REAR_DELTS,
        MuscleGroup.SIDE_DELTS,
        MuscleGroup.TRICEPS,
        MuscleGroup.FOREARMS,
        MuscleGroup.LATS,
        MuscleGroup.UPPER_BACK,
        MuscleGroup.LOWER_BACK,
        MuscleGroup.GLUTES,
        MuscleGroup.HAMSTRINGS,
        MuscleGroup.CALVES,
        MuscleGroup.NECK
    )

    private val sharedMuscles = setOf(
        MuscleGroup.SIDE_DELTS,
        MuscleGroup.FOREARMS,
        MuscleGroup.CALVES,
        MuscleGroup.NECK
    )

    @Test
    fun frontViewCoversExactlyTheListedGroupsWithoutDuplicates() {
        val muscles = frontViewRegions.map { it.muscle }
        assertEquals(frontMuscles, muscles.toSet())
        assertEquals(muscles.size, muscles.toSet().size)
    }

    @Test
    fun backViewCoversExactlyTheListedGroupsWithoutDuplicates() {
        val muscles = backViewRegions.map { it.muscle }
        assertEquals(backMuscles, muscles.toSet())
        assertEquals(muscles.size, muscles.toSet().size)
    }

    @Test
    fun unionOfBothViewsIsEveryMuscleGroup() {
        val union = (frontViewRegions + backViewRegions).map { it.muscle }.toSet()
        assertEquals(MuscleGroup.entries.toSet(), union)
        assertEquals(MuscleGroup.entries.size, union.size)
    }

    @Test
    fun sharedGroupsAppearInBothViewsWithIdenticalPoints() {
        sharedMuscles.forEach { muscle ->
            val front = frontViewRegions.first { it.muscle == muscle }
            val back = backViewRegions.first { it.muscle == muscle }
            assertEquals(front.points, back.points, "$muscle must be one shared region")
        }
    }

    @Test
    fun everyRegionHasAtLeastThreePointsInsideTheUnitSquare() {
        (frontViewRegions + backViewRegions).forEach { region ->
            assertTrue(
                region.points.size >= 3,
                "${region.muscle} has only ${region.points.size} points"
            )
            region.points.forEach { point ->
                assertTrue(
                    point.x in 0f..1f && point.y in 0f..1f,
                    "${region.muscle} point $point is outside the unit square"
                )
            }
        }
    }

    @Test
    fun regionsForSelectsTheMatchingView() {
        assertEquals(frontViewRegions, regionsFor(FatigueMapView.FRONT))
        assertEquals(backViewRegions, regionsFor(FatigueMapView.BACK))
    }

    @Test
    fun boundsAndAreaMatchThePolygonExtent() {
        val region = MapRegion(
            MuscleGroup.CHEST_UPPER,
            listOf(
                NormalizedPoint(0.2f, 0.1f),
                NormalizedPoint(0.6f, 0.1f),
                NormalizedPoint(0.6f, 0.4f),
                NormalizedPoint(0.2f, 0.4f)
            )
        )
        val bounds = region.bounds()
        assertEquals(0.2f, bounds.left, 1e-6f)
        assertEquals(0.1f, bounds.top, 1e-6f)
        assertEquals(0.4f, bounds.width, 1e-6f)
        assertEquals(0.3f, bounds.height, 1e-6f)
        assertEquals(0.12f, region.boundingArea(), 1e-6f)
    }

    /**
     * Walk the regions in the exact order the canvas paints them and require every region to keep
     * at least one sample of its bounds rectangle that no later region's bounds rectangle covers.
     * The accessibility overlay is a Box sized to those bounds rectangles, so a region fully
     * covered by later overlay boxes drops out of the accessibility tree — which is what hid Chest
     * upper, Chest lower and Traps. The guard exists so a future geometry author sees immediately
     * that reordering (or adding an overlapping region) can silently delete a semantics node.
     *
     * Containment is checked with the same `bounds()` rectangles the overlays use: each region's
     * rectangle is rasterized on a 32x32 grid of cell centres, and a region is acceptable when at
     * least one centre lies outside every later rectangle. Cell centres avoid boundary ties, so two
     * rectangles that merely touch along an edge do not count as covering each other. The ordering
     * is taken from the shared production helper and is never re-sorted here.
     */
    @Test
    fun everyCanvasOrderedRegionRetainsAVisiblePointOutsideLaterOverlays() {
        val hidden = FatigueMapView.entries.flatMap { view ->
            val ordered = canvasOrderedRegions(view)
            ordered.filterIndexed { index, region ->
                val later = ordered.drop(index + 1).map { it.bounds() }
                uncoveredSamples(region.bounds(), later).isEmpty()
            }.map { view to it.muscle }
        }
        assertTrue(
            hidden.isEmpty(),
            "regions fully covered by later overlays and lost to accessibility: $hidden"
        )
    }
}

private const val SAMPLE_GRID = 32

/**
 * The cell centres of [bounds]'s 32x32 grid that no rectangle in [coverage] contains. An empty list
 * means the region's whole overlay box is covered by later boxes.
 */
private fun uncoveredSamples(
    bounds: NormalizedBounds,
    coverage: List<NormalizedBounds>
): List<NormalizedPoint> {
    val samples = mutableListOf<NormalizedPoint>()
    for (column in 0 until SAMPLE_GRID) {
        for (row in 0 until SAMPLE_GRID) {
            val point = NormalizedPoint(
                x = bounds.left + bounds.width * (column + 0.5f) / SAMPLE_GRID,
                y = bounds.top + bounds.height * (row + 0.5f) / SAMPLE_GRID
            )
            if (coverage.none { it.contains(point) }) samples += point
        }
    }
    return samples
}

/** True when [point] lies inside the axis-aligned rectangle or on its boundary. */
private fun NormalizedBounds.contains(point: NormalizedPoint): Boolean =
    point.x in left..(left + width) && point.y in top..(top + height)
