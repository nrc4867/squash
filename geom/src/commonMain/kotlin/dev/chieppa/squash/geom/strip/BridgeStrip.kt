package dev.chieppa.squash.geom.strip

import dev.chieppa.squash.geom.Vec3

/** How many sweeps of fairing settle a strip's middle rows. */
private const val FAIRING_SWEEPS = 60

/** How far a sweep steps, as a share of the largest step that settles every wavelength of the grid's bilaplacian. */
private const val DAMPING = 0.9f

/** A strip needs this many rings for a row to be faired: two at either end hold its place and its way. */
private const val HELD_ROWS = 2

/**
 * One end of a bridging strip: a closed loop of [points], and at each the unit [ways] the strip runs there, the same way at both ends,
 * from the first end toward the second.
 */
class StripEnd(val points: List<Vec3>, val ways: List<Vec3>) {
    init {
        require(points.size == ways.size) { "A strip end holds ${points.size} points and ${ways.size} ways" }
    }
}

/**
 * A strip of quads bridging two closed loops of the same count: each column a cubic Hermite curve from its point on [StripEnd] `from`
 * to its point on `to`, leaving and arriving along their ways, so the strip meets both surfaces tangent to them; then faired round
 * the loops by a bilaplacian with the first two and last two rings held, which keeps each column's cubic and calms one column against
 * the next. Fairing is the minimal-curvature form of Botsch and Sorkine's linear variational surface methods, on the strip's fixed grid.
 */
object BridgeStrip {
    /**
     * The points of a strip [rings] rings long from [from] to [to], ring by ring, each ring in the loops' order. [reach] scales how far
     * [from]'s way carries the curve, and [arriveReach] how far [to]'s does, as shares of the distance between its ends.
     */
    fun build(from: StripEnd, to: StripEnd, rings: Int, reach: Float, arriveReach: Float = reach): Array<Vec3> {
        val columns = from.points.size
        require(to.points.size == columns) { "A strip joins loops of ${from.points.size} and ${to.points.size} points" }
        require(rings >= 2) { "A strip has at least two rings" }
        val points = Array(rings * columns) { Vec3.ZERO }
        for (j in 0 until columns) {
            val start = from.points[j]
            val end = to.points[j]
            val chord = (end - start).length
            val leave = from.ways[j] * (chord * reach)
            val arrive = to.ways[j] * (chord * arriveReach)
            for (k in 0 until rings) points[k * columns + j] = hermite(start, leave, end, arrive, k.toFloat() / (rings - 1))
        }
        if (rings > 2 * HELD_ROWS) fair(points, rings, columns)
        return points
    }

    private fun hermite(start: Vec3, leave: Vec3, end: Vec3, arrive: Vec3, t: Float): Vec3 {
        val t2 = t * t
        val t3 = t2 * t
        return start * (2f * t3 - 3f * t2 + 1f) + leave * (t3 - 2f * t2 + t) + end * (-2f * t3 + 3f * t2) + arrive * (t3 - t2)
    }

    /**
     * Settles the rows between the held ones toward the bilaplacian's zero, by damped Jacobi sweeps on the grid's Laplacian weighted by
     * how far apart rings and columns lie, so a strip much longer than its loop's spacing, or much shorter, fairs as evenly. Each sweep
     * steps by the inverse of the bilaplacian's largest eigenvalue, which keeps every wavelength shrinking.
     */
    private fun fair(points: Array<Vec3>, rings: Int, columns: Int) {
        var along = 0f
        var around = 0f
        for (k in 0 until rings) for (j in 0 until columns) {
            if (k > 0) along += (points[k * columns + j] - points[(k - 1) * columns + j]).length
            around += (points[k * columns + (j + 1) % columns] - points[k * columns + j]).length
        }
        along /= (rings - 1) * columns
        around /= rings * columns
        if (along <= 0f || around <= 0f) return
        val alongWeight = 1f / (along * along)
        val aroundWeight = 1f / (around * around)
        val total = alongWeight + aroundWeight
        val wu = alongWeight / total
        val wv = aroundWeight / total
        val centre = 2f * (wu + wv)
        val step = DAMPING / (4f * centre * centre)
        val laplacian = Array(rings * columns) { Vec3.ZERO }
        repeat(FAIRING_SWEEPS) {
            for (k in 1 until rings - 1) for (j in 0 until columns) {
                val here = points[k * columns + j]
                val alongSum = points[(k - 1) * columns + j] + points[(k + 1) * columns + j] - here * 2f
                val aroundSum = points[k * columns + (j + columns - 1) % columns] + points[k * columns + (j + 1) % columns] - here * 2f
                laplacian[k * columns + j] = alongSum * wu + aroundSum * wv
            }
            for (k in HELD_ROWS until rings - HELD_ROWS) for (j in 0 until columns) {
                val i = k * columns + j
                val l = laplacian[i]
                val alongSum = laplacian[i - columns] + laplacian[i + columns] - l * 2f
                val aroundSum = laplacian[k * columns + (j + columns - 1) % columns] + laplacian[k * columns + (j + 1) % columns] - l * 2f
                points[i] = points[i] - (alongSum * wu + aroundSum * wv) * step
            }
        }
    }
}
