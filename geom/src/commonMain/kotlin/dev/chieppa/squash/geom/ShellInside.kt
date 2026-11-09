package dev.chieppa.squash.geom

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** A ring whose polygon encloses less than this area counts as a pole and holds nothing. */
private const val POLE_AREA = 1e-10f

/**
 * Whether a point lies inside a shell lofted as rings: between two of its rings along their axis, and inside the polygon of the nearer
 * ring. Each ring is read from [patch]'s grid as it stands, placed by [place], so the test follows the surface wherever its points are,
 * a posed one included: a ring's axis is the normal of its polygon, turned the way the rings run. A patch with fewer than two rings
 * holds nothing inside. An end ring that is not a pole is an open end.
 */
class ShellInside(patch: Patch, place: (Vec3) -> Vec3 = { it }) {
    private val rings: List<Ring>
    private val low: Vec3
    private val high: Vec3

    /** For each pair of neighbouring rings, the middle and squared radius of a ball holding everything between them. */
    private val spanCenters: List<Vec3>
    private val spanReach: FloatArray

    /** How far along the shell each ring lies from the first, by the distances between their middles. */
    private val alongs: FloatArray

    init {
        val placed = List(patch.nu) { ring -> List(patch.nv) { column -> place(patch.point(patch.index(ring, column))) } }
        val centers = placed.map { points -> points.fold(Vec3.ZERO) { sum, p -> sum + p } * (1f / points.size) }
        rings = placed.indices.map { i -> ringOf(placed[i], centers[i], centers[max(i - 1, 0)], centers[min(i + 1, placed.lastIndex)]) }
        var reach = 0f
        for (i in 1 until centers.size) reach = max(reach, (centers[i] - centers[i - 1]).length)
        var lowX = Float.POSITIVE_INFINITY
        var lowY = Float.POSITIVE_INFINITY
        var lowZ = Float.POSITIVE_INFINITY
        var highX = Float.NEGATIVE_INFINITY
        var highY = Float.NEGATIVE_INFINITY
        var highZ = Float.NEGATIVE_INFINITY
        for (points in placed) for (p in points) {
            lowX = min(lowX, p.x); lowY = min(lowY, p.y); lowZ = min(lowZ, p.z)
            highX = max(highX, p.x); highY = max(highY, p.y); highZ = max(highZ, p.z)
        }
        low = Vec3(lowX - reach, lowY - reach, lowZ - reach)
        high = Vec3(highX + reach, highY + reach, highZ + reach)
        alongs = FloatArray(centers.size)
        for (i in 1 until centers.size) alongs[i] = alongs[i - 1] + (centers[i] - centers[i - 1]).length
        val radii = placed.indices.map { i -> placed[i].maxOf { (it - centers[i]).length } }
        spanCenters = (0 until (placed.size - 1).coerceAtLeast(0)).map { i -> (centers[i] + centers[i + 1]) * 0.5f }
        spanReach = FloatArray(spanCenters.size) { i ->
            val radius = (centers[i + 1] - centers[i]).length / 2f + max(radii[i], radii[i + 1])
            radius * radius
        }
    }

    /** Whether [point] lies near enough that it could be inside. */
    fun near(point: Vec3): Boolean =
        point.x >= low.x && point.y >= low.y && point.z >= low.z && point.x <= high.x && point.y <= high.y && point.z <= high.z

    /** Whether anything inside could lie in the box from [boxLow] to [boxHigh]. */
    fun reaches(boxLow: Vec3, boxHigh: Vec3): Boolean =
        boxLow.x <= high.x && boxLow.y <= high.y && boxLow.z <= high.z && boxHigh.x >= low.x && boxHigh.y >= low.y && boxHigh.z >= low.z

    private class Ring(val center: Vec3, val tangent: Vec3, val side: Vec3, val up: Vec3, val xs: FloatArray, val ys: FloatArray) {
        val pole: Boolean get() = xs.isEmpty()
    }

    private fun ringOf(points: List<Vec3>, center: Vec3, before: Vec3, after: Vec3): Ring {
        var normal = Vec3.ZERO
        for (j in points.indices) normal += (points[j] - center) cross (points[(j + 1) % points.size] - center)
        val area = normal.length / 2f
        val along = after - before
        if (along.length == 0f) return Ring(center, Vec3.ZERO, Vec3.ZERO, Vec3.ZERO, FloatArray(0), FloatArray(0))
        if (area < POLE_AREA) return Ring(center, along.normalized(), Vec3.ZERO, Vec3.ZERO, FloatArray(0), FloatArray(0))
        val tangent = normal.normalized().let { if ((it dot along) < 0f) it * -1f else it }
        val first = (points.firstOrNull { (it - center).length > 0f } ?: points.first()) - center
        val side = (first - tangent * (first dot tangent)).normalized()
        val up = tangent cross side
        return Ring(center, tangent, side, up, FloatArray(points.size) { (points[it] - center) dot side }, FloatArray(points.size) { (points[it] - center) dot up })
    }

