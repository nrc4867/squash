package dev.chieppa.squash.recipe.figure

/** A share of the span between a place's neighbours that its handles reach, as the recipe's pen curves draw them. */
private const val HANDLE_SHARE = 6.0

/** Steps each Bézier segment is cut into before a stroke is evened out. */
private const val SEGMENT_STEPS = 32

/** A stroke as given: its Bézier points, its name, and whether an end on the axis crosses it level. */
class FigureStroke(val id: String, val bezier: List<P2>) {
    init {
        require(bezier.size >= 4 && (bezier.size - 1) % 3 == 0) { "a stroke's Bézier holds 3n + 1 points" }
    }

    /** The places the stroke was drawn through: each segment's ends. */
    val places: List<P2> get() = bezier.filterIndexed { i, _ -> i % 3 == 0 }

    companion object {
        /**
         * The stroke through [through], smooth but at [corners], its handles from each place's neighbours; with [mirrored], an end lying
         * on the axis (x 0) takes its neighbour's mirror image beyond it, so it crosses the axis level.
         */
        fun through(id: String, through: List<P2>, corners: Set<Int> = emptySet(), mirrored: Boolean = false): FigureStroke {
            val n = through.size
            require(n >= 2) { "a stroke passes through at least 2 places" }
            fun neighbour(i: Int): P2 = when {
                i < 0 -> if (mirrored && through[0].x == 0.0) P2(-through[1].x, through[1].y) else through[0]
                i >= n -> if (mirrored && through[n - 1].x == 0.0) P2(-through[n - 2].x, through[n - 2].y) else through[n - 1]
                else -> through[i]
            }
            val out = mutableListOf(through[0])
            for (i in 0 until n - 1) {
                val a = through[i]
                val b = through[i + 1]
                val before = neighbour(i - 1)
                val after = neighbour(i + 2)
                out += if (i in corners) a else a + (b - before) * (1 / HANDLE_SHARE)
                out += if (i + 1 in corners) b else b - (after - a) * (1 / HANDLE_SHARE)
                out += b
            }
            return FigureStroke(id, out)
        }
    }
}

/** A place on a stroke as sampled: where, its outward normal, which stroke, and how far along it. */
data class StrokeSample(val stroke: String, val at: P2, val normal: P2, val along: Double)

private fun cubic(p0: P2, p1: P2, p2: P2, p3: P2, t: Double): P2 {
    val s = 1 - t
    return p0 * (s * s * s) + p1 * (3 * s * s * t) + p2 * (3 * s * t * t) + p3 * (t * t * t)
}

/** A stroke as a dense polyline. */
fun FigureStroke.polyline(): List<P2> {
    val out = mutableListOf(bezier[0])
    for (k in 0 until (bezier.size - 1) / 3) {
        val o = k * 3
        for (s in 1..SEGMENT_STEPS) out += cubic(bezier[o], bezier[o + 1], bezier[o + 2], bezier[o + 3], s.toDouble() / SEGMENT_STEPS)
    }
    return out
}

/** A stroke sampled every [spacing] along its curve, each sample with its outward normal: its left, since the figure lies on its right. */
fun FigureStroke.samples(spacing: Double): List<StrokeSample> {
    val line = polyline()
    val cumulative = DoubleArray(line.size)
    for (i in 1 until line.size) cumulative[i] = cumulative[i - 1] + (line[i] - line[i - 1]).length
    val total = cumulative.last()
    val count = maxOf(2, Math.round(total / spacing).toInt() + 1)
    var j = 1
    return List(count) { k ->
        val s = total * k / (count - 1)
        while (j < line.size - 1 && cumulative[j] < s) j++
        val a = line[j - 1]
        val b = line[j]
        val segment = cumulative[j] - cumulative[j - 1]
        val w = if (segment > 0) (s - cumulative[j - 1]) / segment else 0.0
        val d = (b - a).unit()
        StrokeSample(id, a + (b - a) * w, P2(-d.y, d.x), s)
    }
}
