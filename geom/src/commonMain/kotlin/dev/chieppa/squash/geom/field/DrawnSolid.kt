package dev.chieppa.squash.geom.field

import dev.chieppa.squash.geom.OutlineProfile
import dev.chieppa.squash.geom.Vec2
import dev.chieppa.squash.geom.Vec3
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Directions round the section at which its reach from its middle is kept. */
private const val SECTION_ANGLES = 256

/** The least half extent a section keeps, as a share of the solid's length, so its ends close round rather than to nothing. */
private const val LEAST_HALF_EXTENT = 1e-3f

private const val TWO_PI = (2.0 * PI).toFloat()

/**
 * The solid a part's drawn outlines make, as a distance-like field in the part's own axes (x along, y up, z side): at each point
 * along, a section sized to the drawing there, its height between Side's two crossings and its width between Top's, so Side and Top
 * are its silhouettes exactly. The section is [section] all along, or with [sectionsAlong] the one it gives at each point along (null
 * where it has none, which takes the nearest it gives), so a solid's own sections, an ankle standing narrow over a broad heel, keep
 * their shapes at the drawing's sizes. A view not drawn takes the other's extent in the section's own proportion, about the axis.
 * [height] is Side (along, up) and [width] Top (along, less side), both times [scale]; each section is a closed outline across, x toward
 * the side and y up, read star-shaped from the middle of its box, its corners rounded a little. Negative inside.
 */