    /**
     * Whether [point] lies inside. Between a ring and a pole the shell narrows to the pole as a cone: the ring's polygon shrinks
     * toward the pole's center over the way to it.
     */
    operator fun contains(point: Vec3): Boolean = depthIn(point) != null

    /**
     * Whether [point] lies inside and out of sight through the shell's open ends: no nearer to an open end, along the shell, than it
     * is to the shell's wall. A point inside near an open end and away from the wall can be seen through that end.
     */
    fun covers(point: Vec3): Boolean = depthIn(point)?.let { (toEnd, toWall) -> toEnd >= toWall } ?: false

    /** For a point inside, how far it lies along the shell from the nearer open end and how far across from the wall; null outside. */
    private fun depthIn(point: Vec3): Pair<Float, Float>? {
        if (!near(point)) return null
        for (i in 0 until rings.size - 1) {
            val a = rings[i]
            val b = rings[i + 1]
            if (a.pole && b.pole) continue
            val off = point - spanCenters[i]
            if ((off dot off) > spanReach[i]) continue
            val ahead = (point - a.center) dot a.tangent
            if (ahead < 0f) continue
            val behind = (point - b.center) dot b.tangent
            if (behind > 0f) continue
            val toWall = when {
                a.pole -> wallToward(point, b, a)
                b.pole -> wallToward(point, a, b)
                else -> {
                    val ring = if (ahead <= -behind) a else b
                    val offset = point - ring.center
                    wallIn(offset dot ring.side, offset dot ring.up, ring.xs, ring.ys, 1f)
                }
            } ?: continue
            val along = alongs[i] + ahead.coerceAtMost(alongs[i + 1] - alongs[i])
            val toEnd = minOf(if (rings.first().pole) Float.POSITIVE_INFINITY else along, if (rings.last().pole) Float.POSITIVE_INFINITY else alongs.last() - along)
            return toEnd to toWall
        }
        return null
    }

    /** How far [point] lies from the wall of the cone from [ring]'s polygon to [pole]'s center, or null when it lies outside it. */
    private fun wallToward(point: Vec3, ring: Ring, pole: Ring): Float? {
        val toPole = pole.center - ring.center
        val length = toPole dot toPole
        if (length == 0f) return null
        val t = ((point - ring.center) dot toPole) / length
        if (t < 0f || t >= 1f) return null
        val offset = point - (ring.center + toPole * t)
        return wallIn(offset dot ring.side, offset dot ring.up, ring.xs, ring.ys, 1f - t)
    }

    /** How far ([px], [py]) lies from the edge of the polygon [xs], [ys] scaled by [scale] about its center, or null when it lies outside. */
    private fun wallIn(px: Float, py: Float, xs: FloatArray, ys: FloatArray, scale: Float): Float? {
        if (!insidePolygon(px, py, xs, ys, scale)) return null
        var nearest = Float.POSITIVE_INFINITY
        var j = xs.lastIndex
        for (i in xs.indices) {
            val ax = xs[j] * scale
            val ay = ys[j] * scale
            val ex = xs[i] * scale - ax
            val ey = ys[i] * scale - ay
            val length = ex * ex + ey * ey
            val t = if (length == 0f) 0f else (((px - ax) * ex + (py - ay) * ey) / length).coerceIn(0f, 1f)
            val dx = px - (ax + ex * t)
            val dy = py - (ay + ey * t)
            nearest = min(nearest, sqrt(dx * dx + dy * dy))
            j = i
        }
        return nearest
    }

    /** Whether ([px], [py]) lies inside the polygon [xs], [ys] scaled by [scale] about its center. */
    private fun insidePolygon(px: Float, py: Float, xs: FloatArray, ys: FloatArray, scale: Float): Boolean {
        if (scale <= 0f) return false
        val x = px / scale
        val y = py / scale
        var inside = false
        var j = xs.lastIndex
        for (i in xs.indices) {
            if ((ys[i] > y) != (ys[j] > y) && x < (xs[j] - xs[i]) * (y - ys[i]) / (ys[j] - ys[i]) + xs[i]) inside = !inside
            j = i
        }
        return inside
    }
}
