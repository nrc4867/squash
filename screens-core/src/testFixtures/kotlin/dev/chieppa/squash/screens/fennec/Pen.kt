package dev.chieppa.squash.screens.fennec

import dev.chieppa.squash.document.CurvePoint
import dev.chieppa.squash.document.OutlineCurve

private const val HANDLE_SHARE = 6f

/** A place an outline passes through; a [corner] is sharp where the others are smooth. */
data class PenPoint(val x: Float, val y: Float, val corner: Boolean = false)

fun p(x: Float, y: Float) = PenPoint(x, y)

fun tuft(x: Float, y: Float) = PenPoint(x, y, corner = true)

/** A smooth outline through [points], with handles from its neighbours, as a fitted hand stroke has. */
fun drawn(vararg points: PenPoint): OutlineCurve = through(points.toList()) { i -> points[i.coerceIn(0, points.lastIndex)] }

/** A smooth outline round [points] and back to the first. */
fun loop(vararg points: PenPoint): OutlineCurve =
    through(points.toList() + points.first()) { i -> points[((i % points.size) + points.size) % points.size] }

private fun through(points: List<PenPoint>, neighbour: (Int) -> PenPoint): OutlineCurve {
    val out = mutableListOf(CurvePoint(points[0].x, points[0].y))
    for (i in 0 until points.lastIndex) {
        val a = points[i]
        val b = points[i + 1]
        val before = neighbour(i - 1)
        val after = neighbour(i + 2)
        out += if (a.corner) CurvePoint(a.x, a.y) else CurvePoint(a.x + (b.x - before.x) / HANDLE_SHARE, a.y + (b.y - before.y) / HANDLE_SHARE)
        out += if (b.corner) CurvePoint(b.x, b.y) else CurvePoint(b.x - (after.x - a.x) / HANDLE_SHARE, b.y - (after.y - a.y) / HANDLE_SHARE)
        out += CurvePoint(b.x, b.y)
    }
    return OutlineCurve(out)
}
