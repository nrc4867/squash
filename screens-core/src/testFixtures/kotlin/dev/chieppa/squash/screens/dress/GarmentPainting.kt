package dev.chieppa.squash.screens.dress

import androidx.compose.ui.geometry.Offset
import dev.chieppa.squash.character.build.AssembledCharacter
import dev.chieppa.squash.document.CHART_COLUMNS
import dev.chieppa.squash.document.CHART_ROWS
import dev.chieppa.squash.character.shape.SurfaceMask
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.Character
import dev.chieppa.squash.document.PartId
import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.screens.sheet.SurfaceView

/** How many places a brush stroke along a part takes, start and end included. */
private const val STROKE_PLACES = 24

/** Twelve places round a part, in v, where a stroke down each with a brush near half the part's depth lays cloth all round. */
val ALL_ROUND = List(12) { it / 12f }

/** How big a brush stroked down [ALL_ROUND] is, as a share of the part's half depth. */
const val ROUND_BRUSH = 0.35f

/**
 * A brush stroke down [part] from [fromU] to [toU] at each of [ALL_ROUND], the brush [ROUND_BRUSH] of the part's half depth at its
 * deepest along the stroke: the cells it lays.
 */
fun roundStroke(character: Character, body: AssembledCharacter, part: PartId, fromU: Float, toU: Float): SurfaceMask {
    val size = listOf(fromU, toU, (fromU + toU) / 2f).maxOf { halfDepth(character, body, part, it) } * ROUND_BRUSH
    return brushStroke(character, body, brushPlaces(character, body, part, fromU, toU), size)
}

/**
 * The places a brush passes on [body] in strokes along [part] from [fromU] to [toU], one down each of [vs] round it, as drags down its
 * front, sides and back in the panes lay them: each place at rest on the part's first run.
 */
fun brushPlaces(character: Character, body: AssembledCharacter, part: PartId, fromU: Float, toU: Float, vs: List<Float> = ALL_ROUND): List<Vec3> {
    val patch = checkNotNull(bodyChart(character, body).part(part)) { "No ${part.value} on the chart" }.patch
    return vs.flatMap { v ->
        List(STROKE_PLACES) { i -> fromU + (toU - fromU) * i / (STROKE_PLACES - 1) }.mapNotNull { u -> placeOnBody(body, Anchor(part, patch, u, v)) }
    }
}

/** The cells a brush of [size] lays passing [places]. */
fun brushStroke(character: Character, body: AssembledCharacter, places: List<Vec3>, size: Float): SurfaceMask =
    chartPlaces(character, body).within(places, size)

/** How far round a part, in body units, its slice at [u] reaches from its middle: half the way from its front to its back. */
fun halfDepth(character: Character, body: AssembledCharacter, part: PartId, u: Float): Float {
    val patch = checkNotNull(bodyChart(character, body).part(part)).patch
    val front = checkNotNull(placeOnBody(body, Anchor(part, patch, u, 0f)))
    val back = checkNotNull(placeOnBody(body, Anchor(part, patch, u, 0.5f)))
    return (front - back).length / 2f
}

/**
 * A view straight down the z axis, as Side shows a character facing x: x to the right and y up at [scale] pixels a body unit, about
 * [origin]. A place nearer the viewer has a larger z.
 */
class SideSight(private val scale: Float = 400f, private val origin: Offset = Offset(500f, 500f)) : SurfaceView {
    override fun toPixel(point: Vec3): Offset = Offset(origin.x + point.x * scale, origin.y - point.y * scale)

    override fun toViewer(point: Vec3): Vec3 = Vec3(0f, 0f, 1f)

    override fun depthOf(point: Vec3): Float = -point.z

    override fun pixelsPerUnitAt(point: Vec3): Float = scale
}

/** The cells of [part] from [fromU] up to [toU] along it, the whole way round: a stroke laid exactly over that band. */
fun bandStroke(character: Character, body: AssembledCharacter, part: PartId, fromU: Float, toU: Float): SurfaceMask {
    val chart = bodyChart(character, body)
    val leader = checkNotNull(chart.part(part)).id
    return SurfaceMask.build(chart) { p, cell ->
        val u = (cell / CHART_COLUMNS + 0.5f) / CHART_ROWS
        p == leader && u >= fromU && u < toU
    }
}
