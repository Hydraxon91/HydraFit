package com.hydrafit.app.feature.fatigueheatmap

import com.hydrafit.app.core.domain.fatigue.MuscleGroup

/**
 * A point on a schematic map in normalized 0..1 space, with the origin at the top-left. A value
 * outside that range is a geometry mistake, not a value to clamp at draw time.
 */
data class NormalizedPoint(val x: Float, val y: Float)

/** The two schematic views. A muscle can appear in both; it always keeps one shared score. */
enum class FatigueMapView { FRONT, BACK }

/**
 * One schematic polygon for [muscle]. Regions may overlap and are deliberately approximate: the
 * map is a diagram, not a measured anatomical segmentation.
 */
data class MapRegion(val muscle: MuscleGroup, val points: List<NormalizedPoint>)

/** A region's normalized axis-aligned extent, used for draw order and accessibility bounds. */
data class NormalizedBounds(val left: Float, val top: Float, val width: Float, val height: Float)

private val neckRegion = MapRegion(MuscleGroup.NECK, rect(0.455f, 0.08f, 0.545f, 0.15f))
private val sideDeltsRegion = MapRegion(MuscleGroup.SIDE_DELTS, rect(0.20f, 0.20f, 0.80f, 0.29f))
private val forearmsRegion = MapRegion(MuscleGroup.FOREARMS, rect(0.20f, 0.48f, 0.80f, 0.66f))
private val calvesRegion = MapRegion(MuscleGroup.CALVES, rect(0.34f, 0.84f, 0.66f, 0.97f))

private val chestUpperRegion = MapRegion(
    MuscleGroup.CHEST_UPPER,
    poly(0.36f, 0.17f, 0.64f, 0.17f, 0.63f, 0.31f, 0.37f, 0.31f)
)
private val chestLowerRegion = MapRegion(
    MuscleGroup.CHEST_LOWER,
    poly(0.37f, 0.31f, 0.63f, 0.31f, 0.61f, 0.40f, 0.39f, 0.40f)
)
private val frontDeltsRegion =
    MapRegion(MuscleGroup.FRONT_DELTS, rect(0.20f, 0.13f, 0.80f, 0.21f))
private val bicepsRegion = MapRegion(MuscleGroup.BICEPS, rect(0.19f, 0.29f, 0.81f, 0.46f))
private val absRegion = MapRegion(MuscleGroup.ABS, rect(0.36f, 0.40f, 0.64f, 0.62f))
private val obliquesRegion = MapRegion(MuscleGroup.OBLIQUES, rect(0.31f, 0.42f, 0.69f, 0.53f))
private val quadsRegion = MapRegion(MuscleGroup.QUADS, rect(0.33f, 0.62f, 0.67f, 0.82f))
private val adductorsRegion = MapRegion(MuscleGroup.ADDUCTORS, rect(0.43f, 0.62f, 0.57f, 0.82f))
private val hipAbductorsRegion =
    MapRegion(MuscleGroup.HIP_ABDUCTORS, rect(0.31f, 0.53f, 0.69f, 0.62f))

private val trapsRegion = MapRegion(
    MuscleGroup.TRAPS,
    poly(0.34f, 0.14f, 0.66f, 0.14f, 0.62f, 0.23f, 0.38f, 0.23f)
)
private val rearDeltsRegion = MapRegion(MuscleGroup.REAR_DELTS, rect(0.20f, 0.13f, 0.80f, 0.21f))
private val tricepsRegion = MapRegion(MuscleGroup.TRICEPS, rect(0.19f, 0.29f, 0.81f, 0.46f))
private val latsRegion = MapRegion(
    MuscleGroup.LATS,
    poly(0.34f, 0.33f, 0.66f, 0.33f, 0.63f, 0.44f, 0.37f, 0.44f)
)
private val upperBackRegion = MapRegion(MuscleGroup.UPPER_BACK, rect(0.37f, 0.23f, 0.63f, 0.33f))
private val lowerBackRegion = MapRegion(MuscleGroup.LOWER_BACK, rect(0.40f, 0.44f, 0.60f, 0.54f))
private val glutesRegion = MapRegion(MuscleGroup.GLUTES, rect(0.34f, 0.54f, 0.66f, 0.68f))
private val hamstringsRegion = MapRegion(MuscleGroup.HAMSTRINGS, rect(0.34f, 0.68f, 0.66f, 0.86f))

/** The front-view regions, listed in the order the acceptance contract names them. */
val frontViewRegions: List<MapRegion> = listOf(
    chestUpperRegion,
    chestLowerRegion,
    frontDeltsRegion,
    sideDeltsRegion,
    bicepsRegion,
    forearmsRegion,
    absRegion,
    obliquesRegion,
    quadsRegion,
    adductorsRegion,
    hipAbductorsRegion,
    calvesRegion,
    neckRegion
)

/** The back-view regions, listed in the order the acceptance contract names them. */
val backViewRegions: List<MapRegion> = listOf(
    trapsRegion,
    rearDeltsRegion,
    sideDeltsRegion,
    tricepsRegion,
    forearmsRegion,
    latsRegion,
    upperBackRegion,
    lowerBackRegion,
    glutesRegion,
    hamstringsRegion,
    calvesRegion,
    neckRegion
)

/** The regions for [view]. */
fun regionsFor(view: FatigueMapView): List<MapRegion> = when (view) {
    FatigueMapView.FRONT -> frontViewRegions
    FatigueMapView.BACK -> backViewRegions
}

/** The smallest axis-aligned box that contains every point of the region. */
fun MapRegion.bounds(): NormalizedBounds {
    val left = points.minOf { it.x }
    val top = points.minOf { it.y }
    val right = points.maxOf { it.x }
    val bottom = points.maxOf { it.y }
    return NormalizedBounds(left = left, top = top, width = right - left, height = bottom - top)
}

/**
 * Bounding-box area used only to order drawing: the wide limb bands are drawn before the smaller
 * central regions, so a band that crosses the midline is overpainted in the middle and reads as the
 * two separate limbs a viewer expects.
 */
fun MapRegion.boundingArea(): Float = bounds().let { it.width * it.height }

private fun rect(left: Float, top: Float, right: Float, bottom: Float): List<NormalizedPoint> =
    listOf(
        NormalizedPoint(left, top),
        NormalizedPoint(right, top),
        NormalizedPoint(right, bottom),
        NormalizedPoint(left, bottom)
    )

private fun poly(vararg coordinates: Float): List<NormalizedPoint> {
    require(coordinates.size % 2 == 0) { "Coordinates must be supplied as x/y pairs" }
    return coordinates.toList().chunked(2).map { (x, y) -> NormalizedPoint(x, y) }
}
