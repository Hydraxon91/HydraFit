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
     * Walk the regions in the exact order the canvas paints them (largest first) and require every
     * region to keep at least one interior sample that no later region contains. A region whose every
     * sample is covered by a later region is overpainted on the canvas and drops out of the semantics
     * tree, which is what hid Traps, Chest upper and Chest lower.
     */
    @Test
    fun everyCanvasOrderedRegionRetainsAVisiblePointOutsideLaterOverlays() {
        val hidden = FatigueMapView.entries.flatMap { view ->
            val ordered = regionsFor(view).sortedByDescending { it.boundingArea() }
            ordered.filterIndexed { index, region ->
                val later = ordered.drop(index + 1)
                interiorSamples(region).none { sample ->
                    later.none { contains(it.points, sample) }
                }
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
 * Sample points strictly inside [region]'s polygon, laid out on a grid across its bounding box. They
 * stand in for the pixels the canvas paints for the region; a sample that a later region also
 * contains is overpainted, so a region with no surviving sample is invisible to the eye and to the
 * accessibility tree alike. The grid is finer than the thinnest visible sliver in the current
 * geometry, so it does not report a false hide for a merely partially covered region.
 */
private fun interiorSamples(region: MapRegion): List<NormalizedPoint> {
    val bounds = region.bounds()
    val samples = mutableListOf<NormalizedPoint>()
    for (column in 0 until SAMPLE_GRID) {
        for (row in 0 until SAMPLE_GRID) {
            val point = NormalizedPoint(
                x = bounds.left + bounds.width * (column + 0.5f) / SAMPLE_GRID,
                y = bounds.top + bounds.height * (row + 0.5f) / SAMPLE_GRID
            )
            if (isStrictlyInside(point, region.points)) samples += point
        }
    }
    return samples
}

/** True when [point] lies strictly inside the convex [polygon] (its vertices given in order). */
private fun isStrictlyInside(point: NormalizedPoint, polygon: List<NormalizedPoint>): Boolean {
    var hasPositive = false
    var hasNegative = false
    for (index in polygon.indices) {
        val cross = crossProduct(polygon[index], polygon[(index + 1) % polygon.size], point)
        if (cross > 0f) hasPositive = true
        if (cross < 0f) hasNegative = true
    }
    return hasPositive != hasNegative
}

/** True when [point] lies inside the convex [polygon] or on its boundary. */
private fun contains(polygon: List<NormalizedPoint>, point: NormalizedPoint): Boolean {
    var hasPositive = false
    var hasNegative = false
    for (index in polygon.indices) {
        val cross = crossProduct(polygon[index], polygon[(index + 1) % polygon.size], point)
        if (cross > 0f) hasPositive = true
        if (cross < 0f) hasNegative = true
    }
    return !(hasPositive && hasNegative)
}

private fun crossProduct(a: NormalizedPoint, b: NormalizedPoint, point: NormalizedPoint): Float =
    (b.x - a.x) * (point.y - a.y) - (b.y - a.y) * (point.x - a.x)