class DrawnSolid(
    private val height: OutlineProfile?,
    private val width: OutlineProfile?,
    private val scale: Float,
    section: List<Vec2>,
    sectionsAlong: ((Float) -> List<Vec2>?)? = null,
) : Primitive {
    private val ruler: OutlineProfile = requireNotNull(height ?: width) { "A drawn solid needs Side or Top" }
    private val start = ruler.start * scale
    private val end = ruler.end * scale
    private val least = (end - start) * LEAST_HALF_EXTENT

    /** Where along the solid each of [reaches] is kept. */
    private val stationXs: FloatArray

    /** At each station, the section's reach from its middle at each of [SECTION_ANGLES] directions, in its box taken as -1 to 1 each way. */
    private val reaches: List<FloatArray>

    /** At each station, the section's height over its width. */
    private val aspects: FloatArray

    init {
        require(end > start) { "A drawn solid needs a length" }
        require(section.size >= 3) { "A section needs three points" }
        val fallback = shapeOf(section)
        if (sectionsAlong == null) {
            stationXs = floatArrayOf(start)
            reaches = listOf(fallback.first)
            aspects = floatArrayOf(fallback.second)
        } else {
            val xs = FloatArray(SECTION_STATIONS) { start + (end - start) * (it + 0.5f) / SECTION_STATIONS }
            val shapes = xs.map { x -> sectionsAlong(x)?.takeIf { it.size >= 3 }?.let(::shapeOf) }
            val found = shapes.indices.filter { shapes[it] != null }
            val filled = shapes.indices.map { i -> found.minByOrNull { kotlin.math.abs(it - i) }?.let { shapes[it] } ?: fallback }
            stationXs = xs
            reaches = filled.map { it.first }
            aspects = FloatArray(filled.size) { filled[it].second }
        }
    }

    override val low: Vec3
    override val high: Vec3

    init {
        val xs = List(STATIONS) { start + (end - start) * it / (STATIONS - 1) }
        val boxes = xs.map { box(it) }
        low = Vec3(start, boxes.minOf { it.cy - it.hy }, boxes.minOf { it.cz - it.hz })
        high = Vec3(end, boxes.maxOf { it.cy + it.hy }, boxes.maxOf { it.cz + it.hz })
    }

    /** The section's middle and half extents at [x] along: up from Side, across from Top. */
    private class Box(val cy: Float, val hy: Float, val cz: Float, val hz: Float)

    private fun box(x: Float): Box {
        val aspect = lerpAt(x) { aspects[it] }
        val a = x / scale
        val sideHalf = height?.let { max(it.extentAt(a) * scale / 2f, least) }
        val topHalf = width?.let { max(it.extentAt(a) * scale / 2f, least) }
        val hy = sideHalf ?: (topHalf!! * aspect)
        val hz = topHalf ?: (sideHalf!! / aspect)
        val cy = height?.midAt(a)?.times(scale) ?: 0f
        val cz = width?.let { -it.midAt(a) * scale } ?: 0f
        return Box(cy, hy, cz, hz)
    }

    override fun distance(p: Vec3): Float {
        val x = p.x.coerceIn(start, end)
        val b = box(x)
        val qz = (p.z - b.cz) / b.hz
        val qy = (p.y - b.cy) / b.hy
        val rho = sqrt(qz * qz + qy * qy)
        val across = if (rho < 1e-6f) {
            -min(b.hy, b.hz) * reachAt(x, 0f)
        } else {
            val r = reachAt(x, atan2(qy, qz))
            val rim = sqrt((r * qz / rho * b.hz).let { it * it } + (r * qy / rho * b.hy).let { it * it })
            (rho / r - 1f) * rim
        }
        val beyond = max(start - p.x, p.x - end)
        if (beyond <= 0f) return across
        return if (across <= 0f) beyond else sqrt(beyond * beyond + across * across)
    }

    private fun reachAt(x: Float, angle: Float): Float {
        val t = (angle / TWO_PI).let { it - kotlin.math.floor(it) } * SECTION_ANGLES
        val i = t.toInt() % SECTION_ANGLES
        val f = t - t.toInt()
        return lerpAt(x) { station -> reaches[station].let { it[i] + (it[(i + 1) % SECTION_ANGLES] - it[i]) * f } }
    }

    /** [value] of the stations either side of [x], eased between them, held at the first and last. */
    private inline fun lerpAt(x: Float, value: (Int) -> Float): Float {
        if (stationXs.size == 1 || x <= stationXs[0]) return value(0)
        val last = stationXs.lastIndex
        if (x >= stationXs[last]) return value(last)
        val step = stationXs[1] - stationXs[0]
        val i = ((x - stationXs[0]) / step).toInt().coerceIn(0, last - 1)
        val f = ((x - stationXs[i]) / step).coerceIn(0f, 1f)
        return value(i) + (value(i + 1) - value(i)) * f
    }

    private companion object {
        /** Stations along the solid its box is read at. */
        const val STATIONS = 65

        /** Stations along the solid a section is read at when the sections change along it. */
        const val SECTION_STATIONS = 33

        /** [section]'s reach round its box's middle, rounded, and its height over its width. */
        fun shapeOf(section: List<Vec2>): Pair<FloatArray, Float> {
            val minX = section.minOf { it.x }
            val maxX = section.maxOf { it.x }
            val minY = section.minOf { it.y }
            val maxY = section.maxOf { it.y }
            val cx = (minX + maxX) / 2f
            val cy = (minY + maxY) / 2f
            val hx = max((maxX - minX) / 2f, 1e-6f)
            val hy = max((maxY - minY) / 2f, 1e-6f)
            val unit = section.map { Vec2((it.x - cx) / hx, (it.y - cy) / hy) }
            return rounded(FloatArray(SECTION_ANGLES) { k -> rayReach(unit, TWO_PI * k / SECTION_ANGLES) }) to hy / hx
        }

        /** [reach] eased round by [ROUNDING] passes of a 1-2-1 average, so a corner in a drawn section rounds over rather than meshing ragged. */
        fun rounded(reach: FloatArray): FloatArray {
            var out = reach
            repeat(ROUNDING) {
                val before = out
                out = FloatArray(before.size) { k -> (before[(k + before.size - 1) % before.size] + 2f * before[k] + before[(k + 1) % before.size]) / 4f }
            }
            return out
        }

        /** Passes of the average that rounds a section's corners: about a twentieth of a turn either way. */
        const val ROUNDING = 24

        /** How far from the middle a ray at [angle] (0 toward the side, a quarter turn up) leaves [outline], at its furthest crossing. */
        fun rayReach(outline: List<Vec2>, angle: Float): Float {
            val dx = cos(angle)
            val dy = sin(angle)
            var best = 0f
            for (i in outline.indices) {
                val a = outline[i]
                val b = outline[(i + 1) % outline.size]
                val ex = b.x - a.x
                val ey = b.y - a.y
                val denominator = dx * ey - dy * ex
                if (kotlin.math.abs(denominator) < 1e-9f) continue
                val t = (a.x * ey - a.y * ex) / denominator
                val s = (a.x * dy - a.y * dx) / denominator
                if (t > best && s >= 0f && s <= 1f) best = t
            }
            return max(best, 1e-3f)
        }
    }
}
