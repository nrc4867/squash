package dev.chieppa.squash.screens.surface

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.character.build.PartLines
import dev.chieppa.squash.character.build.ResolvedMark
import dev.chieppa.squash.character.build.WeightedRun
import dev.chieppa.squash.geom.ContourPoint
import dev.chieppa.squash.geom.ContourRun
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.DepthLayer
import dev.chieppa.squash.render.LineRule
import dev.chieppa.squash.render.RibbonDraw
import dev.chieppa.squash.render.RibbonPoint
import dev.chieppa.squash.render.StrokeRibbon
import dev.chieppa.squash.screens.sheet.SurfaceView
import dev.chieppa.squash.screens.sheet.markProfile
import kotlin.math.max

/** How long on screen a contour must run to take its full weight; a shorter one thins with its length, so it grows from nothing and never pops. */
private const val FULL_RUN_PIXELS = 24f

/**
 * How far an interior contour sits toward the camera, as a share of the figure's radius: enough to clear the surface turning away
 * under it, and little enough that one buried in a part it runs under stays buried.
 */
private const val CONTOUR_LIFT_SHARE = 0.008f

/** How far apart on screen a contour's points lie, in pixels. */
private const val LEAST_SPACING = 2f

/** Which slices of the depth buffer a figure's lines use: [behind] for what shows only clear of it, [over] for what it draws on. */
class LineLayers(val behind: DepthLayer, val over: DepthLayer) {
    companion object {
        /** A whole figure in the scene, which clears only what lies behind it all. */
        val FIGURE = LineLayers(DepthLayer.BACK, DepthLayer.SCENE)

        /** A selection drawn in front of what is ghosted around it. */
        val SELECTION = LineLayers(DepthLayer.BEHIND_FRONT, DepthLayer.FRONT)
    }
}

/** One ribbon along where [parts] turn edge on through [view], weighed by what lies on their surfaces, or null with none on show. */
fun contourRibbon(parts: Collection<PartLines>, view: SurfaceView): StrokeRibbon? {
    val ribbons = parts.flatMap { part -> part.grids.flatMap { grid -> gridRibbons(grid.contours(view::toViewer), view) } }
    return StrokeRibbon.joined(ribbons)
}

/** Each of [runs] as a ribbon, thinned by the length on screen of the connected piece it belongs to, however its edges branch. */
private fun gridRibbons(runs: List<ContourRun>, view: SurfaceView): List<StrokeRibbon> {
    val lengths = runs.groupBy { it.piece }.mapValues { (_, piece) -> piece.sumOf { pixelsAlong(it, view).toDouble() }.toFloat() }
    return runs.mapNotNull { run ->
        val fade = (lengths.getValue(run.piece) / FULL_RUN_PIXELS).coerceAtMost(1f)
        if (fade <= 0f) null else StrokeRibbon.along(spacedOnScreen(run, view).map { RibbonPoint(it.position, Vec3.ZERO, it.weight * fade) }, EVEN_WEIGHT, run.closed)
    }
}

/**
 * [run]'s points at least [LEAST_SPACING] apart on screen, its ends kept. Where a contour runs along the view its points land on one
 * pixel, and a ribbon, which takes its direction from the screen, would have none to go by.
 */
private fun spacedOnScreen(run: ContourRun, view: SurfaceView): List<ContourPoint> {
    val kept = ArrayList<ContourPoint>(run.points.size)
    var last: Offset? = null
    for ((i, point) in run.points.withIndex()) {
        val at = view.toPixel(point.position) ?: continue
        val held = last
        if (held == null || i == run.points.lastIndex || (at - held).getDistance() >= LEAST_SPACING) {
            kept += point
            last = at
        }
    }
    return kept
}

private fun pixelsAlong(run: ContourRun, view: SurfaceView): Float {
    var pixels = 0f
    var last = view.toPixel(run.points.first().position)
    for (point in run.points) {
        val at = view.toPixel(point.position)
        if (at != null && last != null) pixels += (at - last).getDistance()
        last = at ?: last
    }
    return pixels
}

/**
 * [contour] at the two weights of [rule]: thick where it clears the figure, thin wherever it shows. The thin line lies half inside
 * the edge, and the thick one shows only outside it, so together they read as the silhouette's weight across.
 */
fun contourDraws(contour: StrokeRibbon, rule: LineRule, color: Color, layers: LineLayers, figureRadius: Float): List<RibbonDraw> {
    val outer = max(0f, 2f * rule.silhouettePixels - rule.interiorPixels)
    return listOf(
        RibbonDraw(contour, LineRule(outer), color, layer = layers.behind, lift = 0f),
        RibbonDraw(contour, LineRule(rule.interiorPixels), color, layer = layers.over, lift = figureRadius * CONTOUR_LIFT_SHARE),
    )
}

/** The ink where [part] leaves its parent, at the weight on the surface there. */
fun creaseDraws(part: PartLines, rule: LineRule, color: Color, layer: DepthLayer): List<RibbonDraw> = part.crease.mapNotNull { run ->
    weightedRibbon(run)?.let { RibbonDraw(it, rule, color, layer = layer) }
}

private fun weightedRibbon(run: WeightedRun): StrokeRibbon? {
    val points = run.points.mapIndexed { i, point -> RibbonPoint(point.position, point.normal, run.weights[i]) }
    return StrokeRibbon.along(points, EVEN_WEIGHT, run.points.size > 2 && run.points.first().anchor == run.points.last().anchor)
}

/**
 * This mark as an inked line at the weight on the surface under it, its ends narrowing by [taper], the share of its length from 0 to
 * 1 that the line rules name. Null when it has no ribbon or is not inked.
 */
fun ResolvedMark.lineDraw(part: PartLines?, rule: LineRule, color: Color, taper: Float, layer: DepthLayer): RibbonDraw? {
    if (!ink) return null
    val weights = part?.markWeights?.get(id)
    val at = points.mapIndexed { i, point -> RibbonPoint(point.position, point.normal, weights?.getOrNull(i) ?: 1f) }
    return StrokeRibbon.along(at, markProfile(width, taper * HALF))?.let { RibbonDraw(it, rule, color, layer = layer) }
}

private const val HALF = 0.5f
