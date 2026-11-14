package dev.chieppa.squash.screens.surface.eyes

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

private const val DEGREES_PER_RADIAN = (180.0 / PI).toFloat()

private const val TINY = 1e-9f

/**
 * One side of a painted eye as a pane shows it: its [middle] in pane pixels, how far a pane moves per unit of the eye's chart toward the
 * head's front, [perU], and up, [perV], and its opening [width] and [height] either side of the middle, turned [tilt] degrees, as the eye's
 * shapes are turned in its chart.
 */
class EyeBox(val middle: Offset, val perU: Offset, val perV: Offset, val width: Float, val height: Float, val tilt: Float) {
    /** The pane pixel of the chart place ([u], [v]) turned with the eye. */
    fun at(u: Float, v: Float): Offset {
        val angle = tilt / DEGREES_PER_RADIAN
        val c = cos(angle)
        val s = sin(angle)
        return middle + perU * (u * c - v * s) + perV * (u * s + v * c)
    }

    /** The box's corners, round from the back bottom. */
    val corners: List<Offset> get() = listOf(at(-width, -height), at(width, -height), at(width, height), at(-width, height))

    /** The turn handle, [reach] pixels above the middle of the box's top. */
    fun turnHandle(reach: Float): Offset {
        val top = at(0f, height)
        val out = top - middle
        val length = out.getDistance()
        return if (length > TINY) top + out * (reach / length) else top
    }

    /** The chart place, before the eye's turn, the pane pixel [pixel] falls on in the eye's plane; null where the eye shows edge on. */
    fun chartOf(pixel: Offset): Offset? {
        val d = pixel - middle
        val det = perU.x * perV.y - perU.y * perV.x
        if (abs(det) <= TINY) return null
        return Offset((d.x * perV.y - d.y * perV.x) / det, (perU.x * d.y - perU.y * d.x) / det)
    }

    /** [pixel]'s chart place in the eye's own turned frame, where the box runs [width] and [height] either side. */
    fun ownOf(pixel: Offset): Offset? = chartOf(pixel)?.let { turned(it, -tilt) }

    /** Whether [pixel] falls inside the box. */
    fun holds(pixel: Offset): Boolean = ownOf(pixel)?.let { abs(it.x) <= width && abs(it.y) <= height } == true

    /** The angle of [pixel] about the middle in the eye's chart, in degrees. */
    fun angleOf(pixel: Offset): Float? = chartOf(pixel)?.let { atan2(it.y, it.x) * DEGREES_PER_RADIAN }

    private fun turned(p: Offset, degrees: Float): Offset {
        val angle = degrees / DEGREES_PER_RADIAN
        val c = cos(angle)
        val s = sin(angle)
        return Offset(p.x * c - p.y * s, p.x * s + p.y * c)
    }
}
