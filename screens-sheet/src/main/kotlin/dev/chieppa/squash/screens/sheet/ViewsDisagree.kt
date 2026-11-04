package dev.chieppa.squash.screens.sheet

import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.document.View

/** A direction of a part's own axes, which two locked views share. */
enum class PartAxis { ALONG, UP, SIDE }

/**
 * A place where [view]'s outline reaches past what [against] reaches along [axis]: [limit] is that reach, as a coordinate
 * along [axis], and each of [spans] is a stretch of [view]'s outline beyond it, in [view]'s plane.
 */
data class Disagreement(val view: View, val against: View, val axis: PartAxis, val limit: Float, val spans: List<List<CurvePoint>>)

private class Rule(val view: View, val against: View, val axis: PartAxis)

/** Side and Top each hold the other to their ends along the axis; Front stays inside the height and width they set. */
private val RULES = listOf(
    Rule(View.SIDE, View.TOP, PartAxis.ALONG),
    Rule(View.TOP, View.SIDE, PartAxis.ALONG),
    Rule(View.FRONT, View.SIDE, PartAxis.UP),
    Rule(View.FRONT, View.TOP, PartAxis.SIDE),
)

/** Where [point], in [view]'s plane, lies along [axis], which the rules only ask of a view that shows it. */
private fun coordinate(view: View, axis: PartAxis, point: CurvePoint): Float = when (view) {
    View.SIDE -> if (axis == PartAxis.ALONG) point.x else point.y
    View.TOP -> if (axis == PartAxis.ALONG) point.x else -point.y
    View.FRONT -> if (axis == PartAxis.SIDE) point.x else point.y
}

/**
 * Every place the drawn views disagree, by more than [tolerance]. A view not drawn asks nothing of the others. A [live]
 * stroke stands in for its view's outline and is measured against the others, but holds them to nothing while it is
 * still growing.
 */
fun viewsDisagree(outlines: Map<View, OutlineCurve>, live: LiveStroke? = null, tolerance: Float = 0f): List<Disagreement> {
    val lines = outlines.mapValues { (_, curve) -> outlineSamples(curve) }
    val found = mutableListOf<Disagreement>()
    for (rule in RULES) {
        if (rule.against == live?.view) continue
        val limits = lines[rule.against] ?: continue
        val line = (if (rule.view == live?.view) live.points else lines[rule.view]) ?: continue
        if (line.size < 2) continue
        var low = Float.POSITIVE_INFINITY
        var high = Float.NEGATIVE_INFINITY
        for (point in limits) {
            val at = coordinate(rule.against, rule.axis, point)
            if (at < low) low = at
            if (at > high) high = at
        }
        beyond(rule, line, low, -1f, tolerance)?.let(found::add)
        beyond(rule, line, high, 1f, tolerance)?.let(found::add)
    }
    return found
}

/** The stretches of [line] on the [sign] side of [limit], if any goes more than [tolerance] past it. */
private fun beyond(rule: Rule, line: List<CurvePoint>, limit: Float, sign: Float, tolerance: Float): Disagreement? {
    fun past(point: CurvePoint) = (coordinate(rule.view, rule.axis, point) - limit) * sign
    if (line.none { past(it) > tolerance }) return null
    val spans = mutableListOf<List<CurvePoint>>()
    var open: MutableList<CurvePoint>? = null
    var previous: CurvePoint? = null
    for (point in line) {
        val outside = past(point) > 0f
        val before = previous
        val span = open
        when {
            outside && span != null -> span += point
            outside -> open = mutableListOf<CurvePoint>().apply {
                if (before != null) add(crossing(before, point, past(before), past(point)))
                add(point)
            }
            span != null -> {
                span += crossing(checkNotNull(before), point, past(before), past(point))
                spans += span
                open = null
            }
        }
        previous = point
    }
    open?.let(spans::add)
    return Disagreement(rule.view, rule.against, rule.axis, limit, spans)
}

private fun crossing(a: CurvePoint, b: CurvePoint, pastA: Float, pastB: Float): CurvePoint {
    val t = pastA / (pastA - pastB)
    return CurvePoint(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
}
