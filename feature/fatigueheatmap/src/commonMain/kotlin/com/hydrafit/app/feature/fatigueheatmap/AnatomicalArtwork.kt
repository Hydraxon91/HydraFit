package com.hydrafit.app.feature.fatigueheatmap

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.vector.PathParser
import com.hydrafit.app.core.domain.fatigue.MuscleGroup

internal enum class BodyModel { MALE, FEMALE }

internal data class ArtworkViewport(val x: Float, val y: Float, val width: Float, val height: Float)

internal fun artworkViewport(model: BodyModel, view: FatigueMapView): ArtworkViewport =
    when (model) {
        BodyModel.MALE -> ArtworkViewport(
            if (view ==
                FatigueMapView.FRONT
            ) {
                0f
            } else {
                718f
            },
            95f,
            727f,
            1280f
        )
        BodyModel.FEMALE -> ArtworkViewport(
            if (view ==
                FatigueMapView.FRONT
            ) {
                0f
            } else {
                823f
            },
            0f,
            650f,
            1450f
        )
    }

internal fun artworkResource(model: BodyModel, view: FatigueMapView): String {
    val modelName = if (model == BodyModel.MALE) "Male" else "Female"
    val viewName = if (view == FatigueMapView.FRONT) "Front" else "Back"
    return "files/bodymap/$modelName$viewName.paths"
}

internal data class ArtworkSourcePath(val slug: String, val svg: String)

internal fun parseArtworkSource(text: String): List<ArtworkSourcePath> = text.lineSequence()
    .filter { it.isNotBlank() }
    .map { line ->
        val fields = line.split('\t', limit = 2)
        require(fields.size == 2 && fields[0].isNotBlank()) { "Malformed bundled body artwork" }
        require(fields[1].startsWith('M') || fields[1].startsWith('m')) {
            "Missing SVG move command"
        }
        ArtworkSourcePath(fields[0], fields[1])
    }.toList().also { require(it.isNotEmpty()) { "Empty bundled body artwork" } }

internal fun artworkMuscle(slug: String): MuscleGroup? = when (slug) {
    "upperChest" -> MuscleGroup.CHEST_UPPER
    "lowerChest" -> MuscleGroup.CHEST_LOWER
    "frontDeltoid" -> MuscleGroup.FRONT_DELTS
    "deltoids" -> MuscleGroup.SIDE_DELTS
    "rearDeltoid" -> MuscleGroup.REAR_DELTS
    "upperBack" -> MuscleGroup.LATS
    "rhomboids", "rotatorCuff" -> MuscleGroup.UPPER_BACK
    "trapezius" -> MuscleGroup.TRAPS
    "lowerBack" -> MuscleGroup.LOWER_BACK
    "biceps" -> MuscleGroup.BICEPS
    "triceps" -> MuscleGroup.TRICEPS
    "forearm" -> MuscleGroup.FOREARMS
    "abs" -> MuscleGroup.ABS
    "obliques" -> MuscleGroup.OBLIQUES
    "quadriceps" -> MuscleGroup.QUADS
    "hamstring" -> MuscleGroup.HAMSTRINGS
    "gluteal" -> MuscleGroup.GLUTES
    "calves" -> MuscleGroup.CALVES
    "adductors" -> MuscleGroup.ADDUCTORS
    "hipAbductors" -> MuscleGroup.HIP_ABDUCTORS
    "neck" -> MuscleGroup.NECK
    "tibialis" -> MuscleGroup.TIBIALIS_ANTERIOR
    "serratus" -> MuscleGroup.SERRATUS_ANTERIOR
    "hipFlexors" -> MuscleGroup.HIP_FLEXORS
    else -> null
}

/**
 * Decorative-only artwork slugs. None maps to a [MuscleGroup], so omitting them from the render can
 * never drop a scored region; `FinalArtworkCoverageTest` holds that against [artworkMuscle].
 */
internal val artworkDecorativeSlugs = setOf(
    "upperAbs",
    "lowerAbs",
    "innerQuad",
    "outerQuad",
    "upperTrapezius",
    "lowerTrapezius"
)

/** The outer head belongs to side delts; this is an approved schematic split, not a measurement. */
internal fun outerPartition(bounds: Rect, midline: Float, fraction: Float): Rect {
    require(fraction in 0f..1f)
    val width = bounds.width * fraction
    return if (bounds.center.x < midline) {
        Rect(bounds.left, bounds.top - 2f, bounds.left + width, bounds.bottom + 2f)
    } else {
        Rect(bounds.right - width, bounds.top - 2f, bounds.right, bounds.bottom + 2f)
    }
}

internal data class ArtworkPiece(val slug: String, val path: Path)

internal data class AnatomicalArtwork(
    val viewport: ArtworkViewport,
    val pieces: List<ArtworkPiece>,
    val muscles: Map<MuscleGroup, Path>
)

private fun union(paths: List<Path>): Path = paths.fold(Path()) { result, path ->
    Path.combine(PathOperation.Union, result, path)
}

