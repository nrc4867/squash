package dev.chieppa.squash.character

import dev.chieppa.squash.character.build.toPath
import dev.chieppa.squash.document.OutlineCurve
import dev.chieppa.squash.geom.PartAxes
import dev.chieppa.squash.geom.Patch
import dev.chieppa.squash.geom.Vec2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** A straight piece of a part's Side view, in (a, b). */
data class SideEdge(val from: Vec2, val to: Vec2) {
    /** The lowest and highest b where this edge crosses the line a = [a], or null if it does not. */
    fun crossing(a: Float): ClosedFloatingPointRange<Float>? {
        if (a < min(from.x, to.x) || a > max(from.x, to.x)) return null
        if (from.x == to.x) return min(from.y, to.y)..max(from.y, to.y)
        val b = from.y + (to.y - from.y) * (a - from.x) / (to.x - from.x)
        return b..b
    }

    fun distanceTo(p: Vec2): Float {
        val along = to - from
        val square = along.x * along.x + along.y * along.y
        val t = if (square == 0f) 0f else (((p.x - from.x) * along.x + (p.y - from.y) * along.y) / square).coerceIn(0f, 1f)
        val dx = from.x + along.x * t - p.x
        val dy = from.y + along.y * t - p.y
        return sqrt(dx * dx + dy * dy)
    }
}

/** The lowest and highest b where these edges cross a = [a], or null if none does. */
fun List<SideEdge>.crossings(a: Float): ClosedFloatingPointRange<Float>? {
    val hits = mapNotNull { it.crossing(a) }
    return if (hits.isEmpty()) null else hits.minOf { it.start }..hits.maxOf { it.endInclusive }
}

fun List<SideEdge>.distanceTo(p: Vec2): Float = minOf { it.distanceTo(p) }

/** The edges of [outline] in Side, closed by the chord between its ends as the slice build reads it. */
fun sideEdges(outline: OutlineCurve): List<SideEdge> {
    val flat = outline.toPath().flatten()
    return flat.xs.indices.map { i ->
        val next = (i + 1) % flat.xs.size
        SideEdge(Vec2(flat.xs[i], flat.ys[i]), Vec2(flat.xs[next], flat.ys[next]))
    }
}

/** Every grid edge of [patch] seen in Side. */
fun sideEdges(patch: Patch, axes: PartAxes): List<SideEdge> {
    fun at(ring: Int, segment: Int): Vec2 {
        val p = patch.point(patch.index(ring, segment))
        return Vec2(p dot axes.along, p dot axes.up)
    }
    return buildList {
        for (ring in 0 until patch.nu) {
            for (segment in 0 until patch.nv) {
                add(SideEdge(at(ring, segment), at(ring, (segment + 1) % patch.nv)))
                if (ring + 1 < patch.nu) add(SideEdge(at(ring, segment), at(ring + 1, segment)))
            }
        }
    }
}
