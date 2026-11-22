package dev.chieppa.squash.recipe.build

import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.View
import dev.chieppa.squash.recipe.model.RecipeRest
import dev.chieppa.squash.recipe.model.RecipeTube
import kotlin.math.abs
import kotlin.math.hypot

/** A cubic Bézier's handle reach, as a share of the span, that draws a quarter circle. */
private const val QUARTER_HANDLE = 0.5523f

/** The least share of its run along the part a curve's end must head out by for a cap to take its slope rather than run level. */
private const val LEAST_HEADING = 0.2f

/** The direction names a tube profile takes, each as the direction it faces. */
private val DIRECTIONS = mapOf(
    "forward" to "forward", "front" to "forward", "back" to "back", "up" to "up", "down" to "down",
    "left" to "left", "outer" to "left", "right" to "right", "inner" to "right",
)

private val OPPOSITE = mapOf("forward" to "back", "back" to "forward", "up" to "down", "down" to "up", "left" to "right", "right" to "left")

/** Which direction Side's +y and Top's +y face for each rest (frames.md). */
private val REST_VIEWS = mapOf(
    RecipeRest.FORWARD to ("up" to "right"),
    RecipeRest.BACK to ("up" to "left"),
    RecipeRest.UP to ("forward" to "left"),
    RecipeRest.DOWN to ("forward" to "right"),
    RecipeRest.OUT to ("up" to "forward"),
)

/** The direction a profile name faces, or null for a name a tube does not take. */
fun tubeDirection(name: String): String? = DIRECTIONS[name]

/** The directions Side's and Top's +y face on a part resting [rest]. */
fun tubeViews(rest: RecipeRest): Pair<String, String> = REST_VIEWS.getValue(rest)

/**
 * A tube's radius curves, by the direction each faces, built into its Side and Top outlines for a part resting [rest]: each view runs
 * out along its +y curve, round its tip to the axis at the tube's length and back along its -y curve mirrored, and a closed tube rounds
 * over its socket end as well. A cap leaves a curve's end along its slope and meets the axis square, a quarter ellipse where the curve
 * ends level. Top mirrors a direction it is not given; Side needs both of its own. [fail] reports a profile that cannot be built.
 */
fun tubeOutlines(tube: RecipeTube, rest: RecipeRest, profiles: Map<String, OutlineCurve>, fail: (String, String) -> Nothing): Map<View, OutlineCurve> {
    val byDirection = profiles.mapKeys { (name, _) -> DIRECTIONS[name] ?: fail("profiles.$name", "a tube profile faces forward, front, back, up, down, left, right, outer or inner, not \"$name\"") }
    if (byDirection.size < profiles.size) fail("profiles", "two profiles face the same way: ${profiles.keys.joinToString()}")
    val (side, top) = tubeViews(rest)
    return mapOf(View.SIDE to view(tube, side, byDirection, false, fail), View.TOP to view(tube, top, byDirection, true, fail))
}

private fun view(tube: RecipeTube, plusDirection: String, curves: Map<String, OutlineCurve>, mirrorMissing: Boolean, fail: (String, String) -> Nothing): OutlineCurve {
    val minusDirection = OPPOSITE.getValue(plusDirection)
    var plus = curves[plusDirection]
    var minus = curves[minusDirection]
    if (mirrorMissing) {
        plus = plus ?: minus
        minus = minus ?: plus
    }
    if (plus == null || minus == null) {
        val missing = listOfNotNull(plusDirection.takeIf { plus == null }, minusDirection.takeIf { minus == null }).joinToString(" and ")
        fail("profiles", "this tube's ${if (mirrorMissing) "Top" else "Side"} needs $missing radii")
    }
    val out = plus.points
    val back = minus.points.map { CurvePoint(it.x, -it.y) }
    val tip = CurvePoint(tube.length, 0f)
    val points = mutableListOf<CurvePoint>()
    if (tube.closed) {
        val axisStart = CurvePoint(0f, 0f)
        points += axisStart
        points += cap(axisStart, out.first(), reversedHeading(out), toAxis = false)
    }
    points += if (points.isEmpty()) out else out.drop(1)
    if (tube.flat) {
        points += line(out.last(), tip) + line(tip, back.last())
    } else {
        points += cap(out.last(), tip, heading(out), toAxis = true)
        val (backHandle, tipHandle) = cap(back.last(), tip, heading(back), toAxis = true)
        points += listOf(tipHandle, backHandle, back.last())
    }
    points += back.reversed().drop(1)
    if (tube.closed) points += cap(back.first(), CurvePoint(0f, 0f), reversedHeading(back), toAxis = true)
    return OutlineCurve(points)
}

/** The direction a curve leaves its last point in. */
private fun heading(points: List<CurvePoint>): CurvePoint {
    val end = points.last()
    val handle = points.asReversed().firstOrNull { abs(it.x - end.x) + abs(it.y - end.y) > 0f } ?: return CurvePoint(1f, 0f)
    return unit(end.x - handle.x, end.y - handle.y)
}

/** The direction a curve leaves its first point in, backward along it. */
private fun reversedHeading(points: List<CurvePoint>): CurvePoint = heading(points.reversed())

private fun unit(x: Float, y: Float): CurvePoint {
    val length = hypot(x, y)
    return if (length == 0f) CurvePoint(1f, 0f) else CurvePoint(x / length, y / length)
}

/**
 * The cap from [from] to [to], one on the axis, leaving [from] along [heading] where it heads along the part and meeting the axis square:
 * its two handles and its end. With [toAxis] false it runs from the axis at [from] out to [to] instead, square at the axis and arriving
 * along the reverse of [heading].
 */
private fun cap(from: CurvePoint, to: CurvePoint, heading: CurvePoint, toAxis: Boolean): List<CurvePoint> {
    val onAxis = if (toAxis) to else from
    val off = if (toAxis) from else to
    val run = onAxis.x - off.x
    val rise = off.y
    val along = if (abs(heading.x) >= LEAST_HEADING && heading.x * run > 0f) heading else CurvePoint(if (run >= 0f) 1f else -1f, 0f)
    val reach = abs(run) * QUARTER_HANDLE / abs(along.x)
    val offHandle = CurvePoint(off.x + along.x * reach, off.y + along.y * reach)
    val axisHandle = CurvePoint(onAxis.x, rise * QUARTER_HANDLE)
    return if (toAxis) listOf(offHandle, axisHandle, onAxis) else listOf(axisHandle, offHandle, off)
}

private fun line(from: CurvePoint, to: CurvePoint): List<CurvePoint> = listOf(
    CurvePoint(from.x + (to.x - from.x) / 3f, from.y + (to.y - from.y) / 3f),
    CurvePoint(from.x + (to.x - from.x) * 2f / 3f, from.y + (to.y - from.y) * 2f / 3f),
    to,
)
