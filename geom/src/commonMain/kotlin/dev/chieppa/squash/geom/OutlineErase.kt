package dev.chieppa.squash.geom

/** What a stroke of the eraser takes of a curve. */
sealed interface EraseReach {
    /** The stroke passes nowhere near the curve. */
    data object Nothing : EraseReach

    /** The stroke covers most of the curve, so all of it goes. */
    data object Whole : EraseReach

    /** The stroke passes over these sections, which go. */
    data class Sections(val sections: Set<Int>) : EraseReach
}

/**
 * What a stroke through [samples] erases of [path]: each section it comes within [reach] of, or the whole curve when those
 * make up as much of it as a redrawing stroke must cover. Whole sections go because the document stores whole sections.
 */
fun eraseReach(path: BezierPath, samples: List<Vec2>, reach: Float): EraseReach {
    if (samples.isEmpty() || path.segments.isEmpty()) return EraseReach.Nothing
    val stroke = if (samples.size < 2) samples else resample(samples, reach / 2f)
    val flats = path.segments.map { BezierPath(listOf(it)).flatten() }
    val touched = flats.indices.filter { section -> flats[section].comesWithin(reach, stroke) }.toSet()
    if (touched.isEmpty()) return EraseReach.Nothing
    val lengths = flats.map { it.length() }
    val share = touched.sumOf { lengths[it].toDouble() } / lengths.sumOf { it.toDouble() }
    return if (share >= WHOLE_CURVE_COVERAGE) EraseReach.Whole else EraseReach.Sections(touched)
}

private fun Polyline2.comesWithin(reach: Float, stroke: List<Vec2>): Boolean {
    val square = reach * reach
    for (point in stroke) {
        for (i in 0 until xs.size - 1) {
            if (squaredToSegment(point, xs[i], ys[i], xs[i + 1], ys[i + 1]) <= square) return true
        }
    }
    return false
}

private fun squaredToSegment(p: Vec2, ax: Float, ay: Float, bx: Float, by: Float): Float {
    val dx = bx - ax
    val dy = by - ay
    val along = dx * dx + dy * dy
    val t = if (along == 0f) 0f else (((p.x - ax) * dx + (p.y - ay) * dy) / along).coerceIn(0f, 1f)
    val ex = ax + dx * t - p.x
    val ey = ay + dy * t - p.y
    return ex * ex + ey * ey
}

private fun Polyline2.length(): Float {
    var total = 0f
    for (i in 0 until xs.size - 1) {
        val dx = xs[i + 1] - xs[i]
        val dy = ys[i + 1] - ys[i]
        total += kotlin.math.sqrt(dx * dx + dy * dy)
    }
    return total
}
