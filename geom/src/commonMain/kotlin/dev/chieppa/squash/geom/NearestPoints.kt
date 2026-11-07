package dev.chieppa.squash.geom

/**
 * A set of points that answers which few lie nearest a place, as a scan of them all in order would: nearest first, and of points
 * equally near, the one listed first. A tree splits the points at the middle of their widest spread, so a question reads only the
 * points near its place.
 */
class NearestPoints(private val points: Array<Vec3>) {
    private companion object {
        const val BOX = 6

        /** How much further than the last of the nearest, squared, a subtree's box must lie to be passed over. */
        const val PRUNE_MARGIN = 1.0001f
    }

    private val order = IntArray(points.size) { it }
    private val axes = IntArray(points.size)

    /** Each subtree's box, six floats at its middle's place: low x, y, z then high x, y, z. */
    private val boxes = FloatArray(points.size * BOX)

    init {
        build(0, points.size)
    }

    private fun coordinate(point: Vec3, axis: Int): Float = when (axis) {
        0 -> point.x
        1 -> point.y
        else -> point.z
    }

    /** Lays the points in [from] until [until] out as a subtree: the middle one splits the rest on the axis they spread widest along. */
    private fun build(from: Int, until: Int) {
        if (until - from <= 0) return
        var lowX = Float.MAX_VALUE
        var lowY = Float.MAX_VALUE
        var lowZ = Float.MAX_VALUE
        var highX = -Float.MAX_VALUE
        var highY = -Float.MAX_VALUE
        var highZ = -Float.MAX_VALUE
        for (i in from until until) {
            val p = points[order[i]]
            lowX = minOf(lowX, p.x)
            lowY = minOf(lowY, p.y)
            lowZ = minOf(lowZ, p.z)
            highX = maxOf(highX, p.x)
            highY = maxOf(highY, p.y)
            highZ = maxOf(highZ, p.z)
        }
        val x = highX - lowX
        val y = highY - lowY
        val z = highZ - lowZ
        val axis = if (x >= y && x >= z) 0 else if (y >= z) 1 else 2
        val middle = (from + until) / 2
        select(from, until - 1, middle, axis)
        axes[middle] = axis
        val at = middle * BOX
        boxes[at] = lowX
        boxes[at + 1] = lowY
        boxes[at + 2] = lowZ
        boxes[at + 3] = highX
        boxes[at + 4] = highY
        boxes[at + 5] = highZ
        build(from, middle)
        build(middle + 1, until)
    }

    /** Whether point [a] comes before [b] along [axis], the one listed first when they are level. */
    private fun precedes(a: Int, b: Int, axis: Int): Boolean {
        val ca = coordinate(points[a], axis)
        val cb = coordinate(points[b], axis)
        return ca < cb || (ca == cb && a < b)
    }

    /** Puts in [order] at [k] the point that sorting [left] to [right] along [axis] would put there, those before it to its left. */
    private fun select(left: Int, right: Int, k: Int, axis: Int) {
        var low = left
        var high = right
        while (low < high) {
            val pivot = order[(low + high) ushr 1]
            var i = low
            var j = high
            while (i <= j) {
                while (precedes(order[i], pivot, axis)) i++
                while (precedes(pivot, order[j], axis)) j--
                if (i <= j) {
                    val held = order[i]
                    order[i] = order[j]
                    order[j] = held
                    i++
                    j--
                }
            }
            if (k <= j) high = j else if (k >= i) low = i else return
        }
    }

    /**
     * Writes the [nearest].size points nearest [place] into [nearest] by index, nearest first, with their distances in [distances]; a
     * slot no point fills keeps -1 and the largest float.
     */
    fun nearest(place: Vec3, nearest: IntArray, distances: FloatArray) {
        nearest.fill(-1)
        distances.fill(Float.MAX_VALUE)
        search(0, points.size, place, nearest, distances)
    }

    private fun search(from: Int, until: Int, place: Vec3, nearest: IntArray, distances: FloatArray) {
        if (until - from <= 0) return
        val middle = (from + until) / 2
        if (beyond(middle, place, distances[distances.size - 1])) return
        val index = order[middle]
        offer(index, (points[index] - place).length, nearest, distances)
        val axis = axes[middle]
        val gap = coordinate(place, axis) - coordinate(points[index], axis)
        if (gap < 0f) {
            search(from, middle, place, nearest, distances)
            search(middle + 1, until, place, nearest, distances)
        } else {
            search(middle + 1, until, place, nearest, distances)
            search(from, middle, place, nearest, distances)
        }
    }

    /**
     * Whether every point of the subtree at [middle] lies further from [place] than [reach], with a margin, so a point as near as the
     * last of the nearest, which might come before it, is never passed over.
     */
    private fun beyond(middle: Int, place: Vec3, reach: Float): Boolean {
        if (reach == Float.MAX_VALUE) return false
        val at = middle * BOX
        val dx = maxOf(boxes[at] - place.x, 0f, place.x - boxes[at + 3])
        val dy = maxOf(boxes[at + 1] - place.y, 0f, place.y - boxes[at + 4])
        val dz = maxOf(boxes[at + 2] - place.z, 0f, place.z - boxes[at + 5])
        return dx * dx + dy * dy + dz * dz > reach * reach * PRUNE_MARGIN
    }

    /** Takes [index], [distance] from the place, among the nearest when it comes before the last of them, nearer or as near and listed first. */
    private fun offer(index: Int, distance: Float, nearest: IntArray, distances: FloatArray) {
        var at = nearest.size - 1
        if (!before(index, distance, nearest[at], distances[at])) return
        while (at > 0 && before(index, distance, nearest[at - 1], distances[at - 1])) {
            nearest[at] = nearest[at - 1]
            distances[at] = distances[at - 1]
            at--
        }
        nearest[at] = index
        distances[at] = distance
    }

    private fun before(index: Int, distance: Float, other: Int, otherDistance: Float): Boolean =
        other < 0 || distance < otherDistance || (distance == otherDistance && index < other)
}