private fun difference(path: Path, cut: Path): Path =
    Path.combine(PathOperation.Difference, path, cut)

private fun intersection(path: Path, bounds: Rect): Path =
    Path.combine(PathOperation.Intersect, path, Path().apply { addRect(bounds) })

/** Parses bundled vectors once per model/view, before drawing; all boolean operations stay off-frame. */
internal fun buildAnatomicalArtwork(
    source: List<ArtworkSourcePath>,
    model: BodyModel,
    view: FatigueMapView
): AnatomicalArtwork {
    val viewport = artworkViewport(model, view)
    val back = view == FatigueMapView.BACK
    val parsed = source.map { ArtworkPiece(it.slug, PathParser().parsePathString(it.svg).toPath()) }
    fun paths(slug: String): List<Path> = parsed.filter { it.slug == slug }.map { it.path }
    val omitted = artworkDecorativeSlugs
    val splitSlugs = if (back) {
        setOf(
            "deltoids",
            "rearDeltoid",
            "rhomboids",
            "trapezius"
        )
    } else {
        emptySet()
    }
    val pieces = parsed.filterNot { it.slug in omitted || it.slug in splitSlugs }.toMutableList()
    val midline = viewport.x + viewport.width / 2f
    if (back) {
        val upperBack = mutableListOf<Path>()
        val medialBack = mutableListOf<Path>()
        paths("rhomboids").forEach { path ->
            val outer = intersection(path, outerPartition(path.getBounds(), midline, 0.55f))
            upperBack += outer
            medialBack += difference(path, outer)
        }
        val excluded = union(upperBack + paths("rotatorCuff"))
        pieces.add(
            0,
            ArtworkPiece("trapezius", difference(union(paths("trapezius") + medialBack), excluded))
        )
        pieces += upperBack.map { ArtworkPiece("rhomboids", it) }
        paths("deltoids").forEach { path ->
            val outer = intersection(path, outerPartition(path.getBounds(), midline, 0.35f))
            pieces += ArtworkPiece("deltoids", outer)
            pieces += ArtworkPiece("rearDeltoid", difference(path, outer))
        }
    } else {
        val frontDelts = union(paths("frontDeltoid"))
        pieces.replaceAllPieces("deltoids") { difference(it, frontDelts) }
        // The parent chest is decorative; only the explicit upper/lower regions carry scores.
        pieces.replaceAllPieces("chest") {
            difference(
                it,
                union(
                    paths("upperChest") + paths("lowerChest")
                )
            )
        }
    }
    val hipParent = if (back) "gluteal" else "quadriceps"
    val hipBounds = paths(hipParent).map { it.getBounds() }.filter {
        it.height > viewport.height * 0.07f && it.width > viewport.width * 0.025f
    }
    val hips = listOf(true, false).mapNotNull { left ->
        hipBounds.filter {
            (it.center.x < midline) == left
        }.maxByOrNull { it.width * it.height }?.let { bounds ->
            val x = if (left) bounds.left else bounds.right
            val direction = if (left) 1f else -1f
            val width = bounds.width * 0.32f
            val height = bounds.height * 0.26f
            val y = bounds.top
            Path().apply {
                moveTo(x, y + height)
                quadraticTo(
                    x - direction * width * 0.12f,
                    y + height * 0.25f,
                    x + direction * width * 0.45f,
                    y - height * 0.15f
                )
                lineTo(x + direction * width, y + height * 0.2f)
                lineTo(x + direction * width * 0.65f, y + height)
                close()
            }
        }
    }
    require(hips.size == 2) { "Bundled artwork must provide both schematic hips" }
    val hipPath = union(hips)
    pieces.replaceAllPieces(hipParent) { difference(it, hipPath) }
    pieces += ArtworkPiece("hipAbductors", hipPath)
    val muscles = pieces.filter {
        artworkMuscle(it.slug) != null
    }.groupBy { artworkMuscle(it.slug)!! }
        .mapValues { (_, parts) -> union(parts.map { it.path }) }
    return AnatomicalArtwork(viewport, pieces, muscles)
}

private fun MutableList<ArtworkPiece>.replaceAllPieces(slug: String, transform: (Path) -> Path) {
    indices.forEach { index ->
        if (this[index].slug ==
            slug
        ) {
            this[index] = this[index].copy(path = transform(this[index].path))
        }
    }
}

internal fun boundaryBlendWeight(step: Int, steps: Int = 8): Float {
    require(steps > 0 && step in 1..steps)
    return 0.5f * (steps - step + 1) / steps
}

internal fun anatomicalBlendPairs(view: FatigueMapView): List<Pair<MuscleGroup, MuscleGroup>> =
    when (view) {
        FatigueMapView.FRONT -> listOf(MuscleGroup.FRONT_DELTS to MuscleGroup.SIDE_DELTS)
        FatigueMapView.BACK -> listOf(
            MuscleGroup.SIDE_DELTS to MuscleGroup.REAR_DELTS,
            MuscleGroup.TRAPS to MuscleGroup.UPPER_BACK
        )
    }
