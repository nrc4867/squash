package dev.chieppa.squash.screens.surface

import androidx.compose.ui.graphics.Color
import dev.chieppa.squash.character.surface.CharacterSurface
import dev.chieppa.squash.character.surface.CreaseLine
import dev.chieppa.squash.character.surface.PosedCreases
import dev.chieppa.squash.document.GarmentId
import dev.chieppa.squash.character.surface.SurfaceDress
import dev.chieppa.squash.geom.ContourPoint
import dev.chieppa.squash.geom.ContourRun
import dev.chieppa.squash.geom.CurvePoint
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.render.DepthLayer
import dev.chieppa.squash.render.LineRule
import dev.chieppa.squash.render.RibbonDraw
import dev.chieppa.squash.render.RibbonPoint
import dev.chieppa.squash.render.StrokeRibbon
import dev.chieppa.squash.screens.sheet.SurfaceView

/**
 * How far toward the viewer a join's surface must face, as the cosine of its normal with the view, for its line to be drawn at all, and
 * to take its full weight.
 */
private const val JOIN_LEAST_FACING = 0.15f
private const val JOIN_FULL_FACING = 0.35f

/** How many of the surface's cells a piece of join line runs at least to be drawn. */
private const val JOIN_SHORTEST_CELLS = 6f

/** How many of the surface's cells a piece of hem runs at least to be drawn: short enough for a cuff round a thin wrist or ankle. */
private const val HEM_SHORTEST_CELLS = 2f

/** The ink along each hem, neck opening and cuff of [dress]'s garments whose hem ink is on, where it faces [view], at each one's ink weight. */
fun hemDraws(dress: SurfaceDress, view: SurfaceView, rule: LineRule, color: Color, cell: Float): List<RibbonDraw> =
    dress.garments.filter { it.cover.garment.inkHem }.flatMap { facedCurveDraws(it.hems, view, rule, color, cell, scale = it.cover.garment.inkWeight, shortestCells = HEM_SHORTEST_CELLS) }

/**
 * The ink along each inked join of [surface] where its surface faces [view]: each curve is cut where the surface turns from the viewer
 * past [JOIN_LEAST_FACING],
 * a piece shorter than [JOIN_SHORTEST_CELLS] of the surface's cells is left out, and a piece thins toward where the surface turns.
 */
fun joinDraws(surface: CharacterSurface, view: SurfaceView, rule: LineRule, color: Color): List<RibbonDraw> =
    facedCurveDraws(surface.joins, view, rule, color, surface.mesh.cell)

/**
 * The hems of [dress]'s garments hanging free, as the cloth's outline at its ink weight: an open edge of cloth hanging free outlines it
 * from either side, and where the cloth turns, its own contour lies past the hem, cut away with what the garment does not cover.
 */
fun hangingHems(dress: SurfaceDress): List<ContourRun> = dress.garments.filter { it.cover.hanging }.flatMap { garment ->
    garment.hems.filter { it.size > 1 }.map { curve ->
        ContourRun(curve.map { ContourPoint(it.position, garment.cover.garment.inkWeight) }, curve.size > 2 && curve.first().position == curve.last().position)
    }
}

/** How many of the surface's cells a piece of crease or wrinkle ink runs at least to be drawn. */
private const val CREASE_SHORTEST_CELLS = 2.5f

/**
 * The ink of [creases]' lines where they face [view]: the body's at the crease ink's weight and each garment's at its own ink weight from
 * [inkWeights], each line at its own weight along it, so it tapers where its crease fades.
 */
fun creaseInkDraws(creases: PosedCreases, inkWeights: Map<GarmentId, Float>, view: SurfaceView, rule: LineRule, color: Color, cell: Float): List<RibbonDraw> =
    creases.body?.lines.orEmpty().let { weightedCurveDraws(it, view, rule, color, cell, 1f) } +
        creases.garments.flatMap { (piece, marks) -> weightedCurveDraws(marks.lines, view, rule, color, cell, inkWeights[piece.garment] ?: 1f) }

private fun weightedCurveDraws(lines: List<CreaseLine>, view: SurfaceView, rule: LineRule, color: Color, cell: Float, scale: Float): List<RibbonDraw> =
    lines.flatMap { line -> facedCurveDraws(listOf(line.points), view, rule, color, cell, scale = scale, shortestCells = CREASE_SHORTEST_CELLS, weights = line.weights) }

/**
 * [curves] as ink where they face [view], in [layer], at [scale] of the rule's weight, each point's times its own in [weights] where given,
 * leaving out a piece shorter than [shortestCells] of [cell]: see [joinDraws].
 */
internal fun facedCurveDraws(
    curves: List<List<CurvePoint>>,
    view: SurfaceView,
    rule: LineRule,
    color: Color,
    cell: Float,
    layer: DepthLayer = DepthLayer.SCENE,
    scale: Float = 1f,
    shortestCells: Float = JOIN_SHORTEST_CELLS,
    weights: FloatArray? = null,
): List<RibbonDraw> = curves.flatMap { curve ->
    val facing = curve.map { point -> point.normal dot view.toViewer(point.position).normalized() }
    val closed = curve.size > 2 && curve.first().position == curve.last().position
    val shortest = shortestCells * cell
    facedPieces(facing, closed).filter { piece -> length(piece.map { curve[it].position }) >= shortest }.mapNotNull { piece ->
        val whole = closed && piece.size == curve.size
        val points = piece.map { i ->
            val weight = ((facing[i] - JOIN_LEAST_FACING) / (JOIN_FULL_FACING - JOIN_LEAST_FACING)).coerceIn(0f, 1f)
            RibbonPoint(curve[i].position, curve[i].normal, weight * scale * (weights?.getOrNull(i) ?: 1f))
        }
        StrokeRibbon.along(points, EVEN_WEIGHT, whole)?.let { RibbonDraw(it, rule, color, layer = layer) }
    }
}

/** The runs of indices into a curve whose [facing] is above [JOIN_LEAST_FACING], joined across the start of a [closed] curve. */
private fun facedPieces(facing: List<Float>, closed: Boolean): List<List<Int>> {
    if (facing.all { it > JOIN_LEAST_FACING }) return listOf(facing.indices.toList())
    val pieces = ArrayList<MutableList<Int>>()
    var current: MutableList<Int>? = null
    for (i in facing.indices) {
        if (facing[i] > JOIN_LEAST_FACING) {
            current = current ?: mutableListOf<Int>().also { pieces += it }
            current += i
        } else {
            current = null
        }
    }
    if (closed && pieces.size > 1 && facing.first() > JOIN_LEAST_FACING && facing.last() > JOIN_LEAST_FACING) {
        val first = pieces.removeAt(0)
        pieces.last().addAll(first.drop(1))
    }
    return pieces
}

private fun length(points: List<Vec3>): Float = points.zipWithNext { a, b -> (b - a).length }.sum()
