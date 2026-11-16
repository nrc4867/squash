package dev.chieppa.squash.geom

/**
 * Pushes a strand that hangs from its root, as a lock of hair from the scalp, out of a body it lies against.
 *
 * Each vertex of the strand finds the body's nearest few points and how far out along each point's normal it lies; one that lies less
 * than the clearance out from any of them, or inside, needs to move out along that normal by the difference, the largest such move. A ring moves whole, by the largest need among its
 * columns, so the strand keeps its section rather than flattening on the body. The strand then swings as a rod held at its root ring:
 * a ring pushed carries every ring past it with it, and every ring between it and the root in proportion to how far along the strand
 * from the root each lies, so the strand slides over a shoulder in one piece instead of kinking round it. The root ring never moves.
 */
object StrandPush {
    /**
     * How many nearest body points a strand vertex reads, the one needing the most push deciding it, so a vertex inside one part
     * nearer the surface of another, as over a shoulder where the arm meets the torso, still finds the part it is inside.
     */
    private const val NEAREST = 8

    /**
     * How far each point of [strand], rings from its root at ring 0, moves to clear the body by [clearance], or null when none moves.
     * The body is [points] with outward [normals], one each; a strand vertex further than [reach] from every body point is clear of it.
     */
    fun push(strand: PointGrid, points: Array<Vec3>, normals: Array<Vec3>, clearance: Float, reach: Float): Array<Vec3>? {
        require(points.size == normals.size) { "A body point needs a normal" }
        if (points.isEmpty() || strand.nu < 2) return null
        val nu = strand.nu
        val nv = strand.nv
        val tree = NearestPoints(points)
        val nearest = IntArray(NEAREST)
        val distances = FloatArray(NEAREST)
        val need = Array(nu) { Vec3.ZERO }
        var any = false
        for (i in 1 until nu) for (j in 0 until nv) {
            val p = strand.point(i, j)
            tree.nearest(p, nearest, distances)
            for (k in 0 until NEAREST) {
                val q = nearest[k]
                if (q < 0 || distances[k] > reach) break
                val n = normals[q]
                val gap = (p - points[q]) dot n
                if (gap >= clearance) continue
                val by = n * (clearance - gap)
                if (by.length > need[i].length) {
                    need[i] = by
                    any = true
                }
            }
        }
        if (!any) return null
        val along = FloatArray(nu)
        for (i in 1 until nu) along[i] = along[i - 1] + (middleOf(strand, i) - middleOf(strand, i - 1)).length
        val ring = Array(nu) { Vec3.ZERO }
        for (k in 1 until nu) {
            if (need[k] == Vec3.ZERO || along[k] == 0f) continue
            for (i in 1 until nu) {
                val carried = need[k] * minOf(along[i] / along[k], 1f)
                if (carried.length > ring[i].length) ring[i] = carried
            }
        }
        return Array(nu * nv) { ring[it / nv] }
    }

    private fun middleOf(grid: PointGrid, ring: Int): Vec3 {
        var sum = Vec3.ZERO
        for (j in 0 until grid.nv) sum += grid.point(ring, j)
        return sum * (1f / grid.nv)
    }
}
