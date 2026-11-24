package dev.chieppa.squash.character.mouth.shaped

import dev.chieppa.squash.geom.Vec3
import dev.chieppa.squash.geom.field.JawCut
import kotlin.math.atan2
import kotlin.math.sqrt

/** The fewest places a side of a lip line needs to make a path. */
private const val LEAST_SIDE_PLACES = 1

/**
 * A head's lip line as the shaped mouth parts read it, in the head's own space as it lies shut: from its middle in front, where it crosses
 * the head's middle plane, back along each side to that side's corner. Side 1 is the side the cut's axis points away from, the character's
 * left, and -1 the other. [width] is the whole line from corner to corner, which tooth sizes are shares of.
 */
class LipPath private constructor(val cut: JawCut, private val sides: Map<Int, List<Vec3>>, private val normals: Map<Int, List<Vec3>>) {
    private val lengths: Map<Int, FloatArray> = sides.mapValues { (_, points) ->
        val out = FloatArray(points.size)
        for (i in 1 until points.size) out[i] = out[i - 1] + (points[i] - points[i - 1]).length
        out
    }

    /** The line's length from corner to corner. */
    val width: Float = lengths.values.sumOf { it.last().toDouble() }.toFloat()

    /** How far along the side [sign] the line runs from its middle to its corner. */
    fun reach(sign: Int): Float = lengths[sign]?.last() ?: 0f

    /** The place [distance] along the side [sign] from the middle, and the line's way there, outward toward the corner. */
    fun at(sign: Int, distance: Float): Pair<Vec3, Vec3>? = placeAt(sign, distance)?.let { it.point to it.way }

    /** The place [distance] along the side [sign] from the middle: its point, the line's way there outward, and the head's outward normal. */
    fun placeAt(sign: Int, distance: Float): LipPlace? {
        val points = sides[sign] ?: return null
        val outs = normals.getValue(sign)
        val along = lengths.getValue(sign)
        if (points.size < 2) return null
        val d = distance.coerceIn(0f, along.last())
        var i = 0
        while (i < points.size - 2 && along[i + 1] < d) i++
        val span = along[i + 1] - along[i]
        val t = if (span > 0f) (d - along[i]) / span else 0f
        val way = (points[i + 1] - points[i]).let { if (it.length > 0f) it.normalized() else it }
        val out = (outs[i] + (outs[i + 1] - outs[i]) * t).let { if (it.length > 0f) it.normalized() else it }
        return LipPlace(points[i] + (points[i + 1] - points[i]) * t, way, out)
    }

    /** How far from the middle plane the line runs at distance [r] from the hinge, the mean of both sides where both reach it. */
    fun halfWidthAt(r: Float): Float {
        val widths = sides.values.mapNotNull { points ->
            val byR = points.map { radius(it) to kotlin.math.abs((it - cut.hinge) dot cut.axis) }.sortedBy { it.first }
            if (byR.isEmpty()) return@mapNotNull null
            if (r <= byR.first().first) return@mapNotNull byR.first().second
            if (r >= byR.last().first) return@mapNotNull byR.last().second
            val k = byR.indexOfFirst { it.first >= r }
            val (r0, w0) = byR[k - 1]
            val (r1, w1) = byR[k]
            if (r1 - r0 <= 0f) w1 else w0 + (w1 - w0) * (r - r0) / (r1 - r0)
        }
        return if (widths.isEmpty()) 0f else widths.average().toFloat()
    }

    /** How far the line's middle in front lies from the hinge. */
    val frontRadius: Float get() = sides.values.first().first().let(::radius)

    /** The distance of [p] from the hinge, across the axis. */
    fun radius(p: Vec3): Float {
        val d = p - cut.hinge
        val x = d dot cut.forward
        val y = d dot cut.up
        return sqrt(x * x + y * y)
    }

    /** The angle of [p] about the hinge, from forward toward up. */
    fun angle(p: Vec3): Float {
        val d = p - cut.hinge
        return atan2(d dot cut.up, d dot cut.forward)
    }

    /** The way the angle about the hinge grows at [p], square to the axis. */
    fun turning(p: Vec3): Vec3 {
        val phi = angle(p)
        return cut.forward * -kotlin.math.sin(phi) + cut.up * kotlin.math.cos(phi)
    }

    companion object {
        /**
         * The path through [places], the lip line's places in the head's own space, about [cut], with the head's outward normal at each,
         * [normals], the way out from the hinge where none is given; null where a side has none.
         */
        fun of(cut: JawCut, places: List<Vec3>, normals: List<Vec3> = emptyList()): LipPath? {
            fun side(p: Vec3) = (p - cut.hinge) dot cut.axis
            val withNormals = places.mapIndexed { i, p -> p to (normals.getOrNull(i) ?: (p - cut.hinge).let { if (it.length > 0f) it.normalized() else it }) }
            val bySide = withNormals.groupBy { if (side(it.first) < 0f) 1 else -1 }
            if (bySide.size < 2 || bySide.values.any { it.size < LEAST_SIDE_PLACES }) return null
            fun radius(p: Vec3): Float {
                val d = p - cut.hinge
                val x = d dot cut.forward
                val y = d dot cut.up
                return sqrt(x * x + y * y)
            }
            val ordered = bySide.mapValues { (_, points) -> points.sortedByDescending { radius(it.first) } }
            val fronts = ordered.values.map { it.first() }
            val mid = (fronts[0].first + fronts[1].first) * 0.5f
            val middle = mid - cut.axis * side(mid)
            val midOut = (fronts[0].second + fronts[1].second).let { it - cut.axis * (it dot cut.axis) }.let { if (it.length > 0f) it.normalized() else cut.forward }
            return LipPath(
                cut,
                ordered.mapValues { (_, points) -> listOf(middle) + points.map { it.first } },
                ordered.mapValues { (_, points) -> listOf(midOut) + points.map { it.second } },
            )
        }
    }
}

/** A place on a lip line: its [point], the line's [way] there outward toward the corner, and the head's outward normal there, [out]. */
class LipPlace(val point: Vec3, val way: Vec3, val out: Vec3)
