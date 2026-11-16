package dev.chieppa.squash.screens.surface

import androidx.compose.ui.geometry.Offset
import dev.chieppa.squash.screens.sheet.SurfaceView

/** The places of [drawn] a stroke keeps within [within] pixels of what was drawn, each stretch on one surface thinned alone, so the places either side of a crossing stay. */
fun keptAcross(drawn: List<SurfaceHit>, sight: SurfaceView, within: Float): List<SurfaceHit> {
    val stretches = ArrayList<MutableList<SurfaceHit>>()
    for (hit in drawn) {
        val last = stretches.lastOrNull()?.last()
        if (last != null && last.part == hit.part && last.patch == hit.patch) stretches.last() += hit else stretches += mutableListOf(hit)
    }
    return stretches.flatMap { stretch -> keptAlong(stretch.map { sight.toPixel(it.position) ?: Offset.Zero }, within).map { stretch[it] } }
}

/** The indices of [points] to keep so a line through them strays from the whole by at most [within]; the first and last stay. */
fun keptAlong(points: List<Offset>, within: Float): List<Int> {
    if (points.size <= 2) return points.indices.toList()
    val keep = BooleanArray(points.size)
    keep[0] = true
    keep[points.lastIndex] = true
    val spans = ArrayDeque(listOf(0 to points.lastIndex))
    while (spans.isNotEmpty()) {
        val (from, to) = spans.removeLast()
        if (to - from < 2) continue
        val farthest = (from + 1 until to).maxBy { distanceFromLine(points[it], points[from], points[to]) }
        if (distanceFromLine(points[farthest], points[from], points[to]) <= within) continue
        keep[farthest] = true
        spans += from to farthest
        spans += farthest to to
    }
    return points.indices.filter { keep[it] }
}

private fun distanceFromLine(point: Offset, a: Offset, b: Offset): Float {
    val along = b - a
    val square = along.x * along.x + along.y * along.y
    if (square == 0f) return (point - a).getDistance()
    val t = (((point - a).x * along.x + (point - a).y * along.y) / square).coerceIn(0f, 1f)
    return (a + along * t - point).getDistance()
}

