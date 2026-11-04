package dev.chieppa.squash.character.build

import dev.chieppa.squash.character.model.AreaSpec
import dev.chieppa.squash.document.Anchor
import dev.chieppa.squash.document.BODY_PATCH
import dev.chieppa.squash.document.SlotName
import dev.chieppa.squash.geom.Vec3
import kotlin.math.abs
import kotlin.math.round

private const val HALF_WAY_ROUND = 0.5f

/**
 * The slot showing at [root] among what [work] holds, in the order color layers overdraw: its latest brush stroke there, then its
 * latest region, then a filled area, then its base. A soft stroke counts out to its core. A root on a part whose color arrives from
 * another part's region reads its own part's layers alone.
 */
internal fun slotUnder(root: Anchor, position: Vec3, work: ResolvedSurfaceWork, areas: List<AreaSpec>, closedV: Boolean): SlotName? {
    work.strokes.lastOrNull { stroke -> stroke.reaches(position) }?.let { return it.slot }
    work.regions.lastOrNull { region -> region.holds(root, closedV) }?.let { return it.slot }
    val area = areas.lastOrNull { area -> root.patch == BODY_PATCH && work.areaFills[area.name] != null && root.u in area.u.start..area.u.end && root.v in area.v.start..area.v.end }
    return area?.let { work.areaFills[it.name] } ?: work.baseSlot
}

private fun ResolvedBrushStroke.reaches(position: Vec3): Boolean {
    val core = radius * (1f - softness)
    if (points.size == 1) return (points.single().position - position).length <= core
    return points.zipWithNext().any { (a, b) -> distanceToSegment(position, a.position, b.position) <= core }
}

private fun distanceToSegment(point: Vec3, a: Vec3, b: Vec3): Float {
    val along = b - a
    val span = along dot along
    val share = if (span <= 0f) 0f else (((point - a) dot along) / span).coerceIn(0f, 1f)
    return (a + along * share - point).length
}

/** Whether [root] lies inside this region's border on its patch. A border going once round a closed patch holds the side toward the last ring. */
private fun ResolvedRegion.holds(root: Anchor, closedV: Boolean): Boolean {
    val own = border.map { it.anchor }.filter { it.part == root.part && it.patch == root.patch }
    if (own.size < border.size || own.size < 3) return false
    if (!closedV) return inside(root.u, root.v, own.map { it.u }, own.map { it.v })
    val around = unwrapped(own.map { it.v })
    val first = around.first()
    val turned = around.last() + wrappedStep(own.first().v - own.last().v) - first
    if (abs(turned) > HALF_WAY_ROUND) return root.u >= ringUAt(root.v, own)
    val nearest = first + wrappedStep(root.v - own.first().v)
    return inside(root.u, nearest, own.map { it.u }, around)
}

private fun wrappedStep(step: Float): Float = step - round(step)

/** Each of [vs] carried on from the one before by the short way round. */
private fun unwrapped(vs: List<Float>): List<Float> {
    val out = FloatArray(vs.size)
    out[0] = vs[0]
    for (i in 1 until vs.size) out[i] = out[i - 1] + wrappedStep(vs[i] - vs[i - 1])
    return out.toList()
}

/** The u of the border point nearest [v] around the patch. */
private fun ringUAt(v: Float, border: List<Anchor>): Float = border.minBy { abs(wrappedStep(it.v - v)) }.u

private fun inside(u: Float, v: Float, us: List<Float>, vs: List<Float>): Boolean {
    var crossings = false
    var last = us.lastIndex
    for (i in us.indices) {
        if ((vs[i] > v) != (vs[last] > v) && u < us[i] + (us[last] - us[i]) * (v - vs[i]) / (vs[last] - vs[i])) crossings = !crossings
        last = i
    }
    return crossings
}
