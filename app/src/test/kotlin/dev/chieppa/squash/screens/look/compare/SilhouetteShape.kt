package dev.chieppa.squash.screens.look.compare

import kotlin.math.abs
import kotlin.math.sqrt

/** The box round what a silhouette covers, in pixels, its right and bottom edges past its last covered pixel. */
data class PixelBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

/** The box round every covered pixel, null when none is. */
fun Silhouette.box(): PixelBox? {
    var left = width
    var top = height
    var right = -1
    var bottom = -1
    for (y in 0 until height) for (x in 0 until width) {
        if (!this[x, y]) continue
        if (x < left) left = x
        if (x > right) right = x
        if (y < top) top = y
        if (y > bottom) bottom = y
    }
    return if (right < 0) null else PixelBox(left, top, right + 1, bottom + 1)
}

/** This silhouette at [factor] times smaller each way, a pixel covered where at least half of the pixels it stands for are. */
fun Silhouette.shrunk(factor: Int): Silhouette {
    if (factor <= 1) return this
    val w = width / factor
    val h = height / factor
    val half = factor * factor / 2
    val covered = BooleanArray(w * h) { at ->
        val x0 = at % w * factor
        val y0 = at / w * factor
        var count = 0
        for (y in y0 until y0 + factor) for (x in x0 until x0 + factor) if (this[x, y]) count++
        count >= half.coerceAtLeast(1)
    }
    return Silhouette(w, h, covered)
}

/** This silhouette grown by [radius] pixels all round: a pixel covered where a covered one lies within the radius. */
fun Silhouette.grown(radius: Int): Silhouette {
    if (radius <= 0) return this
    val reach = IntArray(radius + 1) { dy -> sqrt((radius * radius - dy * dy).toDouble()).toInt() }
    val covered = BooleanArray(width * height) { at ->
        val x = at % width
        val y = at / width
        (-radius..radius).any { dy ->
            val sy = y + dy
            sy in 0 until height && (-reach[abs(dy)]..reach[abs(dy)]).any { dx -> x + dx in 0 until width && this[x + dx, sy] }
        }
    }
    return Silhouette(width, height, covered)
}
