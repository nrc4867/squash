package dev.chieppa.squash.geom

import kotlin.math.abs

private const val PARALLEL_COSINE = 0.999f

/** A straight guide in a view's plane, through [from] and [to]. */
data class SnapGuide(val from: Vec2, val to: Vec2)

private class GuideFamily(val normal: Vec2) {
    val offsets = mutableListOf<Float>()
    val guides = mutableListOf<Int>()
}

/** Where a coordinate along one family's normal goes: straight between knots, each a stroke's extreme or end, on a guide or left where it was. */
internal class FamilyWarp(private val normal: Vec2, private val from: FloatArray, private val to: FloatArray) {
    fun moved(p: Vec2): Vec2 {
        val at = p dotWith normal
        return p + normal * (warped(at) - at)
    }

    private fun warped(at: Float): Float {
        if (at <= from.first()) return at + to.first() - from.first()
        if (at >= from.last()) return at + to.last() - from.last()
        val i = (1 until from.size).first { at <= from[it] }
        val t = (at - from[i - 1]) / (from[i] - from[i - 1])
        return to[i - 1] + (to[i] - to[i - 1]) * t
    }
}

/** What guides do to a stroke: [moved] takes a point of it where it lands, and [caught] names the guides it is holding to. */
class GuideSnap internal constructor(private val warps: List<FamilyWarp>, val caught: Set<Int>) {
    fun moved(p: Vec2): Vec2 = warps.fold(p) { at, warp -> warp.moved(at) }
}

/**
 * How [guides] catch a stroke through [samples]: its ends and its widest points square to each set of parallel guides,
 * within [reach] of one. The stroke's coordinate across those guides stretches between what is caught and what is not, so
 * the stroke moves with no break and never by more than [reach]. A function of the samples alone, so a preview and a
 * release of the same samples agree. Given [only], that one point of the samples alone is caught, and the rest of the stroke
 * stretches to follow it.
 */
fun guideSnap(samples: List<Vec2>, guides: List<SnapGuide>, reach: Float, only: Vec2? = null): GuideSnap {
    if (samples.isEmpty()) return GuideSnap(emptyList(), emptySet())
    val caught = mutableSetOf<Int>()
    val warps = families(guides).mapNotNull { family -> familyWarp(samples, family, reach, only, caught) }
    return GuideSnap(warps, caught)
}

private fun families(guides: List<SnapGuide>): List<GuideFamily> {
    val families = mutableListOf<GuideFamily>()
    guides.forEachIndexed { index, guide ->
        val along = (guide.to - guide.from).unit()
        if (along == Vec2(0f, 0f)) return@forEachIndexed
        val normal = Vec2(0f - along.y, along.x)
        val family = families.firstOrNull { abs(it.normal dotWith normal) > PARALLEL_COSINE } ?: GuideFamily(normal).also(families::add)
        family.offsets += guide.from dotWith family.normal
        family.guides += index
    }
    return families
}

private fun familyWarp(samples: List<Vec2>, family: GuideFamily, reach: Float, only: Vec2?, caught: MutableSet<Int>): FamilyWarp? {
    val normal = family.normal
    var low = Float.POSITIVE_INFINITY
    var high = Float.NEGATIVE_INFINITY
    for (point in samples) {
        val at = point dotWith normal
        if (at < low) low = at
        if (at > high) high = at
    }
    val sources = listOf(low, high, samples.first() dotWith normal, samples.last() dotWith normal).distinct().sorted()
    val catchable = only?.let { it dotWith normal }
    var any = false
    val targets = FloatArray(sources.size) { i ->
        val source = sources[i]
        val nearest = family.offsets.indices.minBy { abs(family.offsets[it] - source) }
        if ((catchable == null || source == catchable) && abs(family.offsets[nearest] - source) <= reach) {
            caught += family.guides[nearest]
            any = true
            family.offsets[nearest]
        } else {
            source
        }
    }
    if (!any) return null
    for (i in 1 until targets.size) if (targets[i] < targets[i - 1]) targets[i] = targets[i - 1]
    return FamilyWarp(normal, sources.toFloatArray(), targets)
}
