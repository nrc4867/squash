package dev.chieppa.squash.screens.surface

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.character.build.PartLines
import dev.chieppa.squash.character.build.ResolvedMark
import dev.chieppa.squash.character.build.ResolvedLineWeight
import dev.chieppa.squash.document.MarkWidth
import dev.chieppa.squash.character.build.WeightedRun
import dev.chieppa.squash.geom.ContourPoint
import dev.chieppa.squash.geom.ContourRun
import dev.chieppa.squash.geom.TriangleLines
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

/**
 * One ribbon along where [parts], and the one-surface meshes [surfaces] a character has, turn edge on through [view], weighed by what lies on
 * their surfaces, the one surfaces' by [weightAt] in space times each one's share in [scales] (1 where it has none), and along the open
 * [edges] that outline a surface, or null with none on show.
 */
fun contourRibbon(
    parts: Collection<PartLines>,
    view: SurfaceView,
    surfaces: List<TriangleLines> = emptyList(),
    weightAt: ((Vec3) -> Float)? = null,
    edges: List<ContourRun> = emptyList(),
    scales: List<Float> = emptyList(),
): StrokeRibbon? = figureRibbons(parts, view, surfaces, weightAt, edges, scales, folds = false).contour

/** A figure's contour as one ribbon, and the folds its surfaces' smooth contours miss ([TriangleLines.foldContours]) as another. */
class FigureRibbons(val contour: StrokeRibbon?, val folds: StrokeRibbon?)

/**
 * [contourRibbon], with, where [folds], the folds of [surfaces] the smooth contours miss, at full weight however short and weighed as
 * their contours are, each surface's kept to where [shown] gives it drawn (its own kept values where it gives none): a fold makes the
 * edge of the fill wherever it shows, so it is drawn only clear of the figure ([contourDraws]).
 */
fun figureRibbons(
    parts: Collection<PartLines>,
    view: SurfaceView,
    surfaces: List<TriangleLines> = emptyList(),
    weightAt: ((Vec3) -> Float)? = null,
    edges: List<ContourRun> = emptyList(),
    scales: List<Float> = emptyList(),
    folds: Boolean = true,
    shown: List<FloatArray?> = emptyList(),
): FigureRibbons {
    val ribbons = parts.flatMap { part -> part.grids.flatMap { grid -> gridRibbons(grid.contours(view::toViewer), view) } }
    val own = ArrayList<StrokeRibbon>()
    val folded = ArrayList<StrokeRibbon>()
    for ((i, lines) in surfaces.withIndex()) {
        val runs = lines.smoothContours(view::toViewer)
        val scale = scales.getOrElse(i) { 1f }
        own += gridRibbons(weighed(runs, weightAt, scale), view)
        if (folds) {
            val drawn = shown.getOrNull(i)
            val runsOfFolds = if (drawn == null) lines.foldContours(view::toViewer, runs) else lines.foldContours(view::toViewer, runs, drawn)
            folded += gridRibbons(weighed(runsOfFolds, weightAt, scale), view, fade = false)
        }
    }
    return FigureRibbons(StrokeRibbon.joined(ribbons + own + gridRibbons(edges, view)), StrokeRibbon.joined(folded))
}

/** [runs] weighed by [weightAt] in space times [scale]. */
private fun weighed(runs: List<ContourRun>, weightAt: ((Vec3) -> Float)?, scale: Float): List<ContourRun> =
    if (weightAt == null && scale == 1f) runs else runs.map { run -> ContourRun(run.points.map { ContourPoint(it.position, (weightAt?.invoke(it.position) ?: it.weight) * scale) }, run.closed, run.piece) }

/** Each of [runs] as a ribbon, with [fade] thinned by the length on screen of the connected piece it belongs to, however its edges branch. */
private fun gridRibbons(runs: List<ContourRun>, view: SurfaceView, fade: Boolean = true): List<StrokeRibbon> {
    val lengths = runs.groupBy { it.piece }.mapValues { (_, piece) -> piece.sumOf { pixelsAlong(it, view).toDouble() }.toFloat() }
    return runs.mapNotNull { run ->
        val share = if (fade) (lengths.getValue(run.piece) / FULL_RUN_PIXELS).coerceAtMost(1f) else 1f
        if (share <= 0f) null else StrokeRibbon.along(spacedOnScreen(run, view).map { RibbonPoint(it.position, Vec3.ZERO, it.weight * share) }, EVEN_WEIGHT, run.closed)
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
 * the edge, and the thick one shows only outside it, so together they read as the silhouette's weight across. With [tuck], the thin line
 * lies that far behind the edge instead of over it, so it shows only over what lies further back than that. [folds] draw thick
 * only, where they clear the figure: where a fold makes the edge of the fill, the silhouette's line runs on along it.
 */
fun contourDraws(contour: StrokeRibbon, rule: LineRule, color: Color, layers: LineLayers, figureRadius: Float, tuck: Float? = null, folds: StrokeRibbon? = null): List<RibbonDraw> {
    val outer = max(0f, 2f * rule.silhouettePixels - rule.interiorPixels)
    return listOfNotNull(
        RibbonDraw(contour, LineRule(outer), color, layer = layers.behind, lift = 0f),
        folds?.let { RibbonDraw(it, LineRule(outer), color, layer = layers.behind, lift = 0f) },
        RibbonDraw(contour, LineRule(rule.interiorPixels), color, layer = layers.over, lift = tuck?.let { -it } ?: (figureRadius * CONTOUR_LIFT_SHARE)),
    )
}

/** The ink where [part] leaves its parent, and its strands as hair, at the weight on the surface there. */
fun creaseDraws(part: PartLines, rule: LineRule, color: Color, layer: DepthLayer): List<RibbonDraw> = (part.crease + part.strands).mapNotNull { run ->
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

/** This line weight's path, picked in the Lines inspector, drawn over the line it weighs in [color]. */
fun ResolvedLineWeight.litDraw(rule: LineRule, color: Color): RibbonDraw? =
    StrokeRibbon.along(points.map { RibbonPoint(it.position, it.normal) }, markProfile(MarkWidth()))?.let { RibbonDraw(it, rule, color, layer = DepthLayer.FRONT) }
