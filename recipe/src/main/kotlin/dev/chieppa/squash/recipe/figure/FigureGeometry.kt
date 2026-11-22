package dev.chieppa.squash.recipe.figure

import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/** A place in a figure's view: x across, y up from the ground. */
data class P2(val x: Double, val y: Double) {
    operator fun plus(o: P2) = P2(x + o.x, y + o.y)
    operator fun minus(o: P2) = P2(x - o.x, y - o.y)
    operator fun times(s: Double) = P2(x * s, y * s)
    infix fun dot(o: P2) = x * o.x + y * o.y
    val length: Double get() = hypot(x, y)
    fun unit(): P2 = length.let { if (it == 0.0) P2(1.0, 0.0) else P2(x / it, y / it) }
}

/** A place in body space: x forward, y up, z to the character's right. */
data class P3(val x: Double, val y: Double, val z: Double) {
    operator fun plus(o: P3) = P3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: P3) = P3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Double) = P3(x * s, y * s, z * s)
    infix fun dot(o: P3) = x * o.x + y * o.y + z * o.z
    infix fun cross(o: P3) = P3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    val length: Double get() = sqrt(this dot this)
    fun unit(): P3 = length.let { if (it == 0.0) this else this * (1.0 / it) }

    /** This turned [degrees] about the unit axis [k] (Rodrigues). */
    fun turned(k: P3, degrees: Double): P3 {
        val t = Math.toRadians(degrees)
        return this * cos(t) + (k cross this) * sin(t) + k * ((k dot this) * (1 - cos(t)))
    }

    companion object {
        val ZERO = P3(0.0, 0.0, 0.0)
        val FORWARD = P3(1.0, 0.0, 0.0)
        val UP = P3(0.0, 1.0, 0.0)
        val RIGHT = P3(0.0, 0.0, 1.0)
    }
}

/** The views a figure is drawn in. */
enum class FigureView { FRONT, SIDE }

/** A body place as [view] shows it, standing on a ground [rootHeight] below the root's origin. */
fun project(view: FigureView, p: P3, rootHeight: Double): P2 =
    if (view == FigureView.FRONT) P2(-p.z, p.y + rootHeight) else P2(p.x, p.y + rootHeight)

/** A piecewise straight run of radii along a part, read between its places and held level past its ends. */
class Profile(val at: List<Double>, val radii: List<Double>) {
    init {
        require(at.size == radii.size && at.isNotEmpty())
    }

    operator fun get(x: Double): Double {
        if (x <= at.first()) return radii.first()
        for (i in 1 until at.size) {
            if (x <= at[i]) {
                val w = (x - at[i - 1]) / (at[i] - at[i - 1]).takeIf { it > 0 }.let { it ?: 1.0 }
                return radii[i - 1] + (radii[i] - radii[i - 1]) * w
            }
        }
        return radii.last()
    }
}
